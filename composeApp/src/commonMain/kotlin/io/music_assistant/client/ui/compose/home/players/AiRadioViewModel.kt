package io.music_assistant.client.ui.compose.home.players

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import io.music_assistant.client.data.model.server.ServerAiRadioSession
import io.music_assistant.client.data.model.server.ServerAiRadioStation
import io.music_assistant.client.data.repository.AiRadioRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Backs the AI Radio station picker.
 *
 * Server errors (no permission, run slot taken, station already running) already reach the
 * user through [io.music_assistant.client.api.ErrorMessageBus], so this only tracks what the
 * sheet must render differently: loading, a failed load, and which station is running.
 */
class AiRadioViewModel(
    private val repository: AiRadioRepository,
) : ViewModel() {
    /**
     * A failed load and an empty station list mean opposite things to the user — "try
     * again" versus "go author one" — so they are distinct states rather than one empty list.
     */
    sealed interface State {
        data object Loading : State
        data object Failed : State
        data class Ready(
            val stations: List<ServerAiRadioStation>,
            val running: ServerAiRadioSession?,
        ) : State
    }

    private val _state = MutableStateFlow<State>(State.Loading)
    val state: StateFlow<State> = _state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _state.value = State.Loading
            val stations = repository.stations()
                .onFailure { Logger.w(it) { "AI Radio: station list failed" } }
                .getOrNull()
            _state.value = if (stations == null) {
                State.Failed
            } else {
                State.Ready(stations, runningSessionOrNull())
            }
        }
    }

    fun start(stationId: String, playerId: String, onStarted: () -> Unit) {
        viewModelScope.launch {
            repository.start(stationId, playerId)
                .onSuccess { onStarted() }
                .onFailure { Logger.w(it) { "AI Radio: start failed for $stationId" } }
            refreshRunning()
        }
    }

    fun stop(sessionId: String) {
        viewModelScope.launch {
            repository.stop(sessionId)
                .onFailure { Logger.w(it) { "AI Radio: stop failed for $sessionId" } }
            refreshRunning()
        }
    }

    /**
     * Re-reads the run state after a start or stop. The provider emits no events, so this
     * poll is the only way the sheet learns what changed.
     */
    private fun refreshRunning() {
        viewModelScope.launch {
            val running = runningSessionOrNull()
            _state.update { current ->
                (current as? State.Ready)?.copy(running = running) ?: current
            }
        }
    }

    private suspend fun runningSessionOrNull(): ServerAiRadioSession? =
        repository.runningSession()
            .onFailure { Logger.w(it) { "AI Radio: status read failed" } }
            .getOrNull()
}
