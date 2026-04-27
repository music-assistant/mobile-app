package io.music_assistant.client.ui.compose.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.auth.AuthenticationManager
import io.music_assistant.client.data.model.server.AuthProvider
import io.music_assistant.client.utils.AuthProcessState
import io.music_assistant.client.utils.DataConnectionState
import io.music_assistant.client.utils.SessionState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AuthenticationViewModel(
    private val authManager: AuthenticationManager,
    serviceClient: ServiceClient,
) : ViewModel() {
    private val _providers = MutableStateFlow<List<AuthProvider>>(emptyList())
    val providers: StateFlow<List<AuthProvider>> = _providers.asStateFlow()

    private var loadProvidersJob: Job? = null
    private var loadingForWebRTC: Boolean? = null

    val authState = authManager.authState
    val sessionState = serviceClient.sessionState

    val username = MutableStateFlow("")
    val password = MutableStateFlow("")

    init {
        // Trigger initial load if already connected and awaiting auth
        viewModelScope.launch {
            val currentState = sessionState.value
            if (currentState is SessionState.Connected) {
                val dataConnectionState = currentState.dataConnectionState
                if (dataConnectionState is DataConnectionState.AwaitingAuth) {
                    // Only load providers if we're not in a failed state
                    when (dataConnectionState.authProcessState) {
                        is AuthProcessState.Failed -> {
                            // Don't reload providers when auth failed
                        }

                        else -> {
                            loadProviders()
                        }
                    }
                }
            }
        }

        // Auto-fetch providers when connected and awaiting auth
        viewModelScope.launch {
            sessionState.collect { state ->
                Logger.d("AuthVM") { "SessionState changed: ${state::class.simpleName}" }
                when (state) {
                    is SessionState.Connected -> {
                        val dataConnectionState = state.dataConnectionState
                        Logger.d("AuthVM") { "DataConnectionState: ${dataConnectionState::class.simpleName}" }
                        if (dataConnectionState is DataConnectionState.AwaitingAuth) {
                            Logger.d("AuthVM") { "AwaitingAuth - checking auth process state" }
                            // Only load providers if we're not in a failed state
                            // (to avoid overriding error messages)
                            when (dataConnectionState.authProcessState) {
                                is AuthProcessState.Failed -> {
                                    Logger.d("AuthVM") { "Auth failed - not reloading providers" }
                                    // Don't reload providers when auth failed - keep the error visible
                                }

                                else -> {
                                    Logger.d("AuthVM") { "Calling loadProviders()" }
                                    loadProviders()
                                }
                            }
                        }
                    }

                    is SessionState.Disconnected -> {
                        // Clear providers when disconnected so next connection loads fresh
                        // This ensures switching between WebRTC (builtin only) and Direct (all providers) works correctly
                        Logger.d("AuthVM") { "Disconnected - clearing providers and cancelling pending load" }
                        loadProvidersJob?.cancel()
                        loadProvidersJob = null
                        loadingForWebRTC = null
                        _providers.update { emptyList() }
                    }

                    else -> {
                        // Connecting, Reconnecting - do nothing
                    }
                }
            }
        }
    }

    fun loadProviders() {
        Logger.d("AuthVM") { "loadProviders() called, current providers count: ${_providers.value.size}" }

        // Don't reload if we already have providers (to avoid overriding error states)
        if (_providers.value.isNotEmpty()) {
            return
        }

        val currentState = sessionState.value
        val isWebRTC = currentState is SessionState.Connected.WebRTC

        // If a job is running for the SAME connection type, skip (avoid redundant calls)
        if (loadProvidersJob?.isActive == true && loadingForWebRTC == isWebRTC) {
            Logger.d("AuthVM") { "Provider loading already in progress for same connection type, skipping" }
            return
        }

        // Cancel if connection type changed (WebRTC ↔ Direct) - old result would be wrong
        if (loadProvidersJob?.isActive == true && loadingForWebRTC != isWebRTC) {
            Logger.d("AuthVM") { "Connection type changed, cancelling previous load" }
            loadProvidersJob?.cancel()
            loadProvidersJob = null
        }

        loadingForWebRTC = isWebRTC

        if (isWebRTC) {
            // For WebRTC, skip API call - only builtin auth works (OAuth requires HTTP redirects)
            Logger.d("AuthVM") { "WebRTC connection - using builtin provider directly (skip API call)" }
            val builtinProvider = AuthProvider(
                id = "builtin",
                type = "builtin",
                requiresRedirect = false,
            )
            _providers.update { listOf(builtinProvider) }
            // Clear job reference since we're done (synchronous)
            loadProvidersJob = null
            loadingForWebRTC = null
            return
        }

        // For direct connections, fetch all providers from server
        Logger.d("AuthVM") { "Direct connection - fetching providers from server" }
        loadProvidersJob = viewModelScope.launch {
            try {
                authManager.getProviders()
                    .onSuccess { providerList ->
                        Logger.d("AuthVM") { "Received ${providerList.size} providers: ${providerList.map { it.id }}" }
                        _providers.update { providerList }
                    }
                    .onFailure { error ->
                        Logger.e("AuthVM", error) { "Failed to load providers" }
                    }
            } finally {
                // Clear job reference when done (success or failure)
                loadProvidersJob = null
                loadingForWebRTC = null
            }
        }
    }

    fun login(provider: AuthProvider) {
        viewModelScope.launch {
            when (provider.type) {
                "builtin" -> {
                    authManager.loginWithCredentials(
                        provider.id,
                        username.value,
                        password.value,
                    )
                }

                else -> {
                    // OAuth or other redirect-based auth
                    // Use custom URL scheme for reliable deep linking
                    val returnUrl = "musicassistant://auth/callback"
                    authManager.getOAuthUrl(provider.id, returnUrl)
                        .onSuccess { url -> authManager.startOAuthFlow(url) }
                }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            // AuthenticationManager handles both flag setting and token clearing
            authManager.logout()
        }
    }
}
