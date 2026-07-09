package io.music_assistant.client.ui.compose.auth

import app.cash.turbine.test
import io.music_assistant.client.api.ConnectionInfo
import io.music_assistant.client.auth.AuthCoordinator
import io.music_assistant.client.auth.AuthState
import io.music_assistant.client.data.model.server.AuthProvider
import io.music_assistant.client.data.model.server.ServerInfo
import io.music_assistant.client.data.model.server.User
import io.music_assistant.client.utils.AuthProcessState
import io.music_assistant.client.utils.ConnectionData
import io.music_assistant.client.utils.SessionState
import io.music_assistant.client.webrtc.model.RemoteId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Specifies the reactive contract of [AuthenticationViewModel].
 *
 * The viewmodel projects a list of [AuthProvider] from session-state
 * transitions and a manual retry trigger. These tests pin down the
 * projection rules and — crucially — the cancellation semantics that
 * the old imperative implementation got wrong: a mid-fetch disconnect
 * used to be able to leave a stale provider list around. With
 * `flatMapLatest` the race is structurally impossible; the cancellation
 * tests below prove that.
 *
 * Dispatcher strategy: [UnconfinedTestDispatcher] for `Dispatchers.Main`
 * (which `viewModelScope` delegates to) — every launched coroutine
 * resumes eagerly, so flow propagation is synchronous from the test's
 * point of view. The only real waits are explicit [CompletableDeferred]
 * gates that pin a fetch in mid-suspension while we drive session-state
 * changes underneath.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AuthenticationViewModelTest {
    private lateinit var sessionState: MutableStateFlow<SessionState>
    private lateinit var auth: FakeAuthCoordinator

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        sessionState = MutableStateFlow(SessionState.Disconnected.Initial)
        auth = FakeAuthCoordinator()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(): AuthenticationViewModel =
        AuthenticationViewModel(auth, sessionState)

    // region Auto-load projection

    @Test
    fun `initial Disconnected state yields empty providers`() = runTest {
        viewModel().providers.test {
            assertEquals(emptyList(), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(0, auth.getProvidersCalls, "Disconnected must not hit the API")
    }

    @Test
    fun `transition to Direct AwaitingAuth fetches providers from the API`() = runTest {
        auth.providersResult = Result.success(REMOTE_PROVIDERS)

        viewModel().providers.test {
            assertEquals(emptyList(), awaitItem())

            sessionState.value = connectedDirect(AuthProcessState.NotStarted)

            assertEquals(REMOTE_PROVIDERS, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(1, auth.getProvidersCalls)
    }

    @Test
    fun `transition to WebRTC AwaitingAuth fetches providers from API`() = runTest {
        auth.providersResult = Result.success(REMOTE_PROVIDERS)

        viewModel().providers.test {
            assertEquals(emptyList(), awaitItem())

            sessionState.value = connectedWebRTC(AuthProcessState.NotStarted)

            assertEquals(REMOTE_PROVIDERS, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(1, auth.getProvidersCalls)
    }

    @Test
    fun `transitional Connecting and Reconnecting states do not clear providers`() = runTest {
        auth.providersResult = Result.success(REMOTE_PROVIDERS)

        viewModel().providers.test {
            assertEquals(emptyList(), awaitItem())

            sessionState.value = connectedDirect(AuthProcessState.NotStarted)
            assertEquals(REMOTE_PROVIDERS, awaitItem())

            sessionState.value = SessionState.Connecting
            expectNoEvents()

            sessionState.value = SessionState.Reconnecting.Direct(
                attempt = 1,
                connectionInfo = DIRECT_CONNECTION_INFO,
                connectionData = ConnectionData(serverInfo = SERVER_INFO),
            )
            expectNoEvents()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `AwaitingAuth Failed preserves the displayed provider list without refetch`() = runTest {
        auth.providersResult = Result.success(REMOTE_PROVIDERS)

        viewModel().providers.test {
            assertEquals(emptyList(), awaitItem())

            sessionState.value = connectedDirect(AuthProcessState.NotStarted)
            assertEquals(REMOTE_PROVIDERS, awaitItem())

            sessionState.value = connectedDirect(AuthProcessState.Failed("wrong creds"))
            expectNoEvents()

            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(1, auth.getProvidersCalls, "Failed-auth must not retry on its own")
    }

    @Test
    fun `transition to Authenticated leaves the provider list intact`() = runTest {
        auth.providersResult = Result.success(REMOTE_PROVIDERS)

        viewModel().providers.test {
            assertEquals(emptyList(), awaitItem())

            sessionState.value = connectedDirect(AuthProcessState.NotStarted)
            assertEquals(REMOTE_PROVIDERS, awaitItem())

            // serverInfo + user populated → dataConnectionState resolves to Authenticated.
            sessionState.value = connectedDirectWith(
                ConnectionData(serverInfo = SERVER_INFO, user = USER_FIXTURE, token = "blah"),
            )
            expectNoEvents()

            cancelAndIgnoreRemainingEvents()
        }
    }

    // endregion

    // region Cancellation semantics — the structural race fix

    @Test
    fun `disconnect during in-flight fetch cancels and never emits the stale result`() = runTest {
        val gate = CompletableDeferred<Unit>()
        auth.providersGate = gate
        auth.providersResult = Result.success(REMOTE_PROVIDERS)

        viewModel().providers.test {
            assertEquals(emptyList(), awaitItem())

            // Trigger a fetch that suspends on the gate.
            sessionState.value = connectedDirect(AuthProcessState.NotStarted)
            expectNoEvents()
            assertEquals(1, auth.getProvidersCalls)

            // Disconnect while the fetch is still suspended. Providers was
            // emptyList → no observable emission, but the in-flight load is
            // structurally cancelled by flatMapLatest.
            sessionState.value = SessionState.Disconnected.Error(reason = null)
            expectNoEvents()

            // Release the now-cancelled gate. The old (pre-refactor) code
            // would have emitted REMOTE_PROVIDERS here; the new code MUST NOT.
            gate.complete(Unit)
            expectNoEvents()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `connection-type switch from Direct to WebRTC cancels the in-flight fetch and refetches for the new connection`() = runTest {
        val gate = CompletableDeferred<Unit>()
        auth.providersGate = gate
        auth.providersResult = Result.success(REMOTE_PROVIDERS)

        viewModel().providers.test {
            assertEquals(emptyList(), awaitItem())

            sessionState.value = connectedDirect(AuthProcessState.NotStarted)
            expectNoEvents()
            assertEquals(1, auth.getProvidersCalls)

            // Switch connection type mid-fetch. The connection key changes, so
            // the gated Direct fetch is cancelled and a fresh fetch starts for
            // WebRTC (also gated) — distinctUntilChanged must NOT collapse it.
            sessionState.value = connectedWebRTC(AuthProcessState.NotStarted)
            expectNoEvents()
            assertEquals(2, auth.getProvidersCalls)

            // Releasing the gate: the cancelled Direct fetch must not leak;
            // only the WebRTC fetch emits, exactly once.
            gate.complete(Unit)
            assertEquals(REMOTE_PROVIDERS, awaitItem())
            expectNoEvents()

            cancelAndIgnoreRemainingEvents()
        }
    }

    // endregion

    // region Retry trigger

    @Test
    fun `loadProviders triggers a fresh API fetch when the auto path is suppressed by Failed-auth`() = runTest {
        val firstPage = listOf(REMOTE_PROVIDERS.first())
        val secondPage = REMOTE_PROVIDERS

        auth.providersResult = Result.success(firstPage)

        val vm = viewModel()
        vm.providers.test {
            assertEquals(emptyList(), awaitItem())

            sessionState.value = connectedDirect(AuthProcessState.NotStarted)
            assertEquals(firstPage, awaitItem())

            // Enter Failed-auth: auto path would suppress further fetches.
            sessionState.value = connectedDirect(AuthProcessState.Failed("nope"))
            expectNoEvents()

            // User clicks Retry — explicit override of the Failed-auth guard.
            auth.providersResult = Result.success(secondPage)
            vm.loadProviders()
            assertEquals(secondPage, awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(2, auth.getProvidersCalls)
    }

    @Test
    fun `loadProviders while disconnected does nothing`() = runTest {
        val vm = viewModel()
        vm.providers.test {
            assertEquals(emptyList(), awaitItem())

            vm.loadProviders()
            expectNoEvents()

            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(0, auth.getProvidersCalls, "Retry must no-op when there is no session to load against")
    }

    @Test
    fun `rapid retries collapse to only the latest result`() = runTest {
        val firstGate = CompletableDeferred<Unit>()
        val secondGate = CompletableDeferred<Unit>()
        val firstPage = listOf(REMOTE_PROVIDERS.first())
        val secondPage = REMOTE_PROVIDERS

        auth.providersGate = firstGate
        auth.providersResult = Result.success(firstPage)

        val vm = viewModel()
        vm.providers.test {
            assertEquals(emptyList(), awaitItem())

            sessionState.value = connectedDirect(AuthProcessState.NotStarted)
            expectNoEvents()  // first fetch is gated

            // Swap behavior for the retry attempt.
            auth.providersGate = secondGate
            auth.providersResult = Result.success(secondPage)
            vm.loadProviders()

            // Releasing the (cancelled) first gate must NOT emit firstPage.
            firstGate.complete(Unit)
            expectNoEvents()

            // Releasing the second gate emits the second-page result.
            secondGate.complete(Unit)
            assertEquals(secondPage, awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    // endregion

    // region Error path

    @Test
    fun `API failure logs and leaves the provider list at its prior value`() = runTest {
        auth.providersResult = Result.failure(RuntimeException("boom"))

        viewModel().providers.test {
            assertEquals(emptyList(), awaitItem())

            sessionState.value = connectedDirect(AuthProcessState.NotStarted)
            // loadFlow does not emit on failure; previous emission (emptyList) stays.
            expectNoEvents()
            assertEquals(1, auth.getProvidersCalls)

            cancelAndIgnoreRemainingEvents()
        }
    }

    // endregion

    // region Side-effect delegations

    @Test
    fun `login with builtin provider delegates to AuthCoordinator with current credentials`() = runTest {
        val vm = viewModel()
        vm.username.value = "alice"
        vm.password.value = "hunter2"

        vm.login(BUILTIN_PROVIDER_FIXTURE)

        assertEquals(
            listOf(LoginCall(providerId = "builtin", username = "alice", password = "hunter2")),
            auth.loginCalls,
        )
    }

    @Test
    fun `login with redirect-based provider fetches OAuth URL and starts the flow`() = runTest {
        val haProvider = AuthProvider(id = "homeassistant", type = "homeassistant", requiresRedirect = true)
        auth.oauthUrl = Result.success("https://oauth.example/")

        val vm = viewModel()
        vm.login(haProvider)

        assertEquals(listOf("homeassistant" to "musicassistant://auth/callback"), auth.oauthRequests)
        assertEquals(listOf("https://oauth.example/"), auth.startedOAuthUrls)
        assertTrue(auth.loginCalls.isEmpty(), "OAuth flow must not also try credential login")
    }

    @Test
    fun `logout delegates to AuthCoordinator`() = runTest {
        val vm = viewModel()
        vm.logout()

        assertEquals(1, auth.logoutCalls)
    }

    // endregion

    // region Fixtures and helpers

    private fun connectedDirect(authProcessState: AuthProcessState) = SessionState.Connected.Direct(
        connectionInfo = DIRECT_CONNECTION_INFO,
        connectionData = ConnectionData(serverInfo = SERVER_INFO, authProcessState = authProcessState),
    )

    private fun connectedDirectWith(connectionData: ConnectionData) = SessionState.Connected.Direct(
        connectionInfo = DIRECT_CONNECTION_INFO,
        connectionData = connectionData,
    )

    private fun connectedWebRTC(authProcessState: AuthProcessState) = SessionState.Connected.WebRTC(
        remoteId = WEBRTC_REMOTE_ID,
        connectionData = ConnectionData(serverInfo = SERVER_INFO, authProcessState = authProcessState),
    )

    private companion object {
        private val DIRECT_CONNECTION_INFO = ConnectionInfo(host = "fixture.local", port = 8095, isTls = false)
        private val SERVER_INFO = ServerInfo(serverId = "fixture-server")
        private val WEBRTC_REMOTE_ID = RemoteId("ABCDEFGHIJKLMNOPQRSTUVWXYZ")
        private val USER_FIXTURE = User(userId = "u1", username = "alice")

        private val BUILTIN_PROVIDER_FIXTURE = AuthProvider(
            id = "builtin",
            type = "builtin",
            requiresRedirect = false,
        )
        private val REMOTE_PROVIDERS = listOf(
            BUILTIN_PROVIDER_FIXTURE,
            AuthProvider(id = "homeassistant", type = "homeassistant", requiresRedirect = true),
        )
    }

    // endregion
}

private data class LoginCall(val providerId: String, val username: String, val password: String)

/**
 * Fake [AuthCoordinator] with knobs and call counters. Methods that the
 * viewmodel doesn't currently invoke return a benign default rather than
 * throwing, so adding a test that exercises them doesn't require
 * pre-emptive plumbing here.
 */
private class FakeAuthCoordinator : AuthCoordinator {
    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    override val authState: StateFlow<AuthState> = _authState

    var providersResult: Result<List<AuthProvider>> = Result.success(emptyList())

    /**
     * Optional gate. If non-null, [getProviders] awaits it before returning
     * [providersResult]. Lets tests pin down cancellation behaviour by
     * holding a fetch open while session state changes underneath.
     */
    var providersGate: CompletableDeferred<Unit>? = null

    var getProvidersCalls: Int = 0
        private set

    override suspend fun getProviders(): Result<List<AuthProvider>> {
        getProvidersCalls++
        providersGate?.await()
        return providersResult
    }

    val loginCalls = mutableListOf<LoginCall>()

    override suspend fun loginWithCredentials(
        providerId: String,
        username: String,
        password: String,
    ): Result<Unit> {
        loginCalls += LoginCall(providerId, username, password)
        return Result.success(Unit)
    }

    var oauthUrl: Result<String> = Result.success("https://default.example/")
    val oauthRequests = mutableListOf<Pair<String, String>>()

    override suspend fun getOAuthUrl(providerId: String, returnUrl: String): Result<String> {
        oauthRequests += providerId to returnUrl
        return oauthUrl
    }

    val startedOAuthUrls = mutableListOf<String>()

    override fun startOAuthFlow(oauthUrl: String): Result<Unit> {
        startedOAuthUrls += oauthUrl
        return Result.success(Unit)
    }

    var logoutCalls: Int = 0
        private set

    override suspend fun logout(): Result<Unit> {
        logoutCalls++
        return Result.success(Unit)
    }
}
