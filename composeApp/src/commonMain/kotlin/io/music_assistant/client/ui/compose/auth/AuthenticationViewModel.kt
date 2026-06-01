package io.music_assistant.client.ui.compose.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import io.music_assistant.client.auth.AuthCoordinator
import io.music_assistant.client.data.model.server.AuthProvider
import io.music_assistant.client.utils.AuthProcessState
import io.music_assistant.client.utils.DataConnectionState
import io.music_assistant.client.utils.SessionState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Drives the authentication UI. */
@OptIn(ExperimentalCoroutinesApi::class)
class AuthenticationViewModel(
    private val auth: AuthCoordinator,
    sessionStateFlow: StateFlow<SessionState>,
) : ViewModel() {
    val authState = auth.authState

    val username = MutableStateFlow("")
    val password = MutableStateFlow("")

    // Capacity = 1 so the UI's Retry tap always lands even if the upstream
    // is mid-emission. We don't replay — late subscribers don't get stale retries.
    private val retryTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    val providers: StateFlow<List<AuthProvider>> = merge(
        sessionStateFlow.map(::autoSource).filterNotNull().distinctUntilChanged(),
        retryTrigger.mapNotNull { retrySource(sessionStateFlow.value) },
    )
        .flatMapLatest(::loadFlow)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Forces a fresh providers load. Used by the Retry button after a failed initial fetch. */
    fun loadProviders() {
        retryTrigger.tryEmit(Unit)
    }

    fun login(provider: AuthProvider) {
        viewModelScope.launch {
            when (provider.type) {
                "builtin" -> auth.loginWithCredentials(provider.id, username.value, password.value)
                else -> auth.getOAuthUrl(provider.id, OAUTH_RETURN_URL)
                    .onSuccess { url -> auth.startOAuthFlow(url) }
            }
        }
    }

    fun logout() {
        viewModelScope.launch { auth.logout() }
    }

    /**
     * Projection for the auto path. Returns null to mean "keep the previous
     * emission" — used for transitional states (Connecting/Reconnecting) and
     * for AwaitingAuth(Failed) where we want to preserve the displayed error
     * rather than blow the provider list away.
     */
    private fun autoSource(state: SessionState): ProvidersSource? = when (state) {
        is SessionState.Disconnected -> ProvidersSource.Empty
        is SessionState.Connected -> when (val dcs = state.dataConnectionState) {
            !is DataConnectionState.AwaitingAuth -> null
            else -> when {
                dcs.authProcessState is AuthProcessState.Failed -> null
                else -> ProvidersSource.FromApi(state.connectionKey())
            }
        }
        else -> null
    }

    /**
     * Projection for the retry path. Identical to [autoSource] except it
     * bypasses the Failed-auth guard — Retry is an explicit user signal that
     * we should try again even when the auto-path would have suppressed.
     */
    private fun retrySource(state: SessionState): ProvidersSource? = when (state) {
        is SessionState.Connected -> ProvidersSource.FromApi(state.connectionKey())
        else -> null
    }

    private fun SessionState.Connected.connectionKey(): String = when (this) {
        is SessionState.Connected.Direct ->
            "direct:${connectionInfo.host}:${connectionInfo.port}:${connectionInfo.isTls}"
        is SessionState.Connected.WebRTC -> "webrtc:${remoteId.rawId}"
    }

    private fun loadFlow(source: ProvidersSource) = when (source) {
        ProvidersSource.Empty -> flowOf(emptyList())
        is ProvidersSource.FromApi -> flow {
            auth.getProviders()
                .onSuccess { emit(it) }
                .onFailure { log.e(it) { "Failed to load providers" } }
        }
    }

    private sealed interface ProvidersSource {
        data object Empty : ProvidersSource
        data class FromApi(val connectionKey: String) : ProvidersSource
    }

    private companion object {
        private val log = Logger.withTag("AuthVM")
        private const val OAUTH_RETURN_URL = "musicassistant://auth/callback"
    }
}
