package io.music_assistant.client.ui.compose.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import io.music_assistant.client.data.MainDataSource
import io.music_assistant.client.data.model.server.ServerAiRadioSession
import io.music_assistant.client.data.model.server.ServerAiRadioStation
import io.music_assistant.client.data.repository.AiRadioRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Backs the AI Radio station list.
 *
 * Server errors (no permission, run slot taken, station already running) already reach the
 * user through [io.music_assistant.client.api.ErrorMessageBus], so this only tracks what the
 * screen must render differently: loading, a failed load, and which station is on air.
 *
 * The provider emits no events, so the run state is polled: once on entry, then again after
 * each start and stop. That is the only way the Stop control learns it should appear.
 */
class AiRadioViewModel(
    private val repository: AiRadioRepository,
    private val dataSource: MainDataSource,
) : ViewModel() {
    /**
     * A failed load and an empty station list mean opposite things to the user — "try
     * again" versus "go author one" — so they are distinct states rather than one empty list.
     */
    sealed interface State {
        data object Loading : State
        data object Failed : State

        /**
         * @param artwork station id → source-playlist cover URL. Starts empty and fills in, so
         *   the list paints before the per-station lookups finish; a station absent from it
         *   draws its placeholder.
         */
        data class Ready(
            val stations: List<ServerAiRadioStation>,
            val running: ServerAiRadioSession?,
            val artwork: Map<String, String> = emptyMap(),
        ) : State
    }

    private val _state = MutableStateFlow<State>(State.Loading)
    val state: StateFlow<State> = _state.asStateFlow()

    /**
     * Whether there is a player to start a station on. Rendered as a message rather than left
     * to fail on tap, because the server's own error would say nothing the user can act on.
     *
     * Keyed on the selection index rather than on a player object: the resolver only yields
     * null when no player is visible at all.
     */
    val hasTargetPlayer: StateFlow<Boolean> = dataSource.selectedPlayerIndex
        .map { it != null }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(SUBSCRIPTION_STOP_TIMEOUT_MS),
            dataSource.selectedPlayerIndex.value != null,
        )

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
            stations?.let { loadArtwork(it) }
        }
    }

    /**
     * Fills in the row covers after the list is already on screen. Deliberately not awaited by
     * [load]: it is one round trip per station, and a station name is useful long before its
     * picture is.
     */
    private fun loadArtwork(stations: List<ServerAiRadioStation>) {
        viewModelScope.launch {
            val artwork = repository.artworkUrls(stations)
            if (artwork.isEmpty()) return@launch
            _state.update { current ->
                (current as? State.Ready)
                    ?.takeIf { it.stations == stations }
                    ?.copy(artwork = artwork)
                    ?: current
            }
        }
    }

    /**
     * Starts [stationId] on the player the user has selected. The server wants a player id,
     * not a queue id.
     *
     * Read from [MainDataSource.selectedPlayer], NOT from `nowPlayingPlayer`: the latter is the
     * media session's presented player, which only considers players that already hold a queue
     * item. A freshly selected idle player — the usual case when starting a station — is not
     * among them, so it would silently resolve to whatever else was already playing. And an
     * empty `player_id_override` is not an error server-side: `start_run` falls back to the
     * station's own `default_player_id`, so a wrong id here plays somewhere unexpected instead
     * of failing loudly.
     */
    fun start(stationId: String) {
        val playerId = dataSource.selectedPlayer?.playerId ?: run {
            Logger.w { "AI Radio: no target player, ignoring start of $stationId" }
            return
        }
        viewModelScope.launch {
            repository.start(stationId, playerId)
                .onFailure { Logger.w(it) { "AI Radio: start failed for $stationId" } }
            refreshRunning()
        }
    }

    /**
     * Stops whatever [stationId] has on air. Keyed on the station rather than on the session id
     * from the last poll, which may name a run that has since ended by itself.
     */
    fun stop(stationId: String) {
        viewModelScope.launch {
            repository.stop(stationId)
                .onFailure { Logger.w(it) { "AI Radio: stop failed for $stationId" } }
            refreshRunning()
        }
    }

    /**
     * Re-reads the run state after a start or stop. The provider emits no events, so this
     * poll is the only way the screen learns what changed.
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

    private companion object {
        const val SUBSCRIPTION_STOP_TIMEOUT_MS = 5000L
    }
}
