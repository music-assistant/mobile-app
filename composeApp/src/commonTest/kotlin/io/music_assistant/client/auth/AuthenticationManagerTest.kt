package io.music_assistant.client.auth

import com.russhwolf.settings.MapSettings
import io.music_assistant.client.api.Answer
import io.music_assistant.client.api.ConnectionInfo
import io.music_assistant.client.api.Request
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.data.model.server.events.Event
import io.music_assistant.client.settings.SettingsRepository
import io.music_assistant.client.utils.SessionState
import io.music_assistant.client.webrtc.DataChannelWrapper
import io.music_assistant.client.webrtc.WebRTCHttpProxy
import io.music_assistant.client.webrtc.model.RemoteId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Guards the cancellation contract that [io.music_assistant.client.ui.compose.auth.AuthenticationViewModel]
 * depends on. The viewmodel loads providers inside a `flatMapLatest`, so a
 * session-state change (disconnect, Direct↔WebRTC switch) cancels the
 * in-flight [AuthenticationManager.getProviders] coroutine as a matter of
 * routine. Because `CancellationException` is an [Exception], the manager's
 * broad `catch (e: Exception)` blocks would otherwise swallow it — surfacing a
 * spurious [AuthState.Error] (getProviders, login) or an absorbed failure
 * Result (getOAuthUrl) instead of letting the coroutine cancel. Each cancellable
 * suspend entry point is verified here.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AuthenticationManagerTest {
    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `getProviders rethrows cancellation instead of driving authState to Error`() = runTest {
        val client = StubServiceClient()
        val manager = AuthenticationManager(client, SettingsRepository(MapSettings()))
        try {
            val job = launch { manager.getProviders() }
            runCurrent() // getProviders sets Loading, then suspends inside sendRequest

            assertEquals(
                AuthState.Loading,
                manager.authState.value,
                "Precondition: an in-flight getProviders() shows Loading",
            )

            job.cancel()
            runCurrent() // deliver the cancellation into the suspended sendRequest

            assertTrue(
                manager.authState.value !is AuthState.Error,
                "Cancelling an in-flight getProviders() must not be swallowed into a " +
                    "spurious AuthState.Error",
            )
            assertTrue(job.isCancelled, "The cancellation must propagate, not be absorbed")
        } finally {
            manager.close()
        }
    }

    @Test
    fun `loginWithCredentials rethrows cancellation instead of driving authState to Error`() = runTest {
        val client = StubServiceClient()
        val manager = AuthenticationManager(client, SettingsRepository(MapSettings()))
        try {
            val job = launch { manager.loginWithCredentials("builtin", "user", "pass") }
            runCurrent() // login sets Loading, then suspends inside serviceClient.login

            job.cancel()
            runCurrent()

            assertTrue(
                manager.authState.value !is AuthState.Error,
                "Cancelling an in-flight login must not be swallowed into a spurious AuthState.Error",
            )
            assertTrue(job.isCancelled, "The cancellation must propagate, not be absorbed")
        } finally {
            manager.close()
        }
    }

    @Test
    fun `getOAuthUrl rethrows cancellation instead of completing with a failure result`() = runTest {
        val client = StubServiceClient()
        val manager = AuthenticationManager(client, SettingsRepository(MapSettings()))
        try {
            // getOAuthUrl never touches authState, and job.isCancelled is true whether
            // or not the body swallows the cancellation — so the discriminator is the
            // return value: a propagated cancellation yields no Result at all, while a
            // swallowed one is absorbed into Result.failure and assigned here.
            var result: Result<String>? = null
            val job = launch { result = manager.getOAuthUrl("spotify", "musicassistant://auth/callback") }
            runCurrent() // suspends inside serviceClient.sendRequest

            job.cancel()
            runCurrent()

            assertNull(
                result,
                "Cancellation must propagate; getOAuthUrl must not absorb it into a failure Result",
            )
        } finally {
            manager.close()
        }
    }
}

/**
 * Minimal [ServiceClient] for manager tests. [sendRequest] parks via
 * [awaitCancellation] so a caller can be cancelled mid-request; the session
 * stays Disconnected so the manager's init monitor does nothing.
 */
private class StubServiceClient : ServiceClient {
    override val sessionState = MutableStateFlow<SessionState>(SessionState.Disconnected.Initial)
    override val isReadyForCommands = MutableStateFlow(false)
    override val externalConsumerActive = MutableStateFlow(false)
    override val serverBaseUrl = MutableStateFlow<String?>(null)
    override val webRTCHttpProxy: WebRTCHttpProxy? = null
    override val events: Flow<Event<out Any>> = emptyFlow()
    override val webrtcSendspinChannel: DataChannelWrapper? = null
    override val foregroundEvents: Flow<Unit> = emptyFlow()

    override suspend fun sendRequest(request: Request): Result<Answer> = awaitCancellation()
    override suspend fun login(username: String, password: String): Unit = awaitCancellation()
    override suspend fun authorize(token: String, isAutoLogin: Boolean) = Unit
    override fun logout() = Unit
    override fun resolveImageUrl(
        path: String,
        provider: String,
        isRemotelyAccessible: Boolean,
        proxyId: String?,
    ): String? = null
    override fun rebaseServerImageUrl(rawUrl: String): String? = null
    override fun forceWebRTCReconnect() = Unit
    override fun onAppForeground() = Unit
    override fun onAppBackground() = Unit
    override fun disconnectByUser() = Unit
    override fun connect(connection: ConnectionInfo) = Unit
    override fun connectWebRTC(remoteId: RemoteId) = Unit
    override fun onExternalConsumerActive() = Unit
    override fun onPlaybackActive() = Unit
    override fun onExternalConsumerInactive() = Unit
    override fun onPlaybackInactive() = Unit
    override fun forceDisconnect(reason: Exception) = Unit
}
