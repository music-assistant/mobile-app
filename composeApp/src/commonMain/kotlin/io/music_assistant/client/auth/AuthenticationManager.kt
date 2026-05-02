package io.music_assistant.client.auth

import co.touchlab.kermit.Logger
import io.music_assistant.client.api.Request
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.data.model.server.AuthProvider
import io.music_assistant.client.data.model.server.OauthUrl
import io.music_assistant.client.data.model.server.User
import io.music_assistant.client.settings.SettingsRepository
import io.music_assistant.client.utils.AuthProcessState
import io.music_assistant.client.utils.DataConnectionState
import io.music_assistant.client.utils.SessionState
import io.music_assistant.client.utils.mainDispatcher
import io.music_assistant.client.utils.resultAs
import kotlinx.coroutines.CoroutineScope
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
) {
    private val scope = CoroutineScope(SupervisorJob() + mainDispatcher)

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    // OAuthHandler will be set by platform (e.g., MainActivity on Android)
    var oauthHandler: OAuthHandler? = null

    // Flag to prevent auto-login during intentional logout - using StateFlow for proper synchronization
    private val _isLoggingOut = MutableStateFlow(false)
    private val isLoggingOut: Boolean
        get() = _isLoggingOut.value

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
                                        val serverIdentifier = when (state) {
                                            is SessionState.Connected.Direct ->
                                                settings.getDirectServerIdentifier(
                                                    state.connectionInfo.host,
                                                    state.connectionInfo.port,
                                                    state.connectionInfo.isTls,
                                                )
                                            is SessionState.Connected.WebRTC ->
                                                settings.getWebRTCServerIdentifier(state.remoteId.rawId)
                                        }
                                        val token = settings.getTokenForServer(serverIdentifier)
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
                                    log.i {
                                        "AwaitingAuth(Failed): " +
                                            dataConnectionState.authProcessState.reason
                                    }
                                    _authState.value =
                                        AuthState.Error(dataConnectionState.authProcessState.reason)
                                }

                                AuthProcessState.LoggedOut -> {
                                    log.i { "AwaitingAuth(LoggedOut)" }
                                    _authState.value = AuthState.Idle
                                }
                            }
                        }

                        DataConnectionState.Authenticated -> {
                            state.user?.let { user ->
                                log.i { "Authenticated" }
                                _authState.value = AuthState.Authenticated(user)
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
    }

    suspend fun getProviders(): Result<List<AuthProvider>> {
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
        } catch (e: Exception) {
            val error = e.message ?: "Exception fetching providers"
            _authState.value = AuthState.Error(error)
            Result.failure(e)
        }
    }

    @Suppress("UnusedParameter") // providerId reserved — current server login API doesn't yet route per-provider
    suspend fun loginWithCredentials(
        providerId: String,
        username: String,
        password: String,
    ): Result<Unit> {
        return try {
            _isLoggingOut.value = false  // Reset flag when user explicitly logs in
            _authState.value = AuthState.Loading
            serviceClient.login(username, password)
            Result.success(Unit)
        } catch (e: Exception) {
            val error = e.message ?: "Login failed"
            _authState.value = AuthState.Error(error)
            Result.failure(e)
        }
    }

    suspend fun getOAuthUrl(providerId: String, returnUrl: String): Result<String> {
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
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun startOAuthFlow(oauthUrl: String): Result<Unit> {
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
            Result.success(Unit)
        } catch (e: Exception) {
            val error = e.message ?: "OAuth flow failed"
            _authState.value = AuthState.Error(error)
            Result.failure(e)
        }
    }

    fun handleOAuthCallback(token: String) {
        Logger.d("OAuth callback received")
        _isLoggingOut.value = false  // Reset flag when user explicitly logs in with OAuth
        scope.launch {
            _authState.value = AuthState.Loading

            // Wait for connection to be established if app was backgrounded
            // Try for up to 10 seconds
            var attempts = 0
            while (attempts < CONNECT_POLL_MAX_ATTEMPTS) {
                val currentState = serviceClient.sessionState.value

                if (currentState is SessionState.Connected &&
                    currentState.serverInfo != null
                ) {
                    // Connection is fully established
                    try {
                        serviceClient.authorize(token, isAutoLogin = false)
                        // Auth state will be updated via sessionState flow
                        return@launch
                    } catch (e: Exception) {
                        val error = e.message ?: "Authorization failed"
                        Logger.e(e) { "Authorization failed" }
                        _authState.value = AuthState.Error(error)
                        return@launch
                    }
                }

                Logger.d("Waiting for connection... attempt ${attempts + 1}")
                delay(CONNECT_POLL_INTERVAL_MS)
                attempts++
            }

            // Timeout - connection not established
            Logger.e("Connection timeout - cannot authorize")
            _authState.value = AuthState.Error("Connection timeout. Please try again.")
        }
    }

    private suspend fun authorizeWithSavedToken(token: String) {
        try {
            serviceClient.authorize(token, isAutoLogin = true)
        } catch (_: Exception) {
            // Silent failure - user will see auth UI
        }
    }

    suspend fun logout(): Result<Unit> {
        return try {
            // Set flag FIRST, before any async operations
            _isLoggingOut.value = true
            val currentState = serviceClient.sessionState.value
            if (currentState is SessionState.Connected) {
                val serverIdentifier = when (currentState) {
                    is SessionState.Connected.Direct ->
                        settings.getDirectServerIdentifier(
                            currentState.connectionInfo.host,
                            currentState.connectionInfo.port,
                            currentState.connectionInfo.isTls,
                        )
                    is SessionState.Connected.WebRTC ->
                        settings.getWebRTCServerIdentifier(currentState.remoteId.rawId)
                }
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
        // Auto-login waits up to 10s for the connection to fully establish: 40 * 250ms.
        const val CONNECT_POLL_MAX_ATTEMPTS = 40
        const val CONNECT_POLL_INTERVAL_MS = 250L
    }
}
