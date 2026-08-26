package io.music_assistant.client.auth

import com.russhwolf.settings.MapSettings
import io.music_assistant.client.api.Answer
import io.music_assistant.client.api.ConnectionInfo
import io.music_assistant.client.api.Request
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.data.model.server.ServerInfo
import io.music_assistant.client.data.model.server.User
import io.music_assistant.client.data.model.server.events.Event
import io.music_assistant.client.settings.SettingsRepository
import io.music_assistant.client.utils.AuthProcessState
import io.music_assistant.client.utils.ConnectionData
import io.music_assistant.client.utils.SessionState
import io.music_assistant.client.webrtc.DataChannelWrapper
import io.music_assistant.client.webrtc.WebRTCHttpProxy
import io.music_assistant.client.webrtc.model.RemoteId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
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
        val manager = AuthenticationManager(client, SettingsRepository(MapSettings(), MapSettings()))
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
        val manager = AuthenticationManager(client, SettingsRepository(MapSettings(), MapSettings()))
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
        val manager = AuthenticationManager(client, SettingsRepository(MapSettings(), MapSettings()))
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

    // --- OAuth deep-link token buffering (issue #901) ---

    @Test
    fun `a deep-link OAuth token is spent once the transport becomes ready`() = runTest {
        val client = StubServiceClient()
        val manager = AuthenticationManager(client, SettingsRepository(MapSettings(), MapSettings()))
        try {
            // The deep link arrives while the foreground reconnect is still in flight,
            // so there is no live socket to send on yet.
            manager.handleOAuthCallback("oauth-token")
            runCurrent()

            assertTrue(
                client.authorizeCalls.isEmpty(),
                "The token must not be sent while no transport can carry it",
            )

            client.sessionState.value = awaitingAuth()
            runCurrent()

            assertEquals(
                listOf("oauth-token" to false),
                client.authorizeCalls,
                "The buffered token must be spent as a user-initiated login once ready",
            )
        } finally {
            manager.close()
        }
    }

    @Test
    fun `a deep-link OAuth token survives a reconnect that aborts the first attempt`() = runTest {
        val client = StubServiceClient()
        val manager = AuthenticationManager(client, SettingsRepository(MapSettings(), MapSettings()))
        try {
            client.sessionState.value = awaitingAuth()
            manager.handleOAuthCallback("oauth-token")
            runCurrent()
            assertEquals(1, client.authorizeCalls.size, "Precondition: the first attempt was made")

            // The transport bounces: authorize resolved to Aborted, nothing settled.
            client.sessionState.value = SessionState.Reconnecting.Direct(
                attempt = 1,
                connectionInfo = CONNECTION,
            )
            runCurrent()
            client.sessionState.value = awaitingAuth()
            runCurrent()

            assertEquals(
                listOf("oauth-token" to false, "oauth-token" to false),
                client.authorizeCalls,
                "An aborted attempt must not consume the token — the reconnect has to retry it",
            )
        } finally {
            manager.close()
        }
    }

    @Test
    fun `a spent OAuth token is not replayed after the session authenticates`() = runTest {
        val client = StubServiceClient()
        val manager = AuthenticationManager(client, SettingsRepository(MapSettings(), MapSettings()))
        try {
            client.sessionState.value = awaitingAuth()
            manager.handleOAuthCallback("oauth-token")
            runCurrent()

            client.sessionState.value = SessionState.Connected.Direct(
                connectionInfo = CONNECTION,
                connectionData = ConnectionData(
                    serverInfo = SERVER_INFO,
                    user = USER,
                    token = "oauth-token",
                ),
            )
            runCurrent()
            client.sessionState.value = awaitingAuth()
            runCurrent()

            assertEquals(
                listOf("oauth-token" to false, "oauth-token" to true),
                client.authorizeCalls,
                "Once authenticated the token is spent and persisted, so a later AwaitingAuth " +
                    "must re-auth through the saved-token path (isAutoLogin = true), not replay " +
                    "the pending one as a fresh user login",
            )
        } finally {
            manager.close()
        }
    }

    @Test
    fun `a deep-link OAuth token is spent when a previous attempt already failed`() = runTest {
        val client = StubServiceClient()
        val manager = AuthenticationManager(client, SettingsRepository(MapSettings(), MapSettings()))
        try {
            // AwaitingAuth(Failed) never emits again on its own, so nothing but the
            // callback itself can drive the retry.
            client.sessionState.value = awaitingAuth(AuthProcessState.Failed("earlier failure"))
            runCurrent()

            manager.handleOAuthCallback("oauth-token")
            runCurrent()

            assertEquals(
                listOf("oauth-token" to false),
                client.authorizeCalls,
                "A token arriving into a failed auth state must still be spent, not left to " +
                    "expire on the watchdog",
            )
        } finally {
            manager.close()
        }
    }

    // --- OAuth cancellation (issue #901, item 3) ---

    @Test
    fun `a handler that cannot report cancellation still has abandonment inferred`() = runTest {
        val client = StubServiceClient()
        val manager = AuthenticationManager(client, SettingsRepository(MapSettings(), MapSettings()))
        try {
            manager.oauthHandler = FakeOAuthHandler(reportsCancellation = false)
            manager.startOAuthFlow("https://example.test/authorize")
            runCurrent()
            assertEquals(AuthState.Loading, manager.authState.value, "Precondition: flow pending")

            client.foregroundEventsFlow.emit(Unit)
            runCurrent()

            assertEquals(
                AuthState.Idle,
                manager.authState.value,
                "Custom Tabs backgrounds the app and never reports a dismissal, so returning " +
                    "to the foreground without a callback is the only cancellation signal there is",
            )
        } finally {
            manager.close()
        }
    }

    @Test
    fun `a handler that reports cancellation suppresses the foreground heuristic`() = runTest {
        val client = StubServiceClient()
        val manager = AuthenticationManager(client, SettingsRepository(MapSettings(), MapSettings()))
        try {
            manager.oauthHandler = FakeOAuthHandler(reportsCancellation = true)
            manager.startOAuthFlow("https://example.test/authorize")
            runCurrent()

            client.foregroundEventsFlow.emit(Unit)
            runCurrent()

            assertEquals(
                AuthState.Loading,
                manager.authState.value,
                "An in-app auth session reports its own dismissal, so a foreground event must " +
                    "not tear down a flow that is still on screen",
            )
        } finally {
            manager.close()
        }
    }

    @Test
    fun `cancelOAuthFlow is a no-op unless a flow is pending`() = runTest {
        val client = StubServiceClient()
        val manager = AuthenticationManager(client, SettingsRepository(MapSettings(), MapSettings()))
        try {
            manager.cancelOAuthFlow("dismissed")
            assertEquals(
                AuthState.Idle,
                manager.authState.value,
                "A late or duplicate cancel must not manufacture an error out of nothing",
            )

            manager.oauthHandler = FakeOAuthHandler(reportsCancellation = true)
            manager.startOAuthFlow("https://example.test/authorize")
            runCurrent()
            manager.cancelOAuthFlow("dismissed")
            val afterFirst = manager.authState.value
            manager.cancelOAuthFlow("dismissed again")

            assertTrue(afterFirst is AuthState.Error)
            assertEquals("dismissed", afterFirst.message)
            assertEquals(
                afterFirst,
                manager.authState.value,
                "The handler may cancel without knowing whether something else already settled " +
                    "the flow, so a second call must change nothing",
            )
        } finally {
            manager.close()
        }
    }

    @Test
    fun `a dismissal with no reason drops back to the login screen without an error`() = runTest {
        val client = StubServiceClient()
        val manager = AuthenticationManager(client, SettingsRepository(MapSettings(), MapSettings()))
        try {
            manager.oauthHandler = FakeOAuthHandler(reportsCancellation = true)
            manager.startOAuthFlow("https://example.test/authorize")
            runCurrent()

            manager.cancelOAuthFlow(null)

            assertEquals(
                AuthState.Idle,
                manager.authState.value,
                "Backing out of the sheet is a choice, not a failure — no error banner",
            )
        } finally {
            manager.close()
        }
    }

    private fun awaitingAuth(
        authProcessState: AuthProcessState = AuthProcessState.NotStarted,
    ) = SessionState.Connected.Direct(
        connectionInfo = CONNECTION,
        connectionData = ConnectionData(
            serverInfo = SERVER_INFO,
            authProcessState = authProcessState,
        ),
    )

    private companion object {
        val CONNECTION = ConnectionInfo(host = "ma.local", port = 8095, isTls = false)
        val SERVER_INFO = ServerInfo(serverId = "server-1")
        val USER = User(username = "daveb")
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
    override val webRTCHttpProxy: WebRTCHttpProxy? = null
    override val events: Flow<Event<out Any>> = emptyFlow()
    override val webrtcSendspinChannel: DataChannelWrapper? = null
    val foregroundEventsFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val foregroundEvents: Flow<Unit> = foregroundEventsFlow

    override suspend fun sendRequest(request: Request): Result<Answer> = awaitCancellation()
    override suspend fun login(username: String, password: String): Unit = awaitCancellation()
    val authorizeCalls = mutableListOf<Pair<String, Boolean>>()

    override suspend fun authorize(token: String, isAutoLogin: Boolean) {
        authorizeCalls += token to isAutoLogin
    }
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
    override fun noServer() = Unit
}

/** Records presentation without doing any. */
private class FakeOAuthHandler(override val reportsCancellation: Boolean) : OAuthHandler {
    val openedUrls = mutableListOf<String>()
    override fun openOAuthUrl(url: String) {
        openedUrls += url
    }
}
