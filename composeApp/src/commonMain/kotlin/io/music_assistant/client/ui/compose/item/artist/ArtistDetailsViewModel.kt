package io.music_assistant.client.ui.compose.item.artist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.music_assistant.client.api.Request
import io.music_assistant.client.data.model.client.items.Album
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.client.items.Artist
import io.music_assistant.client.data.model.client.items.Track
import io.music_assistant.client.data.model.client.items.itemList
import io.music_assistant.client.data.model.server.ProviderMapping
import io.music_assistant.client.data.repository.MediaItemRepository
import io.music_assistant.client.ui.compose.common.DataState
import io.music_assistant.client.ui.compose.common.map
import io.music_assistant.client.ui.compose.common.mapData
import io.music_assistant.client.ui.compose.item.FetchArtistItemsUseCase
import io.music_assistant.client.ui.compose.item.ItemDetailsViewModel.Companion.ARTIST_SECTION_LIMIT
import io.music_assistant.client.ui.compose.item.ItemList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ArtistDetailsViewModel(
    private val artist: Artist,
    mediaItemRepository: MediaItemRepository,
) : ViewModel() {
    private val fetchArtistItemsUseCase = FetchArtistItemsUseCase(mediaItemRepository)

    private val libraryItems = itemList(mediaItemRepository)
    val library = libraryItems.asFlow()
        .mapData {
            Section(
                items = it
                    .filterIsInstance<Album>()
                    .take(ARTIST_SECTION_LIMIT),
                itemList = ItemList.ArtistLibrary(artist.itemId),
            )
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, DataState.Loading())

    private val allItems = itemList(mediaItemRepository)
    private val allProviderFilter = MutableStateFlow<Section.ProviderFilter?>(null)
    val all = allItems.asFlow()
        .combine(allProviderFilter) { items, providerFilter ->
            if (providerFilter != null) {
                items.map {
                    Section(
                        items = it
                            .filterIsInstance<Album>()
                            .take(ARTIST_SECTION_LIMIT),
                        itemList = ItemList.ArtistAlbums(
                            providerFilter.current.providerInstance,
                            providerFilter.current.itemId,
                        ),
                        providerFilter = providerFilter,
                    )
                }
            } else {
                DataState.Loading<Section<Album>>()
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, DataState.Loading())

    private val topTrackItems = itemList(mediaItemRepository)
    private val topTracksProviderInfo = MutableStateFlow<Section.ProviderFilter?>(null)
    val topTracks = topTrackItems.asFlow()
        .combine(topTracksProviderInfo) { items, providerFilter ->
            if (providerFilter != null) {
                items.map {
                    Section(
                        items = it
                            .filterIsInstance<Track>()
                            .take(ARTIST_SECTION_LIMIT),
                        itemList = ItemList.ArtistTopTracks(
                            providerFilter.current.providerInstance,
                            providerFilter.current.itemId,
                        ),
                        providerFilter = providerFilter,
                    )
                }
            } else {
                DataState.Loading<Section<Track>>()
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, DataState.Loading())

    init {
        loadSections(artist)
    }

    private fun loadSections(artist: Artist) {
        viewModelScope.launch {
            if (artist.isInLibrary) {
                libraryItems.set(Request.Artist.getAlbums(artist.itemId, artist.provider))
            } else {
                libraryItems.setEmpty()
            }
        }

        viewModelScope.launch {
            val itemsWithMappings = fetchArtistItemsUseCase.run(
                artist,
                Request.Artist::getAlbums,
            )

            if (itemsWithMappings != null) {
                allItems.set(itemsWithMappings.items, itemsWithMappings.request)
                allProviderFilter.value = Section.ProviderFilter(
                    itemsWithMappings.mapping,
                    artist.providerMappings ?: emptyList(),
                )
            } else {
                allItems.setError()
            }
        }

        viewModelScope.launch {
            val itemsWithMappings = fetchArtistItemsUseCase.run(
                artist,
                Request.Artist::getTopTracks,
            )

            if (itemsWithMappings != null) {
                topTrackItems.set(itemsWithMappings.items, itemsWithMappings.request)
                topTracksProviderInfo.value = Section.ProviderFilter(
                    itemsWithMappings.mapping,
                    artist.providerMappings ?: emptyList(),
                )
            } else {
                topTrackItems.setError()
            }
        }
    }

    fun loadAlbumsForProvider(mapping: ProviderMapping) {
        allProviderFilter.value = Section.ProviderFilter(
            mapping,
            artist.providerMappings ?: emptyList(),
        )

        viewModelScope.launch {
            val itemId = mapping.itemId
            val providerInstance = mapping.providerInstance
            allItems.set(Request.Artist.getAlbums(itemId, providerInstance))
        }
    }

    fun loadTopTracksForProvider(mapping: ProviderMapping) {
        topTracksProviderInfo.value = Section.ProviderFilter(
            mapping,
            artist.providerMappings ?: emptyList(),
        )

        viewModelScope.launch {
            val itemId = mapping.itemId
            val providerInstance = mapping.providerInstance
            topTrackItems.set(Request.Artist.getTopTracks(itemId, providerInstance))
        }
    }

    data class Section<T : AppMediaItem>(
        val items: List<T>,
        val itemList: ItemList,
        val providerFilter: ProviderFilter? = null,
    ) {
        data class ProviderFilter(
            val current: ProviderMapping,
            val options: List<ProviderMapping>,
        )
    }
}
