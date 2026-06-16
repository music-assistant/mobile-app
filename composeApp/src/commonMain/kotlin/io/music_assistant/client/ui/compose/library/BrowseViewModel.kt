package io.music_assistant.client.ui.compose.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import io.music_assistant.client.api.Request
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.data.MainDataSource
import io.music_assistant.client.data.model.client.QueueOption
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.client.items.Genre
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
    private val apiClient: ServiceClient,
    private val mainDataSource: MainDataSource,
    private val mediaItemRepository: MediaItemRepository,
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

    // Mirrors ItemListViewModel.onPlayClick — play a browsed item on the selected player.
    fun onPlayClick(
        item: AppMediaItem,
        option: QueueOption,
        radio: Boolean,
    ) {
        viewModelScope.launch {
            val queueId = mainDataSource.selectedPlayer?.queueOrPlayerId ?: return@launch
            item.mediaUri?.let { mediaUri ->
                apiClient.sendRequest(
                    Request.Library.play(
                        media = listOf(mediaUri),
                        queueOrPlayerId = queueId,
                        option = option,
                        radioMode = radio && item !is Genre,
                    ),
                )
            }
        }
    }
}
