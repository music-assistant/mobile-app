package io.music_assistant.client.auth

import co.touchlab.kermit.Logger
import io.music_assistant.client.api.Request
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.data.model.server.AuthProvider
import io.music_assistant.client.data.model.server.OauthUrl
import io.music_assistant.client.data.model.server.User
import io.music_assistant.client.settings.ConnectionType
import io.music_assistant.client.settings.SettingsRepository
import io.music_assistant.client.utils.AuthProcessState
import io.music_assistant.client.utils.DataConnectionState
import io.music_assistant.client.utils.SessionState
import io.music_assistant.client.utils.mainDispatcher
import io.music_assistant.client.utils.resultAs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

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

    // Flag to prevent auto-login during intentional logout - using StateFlow for proper synchronization
    private val _isLoggingOut = MutableStateFlow(false)
    private val isLoggingOut: Boolean
        get() = _isLoggingOut.value

    /**
     * Snapshot at construction: will the cold-launch auto-connect produce a silent auto-login?
     * True iff the most-recent saved server has a saved token. Mirrors the identifier resolution
     * used by the [init] block below and by `KtorServiceClient.init`'s auto-connect path.
     */
    val willAutoLoginOnLaunch: Boolean = run {
        val mostRecent = settings.connectionHistory.value.firstOrNull() ?: return@run false
        val identifier = when (mostRecent.type) {
            ConnectionType.DIRECT -> mostRecent.connectionInfo?.let {
                settings.getDirectServerIdentifier(it.host, it.port, it.isTls)
            }
            ConnectionType.WEBRTC -> mostRecent.remoteId?.let {
                settings.getWebRTCServerIdentifier(it)
            }
        } ?: return@run false
        settings.getTokenForServer(identifier) != null
    }

    init {
        // Monitor session state to update auth UI state
        scope.launch {
            serviceClient.sessionState.collect { state ->
                if (state is SessionState.Connected) {
                    when (val dataConnectionState = state.dataConnectionState) {
                        is DataConnectionState.AwaitingAuth -> {
                            when (dataConnectionState.authProcessState) {
                                AuthProcessState.NotStarted -> {
                                    // Try auto-login with saved token (unless we're intentionally logging out)
                                    if (isLoggingOut) {
                                        log.i { "AwaitingAuth(NotStarted) — skipping auto-login (logging out)" }
                                    } else {
                                        val serverIdentifier = settings.getServerIdentifier(state)
                                        val token = settings.getTokenForServer(serverIdentifier)
                                        if (token == null) {
                                            log.i { "AwaitingAuth(NotStarted) — no saved token for server" }
                                        } else {
                                            log.i { "AwaitingAuth(NotStarted) — auto-login with saved token" }

                                            val currentServerId =
                                                dataConnectionState.serverInfo.serverId
                                            val previousServerId =
                                                settings.getIdForServer(serverIdentifier)
                                            if (previousServerId == null || currentServerId == previousServerId) {
                                                authorizeWithSavedToken(token)
                                            } else {
                                                serviceClient.forceDisconnect(
                                                    ServerIdMismatchException(),
                                                )
                                            }
                                        }
                                    }
                                }

                                AuthProcessState.InProgress -> {
                                    log.i { "AwaitingAuth(InProgress)" }
                                    _authState.value = AuthState.Loading
                                }

                                is AuthProcessState.Failed -> {
                                    val serverIdentifier = settings.getServerIdentifier(state)
                                    settings.setTokenForServer(serverIdentifier, null)
                                    log.i { "Cleared token for server due to auth failure" }

                                    log.i {
                                        "AwaitingAuth(Failed): " +
                                            dataConnectionState.authProcessState.reason
                                    }
                                    _authState.value =
                                        AuthState.Error(dataConnectionState.authProcessState.reason)
                                }

                                AuthProcessState.LoggedOut -> {
                                    val serverIdentifier = settings.getServerIdentifier(state)
                                    settings.setTokenForServer(serverIdentifier, null)
                                    log.d { "Cleared token for server" }

                                    log.i { "AwaitingAuth(LoggedOut)" }
                                    _authState.value = AuthState.Idle
                                }
                            }
                        }

                        is DataConnectionState.Authenticated -> {
                            state.user?.let { user ->
                                log.i { "Authenticated" }
                                _authState.value = AuthState.Authenticated(user)

                                val serverIdentifier = settings.getServerIdentifier(state)
                                val serverId = dataConnectionState.serverInfo.serverId
                                settings.setIdForServer(serverIdentifier, serverId)
                                settings.setTokenForServer(
                                    serverIdentifier,
                                    dataConnectionState.token,
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
        // Loading.
        scope.launch {
            serviceClient.foregroundEvents.collect {
                if (awaitingOAuthCallback && _authState.value is AuthState.Loading) {
                    awaitingOAuthCallback = false
                    log.i { "OAuth flow abandoned (foregrounded without callback)" }
                    _authState.value = AuthState.Idle
                }
            }
        }
    }

    override suspend fun getProviders(): Result<List<AuthProvider>> {
        return try {
            _authState.value = AuthState.Loading
            val response = serviceClient.sendRequest(Request.Auth.providers())

            if (response.isFailure) {
                val error = "Failed to fetch auth providers"
                _authState.value = AuthState.Error(error)
                return Result.failure(Exception(error))
            }

            response.resultAs<List<AuthProvider>>()?.let { providers ->
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
    ): Result<Unit> {
        return try {
            _isLoggingOut.value = false  // Reset flag when user explicitly logs in
            _authState.value = AuthState.Loading
            serviceClient.login(username, password)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val error = e.message ?: "Login failed"
            _authState.value = AuthState.Error(error)
            Result.failure(e)
        }
    }

    override suspend fun getOAuthUrl(providerId: String, returnUrl: String): Result<String> {
        return try {
            val response = serviceClient.sendRequest(
                Request.Auth.authorizationUrl(providerId, returnUrl),
            )

            if (response.isFailure) {
                return Result.failure(Exception("Failed to get OAuth URL"))
            }

            response.resultAs<OauthUrl>()?.let { oauthUrl ->
                Result.success(oauthUrl.url)
            } ?: Result.failure(Exception("Failed to parse OAuth URL"))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
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
            // Launch OAuth URL in Chrome Custom Tab
            // Token will be delivered via deep link callback to MainActivity
            handler.openOAuthUrl(oauthUrl)
            awaitingOAuthCallback = true
            Result.success(Unit)
        } catch (e: Exception) {
            awaitingOAuthCallback = false
            val error = e.message ?: "OAuth flow failed"
            _authState.value = AuthState.Error(error)
            Result.failure(e)
        }
    }

    fun handleOAuthCallback(token: String) {
        Logger.d("OAuth callback received")
        // Clear synchronously (before the launch) so the foreground collector,
        // which fires after this on the success path, sees no pending flow.
        awaitingOAuthCallback = false
        _isLoggingOut.value = false
        scope.launch {
            _authState.value = AuthState.Loading

            // Wait for transport + server/hello (authorize() silently no-ops without
            // a Connected session). The foreground/JIT recovery path elsewhere in the
            // pipeline will drive the reconnect — we just wait for it to land.
            val ready = withTimeoutOrNull(CONNECT_WAIT_MS) {
                serviceClient.sessionState.first {
                    it is SessionState.Connected && it.serverInfo != null
                }
            }
            if (ready == null) {
                Logger.e("OAuth: connection timeout — cannot authorize")
                _authState.value = AuthState.Error("Connection timeout. Please try again.")
                return@launch
            }
            try {
                serviceClient.authorize(token, isAutoLogin = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.e(e) { "Authorization failed" }
                _authState.value = AuthState.Error(e.message ?: "Authorization failed")
            }
        }
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
            val currentState = serviceClient.sessionState.value
            val serverIdentifier = settings.getServerIdentifier(currentState)
            if (serverIdentifier != null) {
                settings.setTokenForServer(serverIdentifier, null)
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

    private companion object {
        const val CONNECT_WAIT_MS = 10_000L
    }
}

class ServerIdMismatchException : Exception()

private fun SettingsRepository.getServerIdentifier(sessionState: SessionState): String? {
    return when (sessionState) {
        is SessionState.Connected -> getServerIdentifier(sessionState)
        else -> null
    }
}

private fun SettingsRepository.getServerIdentifier(sessionState: SessionState.Connected): String {
    return when (sessionState) {
        is SessionState.Connected.Direct -> this.getDirectServerIdentifier(
            sessionState.connectionInfo.host,
            sessionState.connectionInfo.port,
            sessionState.connectionInfo.isTls,
        )

        is SessionState.Connected.WebRTC -> this.getWebRTCServerIdentifier(sessionState.remoteId.rawId)
    }
}
