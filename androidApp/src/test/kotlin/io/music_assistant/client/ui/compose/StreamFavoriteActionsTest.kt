package io.music_assistant.client.ui.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.music_assistant.client.api.APICommands
import io.music_assistant.client.api.Answer
import io.music_assistant.client.api.ConnectionInfo
import io.music_assistant.client.api.Request
import io.music_assistant.client.api.RpcEngine
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.data.MainDataSource
import io.music_assistant.client.data.model.server.ServerInfo
import io.music_assistant.client.support.FakeServiceClient
import io.music_assistant.client.support.get
import io.music_assistant.client.support.radioStreamPlayer
import io.music_assistant.client.support.rules.createKoinTestRule
import io.music_assistant.client.ui.compose.common.ToastHost
import io.music_assistant.client.ui.compose.common.rememberToastState
import io.music_assistant.client.ui.compose.common.viewmodel.ActionsViewModel
import io.music_assistant.client.ui.compose.home.HomeScreenViewModel
import io.music_assistant.client.ui.compose.home.players.PlayersPager
import io.music_assistant.client.utils.ConnectionData
import io.music_assistant.client.utils.SessionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.toast_error_favorite_stream_track
import musicassistantclient.composeapp.generated.resources.toast_favorited_stream_track
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.koin.core.context.GlobalContext.get as getKoin

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class StreamFavoriteActionsTest {
    @get:Rule
    val koinRule = createKoinTestRule { _, _ -> client }

    @get:Rule
    val composeTestRule = createComposeRule()

    private val client = StreamClient()

    @After
    fun resetDispatcher() = Dispatchers.resetMain()

    @Test
    fun `success emits a toast after the stream request`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        client.schema(27)
        val vm = viewModel()
        val toast = async(UnconfinedTestDispatcher(testScheduler)) { vm.toasts.first() }
        vm.onFavoriteStreamClick(radioStreamPlayer("Song"))
        assertEquals(Res.string.toast_favorited_stream_track.get(), toast.await())
        assertEquals(1, client.requests.size)
        assertEquals("player", client.requests.single().args?.get("player_id")?.toString()?.trim('"'))
    }

    @Test
    fun `server refusal emits the resolution failure toast`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        client.schema(27)
        client.result = Result.failure(IllegalArgumentException("Cannot resolve stream title"))
        val vm = viewModel()
        val toast = async(UnconfinedTestDispatcher(testScheduler)) { vm.toasts.first() }
        vm.onFavoriteStreamClick(radioStreamPlayer("Song"))
        assertEquals(Res.string.toast_error_favorite_stream_track.get(), toast.await())
        assertEquals(1, client.requests.size)
    }

    @Test
    fun `capability follows schema and disconnect without a player change`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val vm = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.streamFavoriteSupported.collect {} }
        assertFalse(vm.streamFavoriteSupported.value)
        client.schema(26)
        runCurrent()
        assertFalse(vm.streamFavoriteSupported.value)
        client.schema(27)
        runCurrent()
        assertTrue(vm.streamFavoriteSupported.value)
        client.sessionState.value = SessionState.Disconnected.ByUser
        runCurrent()
        assertFalse(vm.streamFavoriteSupported.value)
    }

    @Test
    fun `send boundary refuses unsupported schema or missing title`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val dataSource = getKoin().get<MainDataSource>()
        client.schema(26)
        assertTrue(dataSource.favoriteCurrentlyPlaying(radioStreamPlayer("Song")).isFailure)
        client.schema(27)
        assertTrue(dataSource.favoriteCurrentlyPlaying(radioStreamPlayer("Station")).isFailure)
        assertTrue(client.requests.isEmpty())
    }

    @Test
    fun `send boundary returns the server result`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        client.schema(27)
        val player = radioStreamPlayer("Song")
        val dataSource = getKoin().get<MainDataSource>()
        assertEquals(client.result, dataSource.favoriteCurrentlyPlaying(player))
        val refusal = IllegalArgumentException("Cannot resolve stream title")
        client.result = Result.failure(refusal)
        assertSame(refusal, dataSource.favoriteCurrentlyPlaying(player).exceptionOrNull())
    }

    @Test
    fun `player renders the successful stream favorite toast`() {
        client.schema(27)
        val vm = viewModel()
        showPlayerToasts(vm)
        composeTestRule.runOnIdle { vm.onFavoriteStreamClick(radioStreamPlayer("Song")) }
        composeTestRule.mainClock.advanceTimeBy(300)
        composeTestRule.onNodeWithText(Res.string.toast_favorited_stream_track.get()).assertIsDisplayed()
    }

    @Test
    fun `player renders the failed stream favorite toast`() {
        client.schema(27)
        client.result = Result.failure(IllegalArgumentException("Cannot resolve stream title"))
        val vm = viewModel()
        showPlayerToasts(vm)
        composeTestRule.runOnIdle { vm.onFavoriteStreamClick(radioStreamPlayer("Song")) }
        composeTestRule.mainClock.advanceTimeBy(300)
        composeTestRule.onNodeWithText(Res.string.toast_error_favorite_stream_track.get()).assertIsDisplayed()
    }

    @Test
    fun `player host only collects its own action view model`() {
        client.schema(27)
        val playerVm = viewModel()
        val navigationVm = viewModel()
        showPlayerToasts(playerVm)
        composeTestRule.runOnIdle { navigationVm.onFavoriteStreamClick(radioStreamPlayer("Song")) }
        composeTestRule.mainClock.advanceTimeBy(300)
        composeTestRule.onNodeWithText(Res.string.toast_favorited_stream_track.get()).assertDoesNotExist()
        composeTestRule.runOnIdle { playerVm.onFavoriteStreamClick(radioStreamPlayer("Song")) }
        composeTestRule.mainClock.advanceTimeBy(300)
        composeTestRule.onNodeWithText(Res.string.toast_favorited_stream_track.get()).assertIsDisplayed()
    }

    @Test
    fun `protocol error envelope becomes a failed favorite result`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        client.schema(27)
        client.result = protocolRefusal()
        assertTrue(client.result.isSuccess, "The transport itself received a response")
        val dataSource = getKoin().get<MainDataSource>()
        assertTrue(dataSource.favoriteCurrentlyPlaying(radioStreamPlayer("Song")).isFailure)
    }

    @Test
    fun `protocol refusal renders failure instead of success`() {
        client.schema(27)
        client.result = protocolRefusal()
        val vm = viewModel()
        showPlayerToasts(vm)
        composeTestRule.runOnIdle { vm.onFavoriteStreamClick(radioStreamPlayer("Song")) }
        composeTestRule.mainClock.advanceTimeBy(300)
        composeTestRule.onNodeWithText(Res.string.toast_error_favorite_stream_track.get()).assertIsDisplayed()
        composeTestRule.onNodeWithText(Res.string.toast_favorited_stream_track.get()).assertDoesNotExist()
    }

    /** Matches the real RPC engine and Ktor transport's successful response wrapper. */
    private fun protocolRefusal(): Result<Answer> {
        var response: Result<Answer> = Result.failure(IllegalStateException("No response"))
        val engine = RpcEngine(onAuthError = {}, onError = {})
        engine.registerCallback("stream-request") { response = Result.success(it) }
        engine.handleResponse(
            Json.parseToJsonElement(
                """{"message_id":"stream-request","error_code":1,"details":"Stream title could not be resolved"}""",
            ) as JsonObject,
        )
        return response
    }

    private fun showPlayerToasts(vm: ActionsViewModel) {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            val toastState = rememberToastState()
            Box {
                PlayersPager(
                    playerPagerState = rememberPagerState { 0 },
                    state = HomeScreenViewModel.PlayersState.NoServer,
                    homeScreenViewModel = getKoin().get(),
                    actionsViewModel = vm,
                    dspSettingsViewModel = getKoin().get(),
                    expanded = true,
                    onClose = {},
                    contentPadding = PaddingValues(),
                    toastState = toastState,
                    navigateToItem = {},
                )
                ToastHost(toastState)
            }
        }
    }

    private fun viewModel() = ActionsViewModel(client, getKoin().get(), getKoin().get())

    private class StreamClient : ServiceClient by FakeServiceClient() {
        override val sessionState = MutableStateFlow<SessionState>(SessionState.Disconnected.Initial)
        val requests = mutableListOf<Request>()
        var result: Result<Answer> = Result.success(Answer(JsonObject(mapOf("result" to JsonNull))))

        fun schema(version: Int) {
            sessionState.value = SessionState.Connected.Direct(
                ConnectionInfo("example.invalid", 80, false),
                ConnectionData(serverInfo = ServerInfo("fixture", schemaVersion = version)),
            )
        }

        override suspend fun sendRequest(request: Request): Result<Answer> {
            check(request.command == APICommands.PLAYERS_ADD_CURRENTLY_PLAYING_TO_FAVORITES)
            requests += request
            return result
        }
    }
}
