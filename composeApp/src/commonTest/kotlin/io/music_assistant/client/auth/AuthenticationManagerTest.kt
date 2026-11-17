package io.music_assistant.client.auth

import com.russhwolf.settings.MapSettings
import io.music_assistant.client.api.Answer
import io.music_assistant.client.api.ConnectionInfo
import io.music_assistant.client.api.ConnectionLostException
import io.music_assistant.client.api.Request
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.data.model.server.AuthProvider
import io.music_assistant.client.data.model.server.SERVER_2_11_SCHEMA
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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
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
    fun `a provider load that the transport drops leaves no error on the login screen`() = runTest {
        val client = StubServiceClient()
        client.respond = { Result.failure(ConnectionLostException("Transport disconnected")) }
        val manager = AuthenticationManager(client, SettingsRepository(MapSettings(), MapSettings()))
        try {
            val result = manager.getProviders()

            assertTrue(result.isFailure)
            assertEquals(
                AuthState.Loading,
                manager.authState.value,
                "The reconnect loads the providers again, so a load that died with the old " +
                    "socket must not show as a failure",
            )
        } finally {
            manager.close()
        }
    }

    @Test
    fun `loginWithToken spends the trimmed token as a manual login`() = runTest {
        val client = StubServiceClient()
        val manager = AuthenticationManager(client, SettingsRepository(MapSettings(), MapSettings()))
        try {
            manager.loginWithToken(" long-lived\n")

            assertEquals(
                listOf("long-lived" to false),
                client.authorizeCalls,
                "A pasted token must reach the server without its surrounding whitespace " +
                    "and never as a silent auto-login",
            )
            assertEquals(AuthState.Loading, manager.authState.value)
        } finally {
            manager.close()
        }
    }

    @Test
    fun `getOAuthUrl rethrows cancellation instead of completing with a failure result`() = runTest {
        val client = StubServiceClient()
        val manager = AuthenticationManager(client, SettingsRepository(MapSettings(), MapSettings()))
        try {
            // getOAuthUrl touches authState only for a failure Result, and job.isCancelled is
            // true whether or not the body swallows the cancellation — so the discriminator is the
            // return value: a propagated cancellation yields no Result at all, while a
            // swallowed one is absorbed into Result.failure and assigned here.
            var result: Result<String>? = null
            val job = launch { result = manager.getOAuthUrl(HA_PROVIDER, "musicassistant://auth/callback") }
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

    @Test
    fun `a token saved for this server drives a silent auto-login`() = runTest {
        val client = StubServiceClient()
        val settings = SettingsRepository(MapSettings(), MapSettings())
        settings.setTokenForServer(SERVER_INFO.serverId, "saved-token")
        val manager = AuthenticationManager(client, settings)
        try {
            client.sessionState.value = awaitingAuth()
            runCurrent()

            assertEquals(
                listOf("saved-token" to true),
                client.authorizeCalls,
                "The saved token must be spent as an auto-login",
            )
        } finally {
            manager.close()
        }
    }

    @Test
    fun `a token saved for another server is never offered to this one`() = runTest {
        val client = StubServiceClient()
        val settings = SettingsRepository(MapSettings(), MapSettings())
        // The same address now hosts a different server, for example a beta build.
        settings.setTokenForServer("another-server", "saved-token")
        val manager = AuthenticationManager(client, settings)
        try {
            client.sessionState.value = awaitingAuth()
            runCurrent()

            assertTrue(client.authorizeCalls.isEmpty(), "No token belongs to this server")
            assertTrue(
                client.forceDisconnectCalls.isEmpty(),
                "An unknown server must present the login form, not kill the connection",
            )
        } finally {
            manager.close()
        }
    }

    @Test
    fun `authenticating saves the token under the id the auto-login reads back`() = runTest {
        val client = StubServiceClient()
        val settings = SettingsRepository(MapSettings(), MapSettings())
        val manager = AuthenticationManager(client, settings)
        try {
            client.sessionState.value = authenticated("fresh-token")
            runCurrent()

            assertEquals("fresh-token", settings.getTokenForServer(SERVER_INFO.serverId))
            val entry = settings.connectionHistory.value.single()
            assertEquals(SERVER_INFO.serverId, entry.serverId)
            assertTrue(
                settings.hasCredentialsForAddress(entry.serverIdentifier),
                "The saved-credentials button must light up for this address",
            )
        } finally {
            manager.close()
        }
    }

    // --- Sign-in that returns to the app (PKCE, server 2.11 over Remote Access, issue #1184) ---

    @Test
    fun `a remote sign-in returns to the app and exchanges the code with its verifier`() = runTest {
        val client = StubServiceClient()
        client.sessionState.value = remoteAwaitingAuth()
        client.respond = { request ->
            when (request.command) {
                "auth/authorization_url" -> authorizationUrlAnswer(state = "n.state-1")
                "auth/exchange" -> loginAnswer("exchanged-token")
                else -> null
            }
        }
        val manager = AuthenticationManager(client, SettingsRepository(MapSettings(), MapSettings()))
        try {
            val url = manager.getOAuthUrl(HA_PROVIDER, OAuthCallback.RETURN_URL)
            assertEquals("https://ha.example/auth/authorize", url.getOrNull())
            val start = checkNotNull(client.requests.single().args)
            assertEquals("app", start.string("redirect_target"))
            assertEquals("S256", start.string("code_challenge_method"))

            manager.handleOAuthCallbackUrl(
                "musicassistant://auth/callback?code=ha-code&state=n.state-1",
            )
            runCurrent()

            val exchange = checkNotNull(client.requests.last().args)
            assertEquals("n.state-1", exchange.string("state"))
            assertEquals("ha-code", exchange.string("code"))
            assertEquals(
                start.string("code_challenge"),
                Pkce.challengeOf(checkNotNull(exchange.string("code_verifier"))),
                "The verifier must be the one whose challenge started the sign-in",
            )
            assertEquals(listOf("exchanged-token" to false), client.authorizeCalls)
        } finally {
            manager.close()
        }
    }

    @Test
    fun `a direct session keeps the server callback flow`() = runTest {
        val client = StubServiceClient()
        client.sessionState.value = awaitingAuth(
            serverInfo = ServerInfo(serverId = "server-1", schemaVersion = SERVER_2_11_SCHEMA),
        )
        client.respond = { authorizationUrlAnswer(state = "w.state-1") }
        val manager = AuthenticationManager(client, SettingsRepository(MapSettings(), MapSettings()))
        try {
            manager.getOAuthUrl(HA_PROVIDER, OAuthCallback.RETURN_URL)

            val start = checkNotNull(client.requests.single().args)
            assertNull(start["code_challenge"], "The server refuses the app return outside Remote Access")
            assertNull(start["redirect_target"])
        } finally {
            manager.close()
        }
    }

    @Test
    fun `a remote session on server 2_10 keeps the server callback flow`() = runTest {
        val client = StubServiceClient()
        client.sessionState.value = remoteAwaitingAuth(schemaVersion = SERVER_2_11_SCHEMA - 1)
        client.respond = { authorizationUrlAnswer(state = "w.state-1") }
        val manager = AuthenticationManager(client, SettingsRepository(MapSettings(), MapSettings()))
        try {
            manager.getOAuthUrl(HA_PROVIDER, OAuthCallback.RETURN_URL)

            assertNull(checkNotNull(client.requests.single().args)["code_challenge"])
        } finally {
            manager.close()
        }
    }

    @Test
    fun `a code for a sign-in this app did not start fails without an exchange`() = runTest {
        val client = StubServiceClient()
        client.sessionState.value = remoteAwaitingAuth()
        client.respond = { authorizationUrlAnswer(state = "n.state-1") }
        val manager = AuthenticationManager(client, SettingsRepository(MapSettings(), MapSettings()))
        try {
            manager.oauthHandler = FakeOAuthHandler(reportsCancellation = true)
            manager.getOAuthUrl(HA_PROVIDER, OAuthCallback.RETURN_URL)
            manager.startOAuthFlow("https://ha.example/auth/authorize")

            manager.handleOAuthCallbackUrl(
                "musicassistant://auth/callback?code=ha-code&state=n.other",
            )
            runCurrent()

            assertIs<AuthState.Error>(manager.authState.value)
            assertTrue(client.requests.none { it.command == "auth/exchange" })
            assertTrue(client.authorizeCalls.isEmpty())
        } finally {
            manager.close()
        }
    }

    @Test
    fun `a refused exchange shows the server error`() = runTest {
        val client = StubServiceClient()
        client.sessionState.value = remoteAwaitingAuth()
        client.respond = { request ->
            when (request.command) {
                "auth/authorization_url" -> authorizationUrlAnswer(state = "n.state-1")
                else -> Result.success(
                    answer(
                        buildJsonObject {
                            put("success", false)
                            put("error", "This sign-in is invalid or has expired")
                        },
                    ),
                )
            }
        }
        val manager = AuthenticationManager(client, SettingsRepository(MapSettings(), MapSettings()))
        try {
            manager.getOAuthUrl(HA_PROVIDER, OAuthCallback.RETURN_URL)
            manager.handleOAuthCallbackUrl(
                "musicassistant://auth/callback?code=ha-code&state=n.state-1",
            )
            runCurrent()

            assertEquals(
                AuthState.Error("This sign-in is invalid or has expired"),
                manager.authState.value,
            )
            assertTrue(client.authorizeCalls.isEmpty())
        } finally {
            manager.close()
        }
    }

    @Test
    fun `a reconnect after the exchange spends the token and does not repeat the exchange`() = runTest {
        val client = StubServiceClient()
        client.sessionState.value = remoteAwaitingAuth()
        client.respond = { request ->
            when (request.command) {
                "auth/authorization_url" -> authorizationUrlAnswer(state = "n.state-1")
                "auth/exchange" -> loginAnswer("exchanged-token")
                else -> null
            }
        }
        val manager = AuthenticationManager(client, SettingsRepository(MapSettings(), MapSettings()))
        try {
            manager.getOAuthUrl(HA_PROVIDER, OAuthCallback.RETURN_URL)
            manager.handleOAuthCallbackUrl(
                "musicassistant://auth/callback?code=ha-code&state=n.state-1",
            )
            runCurrent()

            // The transport bounces: `auth` resolved to Aborted, nothing settled.
            client.sessionState.value = SessionState.Reconnecting.WebRTC(
                attempt = 1,
                remoteId = REMOTE_ID,
            )
            runCurrent()
            client.sessionState.value = remoteAwaitingAuth()
            runCurrent()

            assertEquals(1, client.requests.count { it.command == "auth/exchange" })
            assertEquals(
                listOf("exchanged-token" to false, "exchanged-token" to false),
                client.authorizeCalls,
                "The server accepts a code once, so the retry must spend the token",
            )
        } finally {
            manager.close()
        }
    }

    // --- Login errors and unusable providers ---

    @Test
    fun `a refused OAuth start shows the server error`() = runTest {
        val client = StubServiceClient()
        client.respond = {
            Result.success(
                answer(
                    buildJsonObject {
                        put("authorization_url", JsonNull)
                        put("error", "Provider does not support OAuth or does not exist")
                    },
                ),
            )
        }
        val manager = AuthenticationManager(client, SettingsRepository(MapSettings(), MapSettings()))
        try {
            val result = manager.getOAuthUrl(HA_PROVIDER, OAuthCallback.RETURN_URL)

            assertTrue(result.isFailure)
            assertEquals(
                AuthState.Error("Provider does not support OAuth or does not exist"),
                manager.authState.value,
                "The tap must not end silently: the browser never opens",
            )
        } finally {
            manager.close()
        }
    }

    @Test
    fun `over Remote Access a redirect provider shows only when the browser can come back`() = runTest {
        val remote210 = remoteAwaitingAuth(schemaVersion = SERVER_2_11_SCHEMA - 1)
        assertEquals(
            listOf("builtin"),
            loadedProviderIds(remote210, supportsRemoteApp = true),
            "No External URL and no return to the app: the callback is an internal address",
        )
        assertEquals(
            listOf("builtin", "homeassistant"),
            loadedProviderIds(
                remote210.copy(
                    connectionData = remote210.connectionData.copy(
                        serverInfo = ServerInfo(
                            serverId = "server-1",
                            schemaVersion = SERVER_2_11_SCHEMA - 1,
                            externalUrl = "https://ma.example",
                        ),
                    ),
                ),
                supportsRemoteApp = false,
            ),
            "The server callback goes to the External URL",
        )
        assertEquals(
            listOf("builtin", "homeassistant"),
            loadedProviderIds(remoteAwaitingAuth(), supportsRemoteApp = true),
            "The sign-in returns to the app",
        )
        assertEquals(
            listOf("builtin"),
            loadedProviderIds(remoteAwaitingAuth(), supportsRemoteApp = false),
        )
        assertEquals(
            listOf("builtin", "homeassistant"),
            loadedProviderIds(awaitingAuth(), supportsRemoteApp = false),
            "A direct session reaches the server callback itself",
        )
    }

    private suspend fun loadedProviderIds(session: SessionState, supportsRemoteApp: Boolean): List<String> {
        val client = StubServiceClient()
        client.sessionState.value = session
        client.respond = {
            Result.success(
                Answer(
                    buildJsonObject {
                        put(
                            "result",
                            JsonArray(
                                listOf(
                                    buildJsonObject {
                                        put("provider_id", "builtin")
                                        put("provider_type", "builtin")
                                        put("requires_redirect", false)
                                    },
                                    buildJsonObject {
                                        put("provider_id", "homeassistant")
                                        put("provider_type", "homeassistant")
                                        put("requires_redirect", true)
                                        put("supports_remote_app", supportsRemoteApp)
                                    },
                                ),
                            ),
                        )
                    },
                ),
            )
        }
        val manager = AuthenticationManager(client, SettingsRepository(MapSettings(), MapSettings()))
        return try {
            manager.getProviders().getOrThrow().map { it.id }
        } finally {
            manager.close()
        }
    }

    @Test
    fun `the PKCE challenge matches the RFC 7636 example`() = runTest {
        assertEquals(
            "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
            Pkce.challengeOf("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"),
        )
        val pkce = Pkce.create()
        assertEquals(43, pkce.verifier.length, "The server accepts 43 to 128 characters")
        assertEquals(Pkce.challengeOf(pkce.verifier), pkce.challenge)
    }

    private fun remoteAwaitingAuth(schemaVersion: Int = SERVER_2_11_SCHEMA) = SessionState.Connected.WebRTC(
        remoteId = REMOTE_ID,
        connectionData = ConnectionData(
            serverInfo = ServerInfo(serverId = "server-1", schemaVersion = schemaVersion),
        ),
    )

    private fun JsonObject.string(key: String) = get(key)?.jsonPrimitive?.contentOrNull

    private fun answer(result: JsonObject) = Answer(buildJsonObject { put("result", result) })

    private fun authorizationUrlAnswer(state: String) = Result.success(
        answer(
            buildJsonObject {
                put("authorization_url", "https://ha.example/auth/authorize")
                put("state", state)
            },
        ),
    )

    private fun loginAnswer(token: String) = Result.success(
        answer(
            buildJsonObject {
                put("success", true)
                put("access_token", token)
                put("user", buildJsonObject { put("username", "daveb") })
            },
        ),
    )

    private fun awaitingAuth(
        authProcessState: AuthProcessState = AuthProcessState.NotStarted,
        serverInfo: ServerInfo = SERVER_INFO,
    ) = SessionState.Connected.Direct(
        connectionInfo = CONNECTION,
        connectionData = ConnectionData(
            serverInfo = serverInfo,
            authProcessState = authProcessState,
        ),
    )

    private fun authenticated(token: String) = SessionState.Connected.Direct(
        connectionInfo = CONNECTION,
        connectionData = ConnectionData(
            serverInfo = SERVER_INFO,
            user = USER,
            token = token,
        ),
    )

    private companion object {
        val CONNECTION = ConnectionInfo(host = "ma.local", port = 8095, isTls = false)
        val SERVER_INFO = ServerInfo(serverId = "server-1")
        val USER = User(username = "daveb")
        val REMOTE_ID = RemoteId("PGSVXKGZJCFA6MOH4UPBH5Q9HY")
        val HA_PROVIDER = AuthProvider(
            id = "homeassistant",
            type = "homeassistant",
            requiresRedirect = true,
            supportsRemoteApp = true,
        )
    }
}

/**
 * Minimal [ServiceClient] for manager tests. [sendRequest] parks via
 * [awaitCancellation], unless [respond] answers, so a caller can be cancelled
 * mid-request; the session stays Disconnected unless a test sets it.
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

    val requests = mutableListOf<Request>()

    /** Answers a request, or null to park it until cancelled. */
    var respond: (Request) -> Result<Answer>? = { null }

    override suspend fun sendRequest(request: Request): Result<Answer> {
        requests += request
        return respond(request) ?: awaitCancellation()
    }
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
    override fun requestCommandRecovery() = Unit
    override fun onPlaybackActive() = Unit
    override fun onExternalConsumerInactive() = Unit
    override fun onPlaybackInactive() = Unit
    val forceDisconnectCalls = mutableListOf<Exception>()

    override fun forceDisconnect(reason: Exception) {
        forceDisconnectCalls += reason
    }
    override fun noServer() = Unit
}

/** Records presentation without doing any. */
private class FakeOAuthHandler(override val reportsCancellation: Boolean) : OAuthHandler {
    val openedUrls = mutableListOf<String>()
    override fun openOAuthUrl(url: String) {
        openedUrls += url
    }
}
