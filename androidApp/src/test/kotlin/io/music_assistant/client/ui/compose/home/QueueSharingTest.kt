package io.music_assistant.client.ui.compose.home

import android.content.ContextWrapper
import android.content.Intent
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.core.content.IntentCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.music_assistant.client.api.APICommands
import io.music_assistant.client.api.Answer
import io.music_assistant.client.api.ConnectionInfo
import io.music_assistant.client.api.Request
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.data.factory.MediaItemFactory
import io.music_assistant.client.data.factory.QueueFactory
import io.music_assistant.client.data.model.client.PlayerDataFixtures.toQueue
import io.music_assistant.client.data.model.client.Queue
import io.music_assistant.client.data.model.client.QueueTrack
import io.music_assistant.client.data.model.server.GrantedScopes
import io.music_assistant.client.data.model.server.Scope
import io.music_assistant.client.data.model.server.ServerInfo
import io.music_assistant.client.data.model.server.ServerMediaItem
import io.music_assistant.client.data.model.server.ServerProviderInstance
import io.music_assistant.client.data.model.server.ServerQueueItem
import io.music_assistant.client.data.repository.ProviderRepository
import io.music_assistant.client.data.repository.ShareRepository
import io.music_assistant.client.support.FakeServiceClient
import io.music_assistant.client.ui.compose.common.DataState
import io.music_assistant.client.ui.compose.common.LocalGrantedScopes
import io.music_assistant.client.ui.compose.common.action.QueueAction
import io.music_assistant.client.ui.compose.provider.ProviderViewModel
import io.music_assistant.client.ui.theme.AppTheme
import io.music_assistant.client.utils.ConnectionData
import io.music_assistant.client.utils.SessionState
import io.music_assistant.client.utils.myJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import org.koin.test.KoinTestRule
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class QueueSharingTest {
    @get:Rule
    val koinRule = KoinTestRule.create {}

    @get:Rule
    val composeTestRule = createComposeRule()

    private val client = QueueShareClient()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val intents = mutableListOf<Intent>()
    private val actions = mutableListOf<QueueAction>()

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun `played track shares the direct YouTube Music link without replaying or editing the queue`() {
        assertPlayedTrackShare("YouTube Music", "https://music.youtube.com/watch?v=meuR1l4j-xs")
    }

    @Test
    fun `played track can still share the generated Music Assistant link`() {
        assertPlayedTrackShare("Music Assistant", GENERATED_LINK)
    }

    private fun assertPlayedTrackShare(choice: String, expectedUrl: String) {
        val played = queueItem("Previously played", "library://track/1")
        val current = queueItem("Playing", "library://track/2")
        val queue = listOf(played, current).toQueue()
        showQueue(queue.copy(info = queue.info.copy(currentItem = current)))

        composeTestRule.onNodeWithText("Previously played").performTouchInput { longClick() }
        composeTestRule.onNodeWithText("Delete").assertDoesNotExist()
        composeTestRule.onNodeWithText("Share").assertIsDisplayed()
        composeTestRule.runOnIdle {
            assertTrue(client.requests.isEmpty())
            assertTrue(actions.isEmpty())
        }
        composeTestRule.onNodeWithText("Share").performClick()
        composeTestRule.onNodeWithText("YouTube Music").assertIsDisplayed()
        composeTestRule.onNodeWithText("Music Assistant").assertIsDisplayed()
        composeTestRule.onNodeWithText("unsafe").assertDoesNotExist()
        composeTestRule.runOnIdle { assertTrue(intents.isEmpty()) }
        composeTestRule.onNodeWithText(choice).performClick()

        composeTestRule.runOnIdle {
            val request = client.requests.single()
            assertEquals("share/generate", request.command)
            assertEquals("library://track/1", request.args?.get("uri")?.jsonPrimitive?.content)
            val chooser = intents.single()
            assertEquals(Intent.ACTION_CHOOSER, chooser.action)
            val send = assertNotNull(IntentCompat.getParcelableExtra(chooser, Intent.EXTRA_INTENT, Intent::class.java))
            assertEquals(Intent.ACTION_SEND, send.action)
            assertEquals("text/plain", send.type)
            assertEquals(expectedUrl, send.getStringExtra(Intent.EXTRA_TEXT))
            assertTrue(actions.isEmpty())
        }
    }

    @Test
    fun `radio queue rows do not offer a share link`() {
        val current = queueItem("Playing", "library://track/1")
        val station = queueItem("Station", "library://radio/2", "radio")
        showQueue(listOf(current, station).toQueue())

        composeTestRule.onNodeWithText("Station").performTouchInput { longClick() }
        composeTestRule.onNodeWithText("Delete").assertIsDisplayed()
        composeTestRule.onNodeWithText("Share").assertDoesNotExist()
        composeTestRule.runOnIdle { assertTrue(client.requests.isEmpty()) }
    }

    private fun showQueue(queue: Queue) {
        val dependencies = module {
            single { ShareRepository(client) }
            single { ProviderRepository(client, scope) }
            viewModel { ProviderViewModel(client, get()) }
        }
        koinRule.koin.loadModules(listOf(dependencies))
        composeTestRule.setContent {
            val baseContext = LocalContext.current
            val context = remember(baseContext) {
                object : ContextWrapper(baseContext) {
                    override fun startActivity(intent: Intent) {
                        intents.add(intent)
                    }
                }
            }
            CompositionLocalProvider(
                LocalContext provides context,
                LocalGrantedScopes provides GrantedScopes(setOf(Scope.LIBRARY_READ)),
            ) {
                AppTheme {
                    CollapsibleQueue(
                        queue = DataState.Data(queue),
                        isQueueExpanded = true,
                        onQueueExpandedSwitch = {},
                        onGoToLibrary = {},
                        queueAction = { actions.add(it) },
                        tint = Color.Gray,
                        contentPadding = PaddingValues(),
                    )
                }
            }
        }
    }

    private fun queueItem(name: String, uri: String, mediaType: String = "track"): QueueTrack {
        val item = ServerMediaItem(
            itemId = name,
            provider = "library",
            name = name,
            mediaType = mediaType,
            uri = uri,
            isPlayable = true,
        )
        return assertNotNull(QueueFactory(MediaItemFactory(client)).createTrack(ServerQueueItem(name, item)))
    }

    private class QueueShareClient : ServiceClient by FakeServiceClient() {
        override val sessionState = MutableStateFlow<SessionState>(
            SessionState.Connected.Direct(
                ConnectionInfo("localhost", 8095, false),
                ConnectionData(serverInfo = ServerInfo("test", schemaVersion = 122)),
            ),
        )
        override val isReadyForCommands = MutableStateFlow(true)
        val requests = mutableListOf<Request>()

        override suspend fun sendRequest(request: Request): Result<Answer> {
            val result = when (request.command) {
                APICommands.PROVIDERS -> myJson.encodeToJsonElement(
                    listOf(ServerProviderInstance("share_provider", "Share Links", "share_provider", "plugin")),
                )
                APICommands.PROVIDERS_ICON -> myJson.encodeToJsonElement(null as String?)
                "share/generate" -> {
                    requests.add(request)
                    myJson.encodeToJsonElement(GENERATED_LINK)
                }
                else -> error("Unexpected request: ${request.command}")
            }
            return Result.success(Answer(JsonObject(mapOf("result" to result))))
        }
    }

    private companion object {
        // Version 1 payload with a YouTube Music URL and a credential-bearing URL.
        const val GENERATED_LINK =
            "https://share.music-assistant.io/#1TYxBCsIwEEWvIrPWBsFVQPQI4gUkpiONTpqQmYkNpXe3unL5-O-_GaRlBAtSnH_BF" +
            "iQIfflSsIakTG2TyTXs1y2XVEOP5aaFGOwMTaJy8Ks-iGS2xvy4a0lF79j5FM3biR9O9RhRr3s6PHcTrykd2T3w76iMxTL6gnLGycVM2IWxOgq9gWX5AA"
    }
}
