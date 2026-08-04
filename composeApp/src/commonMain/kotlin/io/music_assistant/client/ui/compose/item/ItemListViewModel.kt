package io.music_assistant.client.ui.compose.item

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.music_assistant.client.api.Request
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.server.ServerMediaItem
import io.music_assistant.client.data.repository.MediaItemRepository
import io.music_assistant.client.ui.compose.common.DataState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

class ItemListViewModel(
    private val itemList: ItemList,
    private val mediaItemRepository: MediaItemRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val request = when (itemList) {
                is ItemList.ArtistAlbums -> Request.Artist.getAlbums(
                    itemList.artistId,
                    itemList.providerInstance,
                )

                is ItemList.ArtistTopTracks -> Request.Artist.getTopTracks(
                    itemList.artistId,
                    itemList.providerInstance,
                )

                is ItemList.ArtistLibrary -> Request.Artist.getAlbums(
                    itemList.artistId,
                    ServerMediaItem.LIBRARY_PROVIDER,
                )
            }

            mediaItemRepository.fetchMediaItems(request) { items ->
                _state.update {
                    it.copy(
                        items = DataState.Data(items),
                    )
                }
            }
        }
    }

    data class State(val items: DataState<List<AppMediaItem>> = DataState.Loading())
}

@Serializable
sealed interface ItemList {
    @Serializable
    data class ArtistAlbums(val providerInstance: String, val artistId: String) : ItemList

    @Serializable
    data class ArtistTopTracks(val providerInstance: String, val artistId: String) : ItemList

    @Serializable
    data class ArtistLibrary(val artistId: String) : ItemList
}
