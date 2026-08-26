package io.music_assistant.client.ui.compose.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import io.music_assistant.client.api.Request
import io.music_assistant.client.data.MainDataSource
import io.music_assistant.client.data.model.client.QueueOption
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.client.items.Genre
import io.music_assistant.client.data.model.client.items.Track
import io.music_assistant.client.data.repository.MediaItemRepository
import io.music_assistant.client.ui.compose.common.DataState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Backs one level of the folder-style Browse screen. [path] is the server browse path for this
 * level (null = provider root); a folder's [AppMediaItem.uri] becomes the next level's path.
 * The server returns the full level in one shot, so there's no pagination/sort/favorites here.
 */
class BrowseViewModel(
    private val path: String?,
    private val mainDataSource: MainDataSource,
    private val mediaItemRepository: MediaItemRepository,
    private val playbackCoordinator: BrowsePlaybackCoordinator,
) : ViewModel() {
    private val _state = MutableStateFlow<DataState<List<AppMediaItem>>>(DataState.Loading())
    val state = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { DataState.Loading() }
            val result = mediaItemRepository.fetchMediaItems(Request.Browse.atPath(path))
            result.getOrNull()
                ?.let { items -> _state.update { DataState.Data(items) } }
                ?: run {
                    Logger.e("Error browsing path=$path:", result.exceptionOrNull())
                    _state.update { DataState.Error() }
                }
        }
    }

    fun onPlayClick(
        item: AppMediaItem,
        option: QueueOption,
        radio: Boolean,
    ) {
        viewModelScope.launch {
            val selectedPlayer = mainDataSource.selectedPlayer ?: return@launch
            val queueOrPlayerId = selectedPlayer.queueOrPlayerId

            val mediaUri = item.mediaUri ?: return@launch

            // For a Track tapped inside Browse, play from that track
            // through the rest of the current folder.
            val playbackUris =
                if (item is Track) {
                    val folderItems =
                        mediaItemRepository.fetchMediaItems(
                            Request.Browse.atPath(path),
                        ).getOrNull().orEmpty()

                    val tracks =
                        folderItems
                            .filterIsInstance<Track>()
                            .filterNot { track ->
                                val name = track.displayName.trim()
                                name.equals("(Empty)", ignoreCase = true) ||
                                    name.equals("Empty", ignoreCase = true)
                            }
                            .sortedBy {
                                it.displayName.lowercase()
                            }
                            .mapNotNull { it.mediaUri }
                            .distinct()

                    val tappedIndex =
                        tracks.indexOf(mediaUri)

                    if (tappedIndex >= 0) {
                        tracks.drop(tappedIndex)
                    } else {
                        listOf(mediaUri)
                    }
                } else {
                    listOf(mediaUri)
                }

            if (playbackUris.isEmpty()) return@launch

            Logger.withTag("PlayDispatch").i {
                    "BrowseViewModel: start=$mediaUri " +
                    "tracks=${playbackUris.size} " +
                    "option=$option queue=$queueOrPlayerId"
            }

            playbackCoordinator.play(
                currentPath = path,
                initialUris = playbackUris,
                queueOrPlayerId = queueOrPlayerId,
                queueId = selectedPlayer.queueInfo?.id,
                option = option,
                radioMode = radio && item !is Genre,
                continueIntoFollowingFolders = item is Track && !radio,
            )
        }
    }
}
