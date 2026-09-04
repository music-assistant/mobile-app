package io.music_assistant.client.ui.compose.item

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.music_assistant.client.api.Request
import io.music_assistant.client.data.model.client.MediaType
import io.music_assistant.client.data.model.client.SortConfig
import io.music_assistant.client.data.model.client.SortOption
import io.music_assistant.client.data.model.client.clientSorted
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.server.ServerMediaItem
import io.music_assistant.client.data.repository.MediaItemDataMediator
import io.music_assistant.client.data.repository.MediaItemRepository
import io.music_assistant.client.ui.compose.common.DataState
import io.music_assistant.client.ui.compose.common.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

class ItemListViewModel(
    private val itemList: ItemList,
    mediaItemRepository: MediaItemRepository,
) : ViewModel() {
    private val items = MediaItemDataMediator(DataState.Loading(), mediaItemRepository)
        .updateOn(viewModelScope)
    private var sortOption = MutableStateFlow(SortConfig.defaultFor(itemList.mediaType))
    val state = items.asFlow()
        .combine(sortOption) { items, sortOption ->
            State(items = items.map { it.clientSorted(sortOption) }, sortOption = sortOption)
        }.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            State(items = DataState.Loading(), sortOption = sortOption.value),
        )

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

            items.set(request)
        }
    }

    fun sort(sortOption: SortOption) {
        this.sortOption.value = sortOption
    }

    data class State(val items: DataState<List<AppMediaItem>>, val sortOption: SortOption)
}

@Serializable
sealed interface ItemList {
    val mediaType: MediaType

    @Serializable
    data class ArtistAlbums(val providerInstance: String, val artistId: String) : ItemList {
        override val mediaType: MediaType = MediaType.ALBUM
    }

    @Serializable
    data class ArtistTopTracks(val providerInstance: String, val artistId: String) : ItemList {
        override val mediaType: MediaType = MediaType.TRACK
    }

    @Serializable
    data class ArtistLibrary(val artistId: String) : ItemList {
        override val mediaType: MediaType = MediaType.ALBUM
    }
}
