package io.music_assistant.client.wear.ui

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import io.music_assistant.client.wear.data.Artwork
import io.music_assistant.client.wear.data.ArtworkLoader
import io.music_assistant.client.wear.data.MaRepository
import io.music_assistant.client.wear.data.NowPlaying
import io.music_assistant.client.wear.data.QueueItemsState
import io.music_assistant.client.wear.data.SessionState
import io.music_assistant.client.wear.data.WearPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Everything the watch screens render, in one snapshot. */
data class WearUiState(
    val session: SessionState = SessionState.Idle,
    val players: List<WearPlayer> = emptyList(),
    val selected: WearPlayer? = null,
    val nowPlaying: NowPlaying? = null,
    val artwork: Artwork? = null,
    val queue: QueueItemsState = QueueItemsState(),
    /** The queue row playing now, which the queue screen marks. */
    val activeQueueItemId: String? = null,
) {
    /** Players the user can pick: reachable, not hidden by the server. */
    val selectablePlayers: List<WearPlayer> = players.filter { it.isSelectable }

    val isConnected: Boolean = session is SessionState.Connected
}

/**
 * Binds [MaRepository] to the Compose screens and keeps the cover art for the current track
 * loaded. Commands are forwarded unchanged; the repository owns the optimism.
 */
class WearPlayerViewModel(
    private val repository: MaRepository,
    private val artworkLoader: ArtworkLoader,
) : ViewModel() {
    private val artwork = MutableStateFlow<Artwork?>(null)

    val state: StateFlow<WearUiState> = combine(
        repository.sessionState,
        repository.players,
        repository.selectedPlayer,
        repository.nowPlaying,
        combine(artwork, repository.queueItems, repository.activeQueueItemId, ::Triple),
    ) { session, players, selected, nowPlaying, queueState ->
        val (artwork, queue, activeItemId) = queueState
        WearUiState(session, players, selected, nowPlaying, artwork, queue, activeItemId)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, WearUiState())

    init {
        viewModelScope.launch {
            repository.nowPlaying
                .map { it?.imageUrl }
                .distinctUntilChanged()
                .collect { url ->
                    // Clear first: keeping the previous cover behind a new track would theme
                    // the screen from the wrong album until the fetch lands.
                    artwork.value = null
                    artwork.value = url?.let { artworkLoader.load(it) }
                }
        }
    }

    fun togglePlayPause() = repository.togglePlayPause()

    fun next() = repository.next()

    fun previous() = repository.previous()

    fun setVolume(level: Int) = repository.setVolume(level)

    fun selectPlayer(playerId: String) = repository.selectPlayer(playerId)

    fun transferPlayback(playerId: String) = repository.transferPlayback(playerId)

    fun playQueueItem(itemId: String) = repository.playQueueItem(itemId)

    fun removeQueueItem(itemId: String) = repository.removeQueueItem(itemId)

    /** The cover for one queue row, at row size. Null when the server has none. */
    suspend fun queueArtwork(url: String): ImageBitmap? = artworkLoader.loadThumbnail(url)

    class Factory(
        private val repository: MaRepository,
        private val artworkLoader: ArtworkLoader,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
            WearPlayerViewModel(repository, artworkLoader) as T
    }
}
