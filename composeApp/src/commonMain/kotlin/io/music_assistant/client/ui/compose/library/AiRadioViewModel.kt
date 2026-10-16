package io.music_assistant.client.ui.compose.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import io.music_assistant.client.data.MainDataSource
import io.music_assistant.client.data.model.client.QueueOption
import io.music_assistant.client.data.model.server.ServerAiRadioStation
import io.music_assistant.client.data.play.PlayDispatcher
import io.music_assistant.client.data.play.PlayRequest
import io.music_assistant.client.data.repository.AiRadioChange
import io.music_assistant.client.data.repository.AiRadioRepository
import kotlinx.coroutines.Job
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
 * Server errors already reach the user through [io.music_assistant.client.api.ErrorMessageBus],
 * so this only tracks what the screen must render differently: loading and a failed load.
 *
 * The list loads on entry, then follows [AiRadioRepository.changes]. A show is a Radio item, so
 * it plays and stops like any radio; the screen holds no run state.
 */
class AiRadioViewModel(
    private val repository: AiRadioRepository,
    private val dataSource: MainDataSource,
    private val playDispatcher: PlayDispatcher,
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

    // One in-flight re-read: a burst of hints keeps only the latest.
    private var stationsRefresh: Job? = null

    init {
        viewModelScope.launch {
            repository.changes.collect { change ->
                when (change) {
                    AiRadioChange.STATIONS, AiRadioChange.RESYNC -> refreshStations()
                    AiRadioChange.HOSTS, AiRadioChange.QUEUE_DJ -> Unit
                }
            }
        }
    }

    fun load() {
        viewModelScope.launch {
            _state.value = State.Loading
            val stations = repository.stations()
                .onFailure { Logger.w(it) { "AI Radio: station list failed" } }
                .getOrNull()
            _state.value = stations?.let { State.Ready(it) } ?: State.Failed
            stations?.let { loadArtwork(it) }
        }
    }

    /**
     * Re-reads the list in place, without the Loading state. Only a shown list is refreshed:
     * a failed or pending load is [load]'s to finish. A failed re-read keeps the shown list.
     */
    private fun refreshStations() {
        stationsRefresh?.cancel()
        stationsRefresh = viewModelScope.launch {
            val stations = repository.stations()
                .onFailure { Logger.w(it) { "AI Radio: station list refresh failed" } }
                .getOrNull() ?: return@launch
            val current = _state.value as? State.Ready ?: return@launch
            if (current.stations == stations) return@launch
            // Old covers stay until the new lookups land, so known rows do not flash placeholders.
            _state.value = current.copy(stations = stations)
            loadArtwork(stations)
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
     * Plays [station] on the queue of the player the user has selected, replacing what it
     * plays. Read from [MainDataSource.selectedPlayer], NOT from `nowPlayingPlayer`: the latter
     * only considers players that already hold a queue item, so a freshly selected idle player
     * would silently resolve to whatever else was already playing.
     */
    fun play(station: ServerAiRadioStation) {
        val queueId = dataSource.selectedPlayer?.queueOrPlayerId ?: run {
            Logger.w { "AI Radio: no target player, ignoring play of ${station.id}" }
            return
        }
        playDispatcher.play(
            PlayRequest(
                queueOrPlayerId = queueId,
                media = listOf(station.uri),
                option = QueueOption.REPLACE,
            ),
        )
    }

    private companion object {
        const val SUBSCRIPTION_STOP_TIMEOUT_MS = 5000L
    }
}
