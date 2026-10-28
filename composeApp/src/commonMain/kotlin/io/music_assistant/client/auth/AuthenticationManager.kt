package io.music_assistant.client.auth

import co.touchlab.kermit.Logger
import io.music_assistant.client.api.ConnectionLostException
import io.music_assistant.client.api.Request
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.data.model.server.AuthProvider
import io.music_assistant.client.data.model.server.LoginResponse
import io.music_assistant.client.data.model.server.OauthUrl
import io.music_assistant.client.data.model.server.User
import io.music_assistant.client.data.model.server.supportsAppSignIn
import io.music_assistant.client.settings.ConnectionHistoryEntry
import io.music_assistant.client.settings.SettingsRepository
import io.music_assistant.client.utils.AuthProcessState
import io.music_assistant.client.utils.DataConnectionState
import io.music_assistant.client.utils.SessionState
import io.music_assistant.client.utils.mainDispatcher
import io.music_assistant.client.utils.resultAs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class AuthState {
    data object Idle : AuthState()
    data object Loading : AuthState()
    data class ProvidersLoaded(val providers: List<AuthProvider>) : AuthState()
    data class Authenticated(val user: User) : AuthState()
    data class Error(val message: String) : AuthState()
}

private val log = Logger.withTag("AuthMgr")

class AuthenticationManager(
    private val serviceClient: ServiceClient,
    private val settings: SettingsRepository,
) : AuthCoordinator {
    private val scope = CoroutineScope(SupervisorJob() + mainDispatcher)

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    // OAuthHandler will be set by platform (e.g., MainActivity on Android)
    var oauthHandler: OAuthHandler? = null

    // True while an OAuth browser is open and we're awaiting its deep-link callback.
    private var awaitingOAuthCallback = false

    /**
     * Grant from an OAuth deep link, held until an auth attempt consumes it.
     *
     * The deep link foregrounds the app, and foregrounding tears the transport down for
     * a reconnect. Sending the grant straight away would write it into a socket that is
     * already dying, so the server never sees it and the user gets a connectivity error
     * on a login that actually succeeded. Instead we buffer the grant and let the
     * `AwaitingAuth(NotStarted)` branch below spend it once the transport settles — the
     * same state-driven path the saved-token auto-login already uses. The grant survives
     * an aborted attempt, so a reconnect mid-round-trip retries instead of dead-ending.
     */
    private var pendingOAuthGrant: OAuthGrant? = null

    /** Guards against the two entry points spending [pendingOAuthGrant] twice. */
    private var spendingOAuthGrant = false

    /** Fails the flow if [pendingOAuthGrant] is never spent. */
    private var pendingOAuthWatchdog: Job? = null

    /** The sign-in that returns to the app, waiting for its authorization code. */
    private var pendingAppSignIn: AppSignIn? = null

    // Flag to prevent auto-login during intentional logout - using StateFlow for proper synchronization
    private val _isLoggingOut = MutableStateFlow(false)
    private val isLoggingOut: Boolean
        get() = _isLoggingOut.value

    /**
     * Snapshot at construction: will the cold-launch auto-connect produce a silent auto-login?
     * True iff the most-recent saved server has a saved token.
     */
    val willAutoLoginOnLaunch: Boolean =
        settings.connectionHistory.value.firstOrNull()
            ?.serverId?.let { settings.getTokenForServer(it) } != null

    init {
        // Monitor session state to update auth UI state
        scope.launch {
            serviceClient.sessionState.collect { state ->
                if (state is SessionState.Connected) {
                    when (val dataConnectionState = state.dataConnectionState) {
                        is DataConnectionState.AwaitingAuth -> {
                            when (dataConnectionState.authProcessState) {
                                AuthProcessState.NotStarted -> {
                                    // A pending OAuth grant outranks the saved token: the user
                                    // just completed an interactive login, so no server-id
                                    // check applies — the fresh grant defines the server.
                                    val pending = pendingOAuthGrant
                                    if (pending != null) {
                                        log.i { "AwaitingAuth(NotStarted) — spending pending OAuth grant" }
                                        spendOAuthGrant(pending)
                                    } else if (isLoggingOut) {
                                        log.i { "AwaitingAuth(NotStarted) — skipping auto-login (logging out)" }
                                    } else {
                                        // Keyed by server id, so a different server at the
                                        // same address simply has no token here: the login
                                        // fields unblock instead of the flow dying.
                                        val serverId = dataConnectionState.serverInfo.serverId
                                        val token = settings.getTokenForServer(serverId)
                                        if (token == null) {
                                            log.i { "AwaitingAuth(NotStarted) — no saved token for server" }
                                        } else {
                                            log.i { "AwaitingAuth(NotStarted) — auto-login with saved token" }
                                            authorizeWithSavedToken(token)
                                        }
                                    }
                                }

                                AuthProcessState.InProgress -> {
                                    log.i { "AwaitingAuth(InProgress)" }
                                    _authState.value = AuthState.Loading
                                }

                                is AuthProcessState.Failed -> {
                                    clearPendingOAuthGrant()
                                    settings.setTokenForServer(
                                        dataConnectionState.serverInfo.serverId,
                                        null,
                                    )
                                    log.i { "Cleared token for server due to auth failure" }

                                    log.i {
                                        "AwaitingAuth(Failed): " +
                                            dataConnectionState.authProcessState.reason
                                    }
                                    _authState.value =
                                        AuthState.Error(dataConnectionState.authProcessState.reason)
                                }

                                AuthProcessState.LoggedOut -> {
                                    clearPendingOAuthGrant()
                                    settings.setTokenForServer(
                                        dataConnectionState.serverInfo.serverId,
                                        null,
                                    )
                                    log.d { "Cleared token for server" }

                                    log.i { "AwaitingAuth(LoggedOut)" }
                                    _authState.value = AuthState.Idle
                                }
                            }
                        }

                        is DataConnectionState.Authenticated -> {
                            state.user?.let { user ->
                                clearPendingOAuthGrant()
                                log.i { "Authenticated" }
                                _authState.value = AuthState.Authenticated(user)

                                val serverInfo = dataConnectionState.serverInfo
                                settings.setTokenForServer(
                                    serverInfo.serverId,
                                    dataConnectionState.token,
                                )
                                // The one point where the connection is proven to work and
                                // the server has named itself, so the only place to save it.
                                settings.addOrUpdateHistoryEntry(
                                    ConnectionHistoryEntry.from(state, serverInfo),
                                )
                            }
                        }

                        DataConnectionState.AwaitingServerInfo -> {
                            // Logged so a stuck reconnect (no `server/hello`) is observable.
                            log.i { "AwaitingServerInfo" }
                        }
                    }
                }
            }
        }

        // Recover from an abandoned OAuth flow: if the user backs out of the
        // external browser, no callback arrives and authState is stuck on
        // Loading. Only for handlers that cannot report the cancellation
        // themselves — an in-app session does, and it never backgrounds the app,
        // so for it this heuristic could only ever fire spuriously.
        scope.launch {
            serviceClient.foregroundEvents.collect {
                if (oauthHandler?.reportsCancellation == true) return@collect
                if (awaitingOAuthCallback) log.i { "OAuth flow abandoned (foregrounded without callback)" }
                cancelOAuthFlow(reason = null)
            }
        }
    }

    override suspend fun getProviders(): Result<List<AuthProvider>> {
        return try {
            _authState.value = AuthState.Loading
            val response = serviceClient.sendRequest(Request.Auth.providers())

            response.exceptionOrNull()?.let { cause ->
                // The transport dropped the request, so a reconnect follows and the
                // viewmodel loads the providers again on the new socket. An error here
                // would sit on the login screen until that load.
                if (cause is ConnectionLostException) return Result.failure(cause)
                val error = "Failed to fetch auth providers"
                _authState.value = AuthState.Error(error)
                return Result.failure(Exception(error))
            }

            response.resultAs<List<AuthProvider>>()?.filter(::canSignIn)?.let { providers ->
                _authState.value = AuthState.ProvidersLoaded(providers)
                Result.success(providers)
            } ?: run {
                val error = "Failed to parse providers"
                _authState.value = AuthState.Error(error)
                Result.failure(Exception(error))
            }
        } catch (e: CancellationException) {
            // Coroutine cancellation (e.g. AuthenticationViewModel's flatMapLatest
            // switching loads on a session-state change) must propagate, not be
            // swallowed by the broad catch below — otherwise it would spuriously
            // drive authState to Error on every disconnect/connection-type switch.
            throw e
        } catch (e: Exception) {
            val error = e.message ?: "Exception fetching providers"
            _authState.value = AuthState.Error(error)
            Result.failure(e)
        }
    }

    @Suppress("UnusedParameter") // providerId reserved — current server login API doesn't yet route per-provider
    override suspend fun loginWithCredentials(
        providerId: String,
        username: String,
        password: String,
    ): Result<Unit> = manualLogin { serviceClient.login(username, password) }

    /**
     * The server checks a long-lived token like a session token, so the same `auth`
     * command signs in. A pasted token often carries a line break, so it is trimmed.
     */
    override suspend fun loginWithToken(token: String): Result<Unit> =
        manualLogin { serviceClient.authorize(token.trim(), isAutoLogin = false) }

    /** A sign-in that the user starts: it lifts the logout block and shows its failure. */
    private suspend fun manualLogin(send: suspend () -> Unit): Result<Unit> {
        return try {
            _isLoggingOut.value = false
            _authState.value = AuthState.Loading
            send()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val error = e.message ?: "Login failed"
            _authState.value = AuthState.Error(error)
            Result.failure(e)
        }
    }

    /** A failure shows on the login screen, because the sign-in never reaches the browser. */
    override suspend fun getOAuthUrl(provider: AuthProvider, returnUrl: String): Result<String> =
        requestOAuthUrl(provider, returnUrl).onFailure {
            log.w(it) { "OAuth URL not received" }
            _authState.value = AuthState.Error(it.message ?: "Failed to get OAuth URL")
        }

    private suspend fun requestOAuthUrl(provider: AuthProvider, returnUrl: String): Result<String> {
        return try {
            val pkce = if (returnsToApp(provider)) Pkce.create() else null
            val response = serviceClient.sendRequest(
                Request.Auth.authorizationUrl(provider.id, returnUrl, pkce?.challenge),
            )

            if (response.isFailure) {
                return Result.failure(Exception("Failed to get OAuth URL"))
            }

            val oauthUrl = response.resultAs<OauthUrl>()
                ?: return Result.failure(Exception("Failed to parse OAuth URL"))
            val url = oauthUrl.url
                ?: return Result.failure(Exception(oauthUrl.error ?: "Failed to get OAuth URL"))
            pendingAppSignIn = pkce?.let {
                val state = oauthUrl.state
                    ?: return Result.failure(Exception("No sign-in state received"))
                AppSignIn(state, it.verifier)
            }
            Result.success(url)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * The browser returns to the app only over Remote Access, from server 2.11, and only
     * when the provider is reachable from outside the home network. Otherwise the server's
     * own callback page completes the sign-in, as on server 2.10.
     */
    private fun returnsToApp(provider: AuthProvider): Boolean {
        val session = serviceClient.sessionState.value as? SessionState.Connected.WebRTC ?: return false
        return provider.supportsRemoteApp && supportsAppSignIn(session.serverInfo?.schemaVersion)
    }

    /**
     * Over Remote Access, a redirect provider works only when the browser can come back: to the
     * app, or to the server's External URL. Otherwise the server sends the browser to its internal
     * address. The web frontend hides the provider in the same case.
     */
    private fun canSignIn(provider: AuthProvider): Boolean {
        val serverInfo = (serviceClient.sessionState.value as? SessionState.Connected.WebRTC)
            ?.serverInfo ?: return true
        return !provider.requiresRedirect ||
            returnsToApp(provider) ||
            !serverInfo.externalUrl.isNullOrBlank()
    }

    override fun startOAuthFlow(oauthUrl: String): Result<Unit> {
        val handler = oauthHandler
        if (handler == null) {
            val error = "OAuth not supported on this platform"
            _authState.value = AuthState.Error(error)
            return Result.failure(Exception(error))
        }

        return try {
            _authState.value = AuthState.Loading
            // Set BEFORE the call, not after: a handler that fails synchronously
            // reports it through cancelOAuthFlow() from inside openOAuthUrl, and
            // setting the flag afterwards would resurrect a flow that already ended.
            awaitingOAuthCallback = true
            handler.openOAuthUrl(oauthUrl)
            Result.success(Unit)
        } catch (e: Exception) {
            awaitingOAuthCallback = false
            val error = e.message ?: "OAuth flow failed"
            _authState.value = AuthState.Error(error)
            Result.failure(e)
        }
    }

    /**
     * End a pending OAuth flow that produced no token: the user dismissed the auth
     * session, or presenting it failed.
     *
     * Idempotent and a no-op when no flow is pending, so the handler may call it
     * without tracking whether anything else already settled the flow. [reason] is
     * shown to the user; a plain dismissal passes null and drops back to [AuthState.Idle]
     * silently.
     */
    fun cancelOAuthFlow(reason: String?) {
        if (!awaitingOAuthCallback || _authState.value !is AuthState.Loading) return
        awaitingOAuthCallback = false
        log.i { "OAuth flow cancelled${reason?.let { ": $it" } ?: ""}" }
        _authState.value = reason?.let { AuthState.Error(it) } ?: AuthState.Idle
    }

    /**
     * Route an incoming URL that may be an OAuth callback. The single entry point for
     * all three delivery paths: the iOS in-app auth session, the iOS deep-link handler
     * and Android's launch intent.
     *
     * @return true when the URL was an OAuth callback and has been dealt with, so a
     *   deep-link dispatcher knows not to forward it anywhere else.
     */
    fun handleOAuthCallbackUrl(urlString: String): Boolean =
        when (val result = OAuthCallback.parse(urlString)) {
            is OAuthCallbackResult.Token -> {
                handleOAuthCallback(result.token)
                true
            }

            is OAuthCallbackResult.AuthorizationCode -> {
                handleAuthorizationCode(result.code, result.state)
                true
            }

            is OAuthCallbackResult.Failed -> {
                cancelOAuthFlow(result.reason)
                true
            }

            OAuthCallbackResult.NotOAuth -> false
        }

    fun handleOAuthCallback(token: String) = acceptOAuthGrant(OAuthGrant.Token(token))

    /**
     * Only this app run can complete a sign-in that returns to the app, because only it
     * holds the PKCE verifier. A code for another sign-in, for example one started before
     * the app was restarted, can only fail.
     */
    private fun handleAuthorizationCode(code: String, state: String) {
        val signIn = pendingAppSignIn?.takeIf { it.state == state } ?: run {
            log.w { "OAuth: authorization code for an unknown sign-in" }
            cancelOAuthFlow("This sign-in has expired. Please try again.")
            return
        }
        pendingAppSignIn = null
        acceptOAuthGrant(OAuthGrant.Code(code, state, signIn.codeVerifier))
    }

    private fun acceptOAuthGrant(grant: OAuthGrant) {
        log.d { "OAuth callback received" }
        // Clear synchronously (before the launch) so the foreground collector,
        // which fires after this on the success path, sees no pending flow.
        awaitingOAuthCallback = false
        _isLoggingOut.value = false
        pendingOAuthGrant = grant
        _authState.value = AuthState.Loading

        pendingOAuthWatchdog?.cancel()
        pendingOAuthWatchdog = scope.launch {
            delay(OAUTH_TOKEN_WAIT_MS)
            if (pendingOAuthGrant == null) return@launch
            pendingOAuthGrant = null
            log.e { "OAuth: grant unspent after ${OAUTH_TOKEN_WAIT_MS}ms — cannot authorize" }
            _authState.value = AuthState.Error("Connection timeout. Please try again.")
        }

        // The session may already sit in a state that can spend the grant — including
        // `Failed` from an earlier attempt, which never emits again on its own. The
        // collector saw that state before the grant existed and a StateFlow does not
        // repeat itself, so drive it here. `InProgress` is left alone: an attempt is
        // already in flight, and its outcome re-enters the collector.
        scope.launch {
            val awaitingAuth = (serviceClient.sessionState.value as? SessionState.Connected)
                ?.dataConnectionState as? DataConnectionState.AwaitingAuth
            if (awaitingAuth != null && awaitingAuth.authProcessState != AuthProcessState.InProgress) {
                spendOAuthGrant(grant)
            }
        }
    }

    /**
     * Spend a deep-link OAuth grant. The grant is NOT cleared here: an attempt that the
     * transport aborts mid-round-trip leaves the session state untouched, so the next
     * `AwaitingAuth(NotStarted)` must be able to retry with it. Only a settled outcome
     * (Authenticated, Failed, LoggedOut) or the watchdog clears it.
     */
    private suspend fun spendOAuthGrant(grant: OAuthGrant) {
        if (spendingOAuthGrant) return
        spendingOAuthGrant = true
        try {
            val token = when (grant) {
                is OAuthGrant.Token -> grant.token
                is OAuthGrant.Code -> exchangeAuthorizationCode(grant) ?: return
            }
            serviceClient.authorize(token, isAutoLogin = false)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.e(e) { "Authorization failed" }
            clearPendingOAuthGrant()
            _authState.value = AuthState.Error(e.message ?: "Authorization failed")
        } finally {
            spendingOAuthGrant = false
        }
    }

    /**
     * Exchange [grant] for an access token, or return null when there is none to spend.
     *
     * The server accepts a code once, so the token replaces the grant: a retry after an
     * aborted `auth` spends the token and never repeats the exchange. A connection loss
     * keeps the grant, because the request may not have reached the server.
     */
    private suspend fun exchangeAuthorizationCode(grant: OAuthGrant.Code): String? {
        val answer = serviceClient.sendRequest(
            Request.Auth.exchange(grant.state, grant.code, grant.codeVerifier, settings.deviceName.value),
        ).getOrElse {
            log.w(it) { "OAuth: code exchange not answered" }
            return null
        }
        // A logout or a newer sign-in replaced the grant while the exchange was in flight.
        if (pendingOAuthGrant != grant) return null
        val response = answer.resultAs<LoginResponse>()
        val token = response?.token?.takeIf { response.success && it.isNotBlank() } ?: run {
            log.w { "OAuth: code exchange refused: ${response?.error}" }
            clearPendingOAuthGrant()
            _authState.value = AuthState.Error(response?.error ?: "Sign-in failed")
            return null
        }
        pendingOAuthGrant = OAuthGrant.Token(token)
        return token
    }

    private fun clearPendingOAuthGrant() {
        pendingOAuthGrant = null
        pendingOAuthWatchdog?.cancel()
        pendingOAuthWatchdog = null
    }

    private suspend fun authorizeWithSavedToken(token: String) {
        try {
            serviceClient.authorize(token, isAutoLogin = true)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Silent failure - user will see auth UI
        }
    }

    override suspend fun logout(): Result<Unit> {
        return try {
            // Set flag FIRST, before any async operations
            _isLoggingOut.value = true
            clearPendingOAuthGrant()
            pendingAppSignIn = null
            val currentState = serviceClient.sessionState.value
            (currentState as? SessionState.Connected)?.serverInfo?.serverId?.let {
                settings.setTokenForServer(it, null)
            }

            serviceClient.logout()
            _authState.value = AuthState.Idle
            // Keep the flag set to prevent auto-login until user explicitly logs in again
            Result.success(Unit)
        } catch (e: Exception) {
            _isLoggingOut.value = false
            Result.failure(e)
        }
    }

    fun close() {
        scope.cancel()
    }

    /** What an OAuth deep link carries to complete the sign-in. */
    private sealed interface OAuthGrant {
        data class Token(val token: String) : OAuthGrant

        data class Code(val code: String, val state: String, val codeVerifier: String) : OAuthGrant
    }

    private data class AppSignIn(val state: String, val codeVerifier: String)

    private companion object {
        /**
         * How long a deep-link OAuth grant may wait for a transport that can spend it.
         * Longer than the old connect wait because the window now covers a full
         * foreground reconnect, including its backoff, not just a readiness check.
         */
        const val OAUTH_TOKEN_WAIT_MS = 20_000L
    }
}
