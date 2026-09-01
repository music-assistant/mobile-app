package io.music_assistant.client.ui.compose.item.artist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import io.music_assistant.client.api.Request
import io.music_assistant.client.data.model.client.items.Album
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.client.items.Artist
import io.music_assistant.client.data.model.client.items.Track
import io.music_assistant.client.data.model.server.ProviderMapping
import io.music_assistant.client.data.repository.MediaItemRepository
import io.music_assistant.client.data.repository.withItemUpdates
import io.music_assistant.client.ui.compose.common.DataState
import io.music_assistant.client.ui.compose.common.getOrEmptyList
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
    private val mediaItemRepository: MediaItemRepository,
) : ViewModel() {
    private val fetchArtistItemsUseCase = FetchArtistItemsUseCase(mediaItemRepository)

    private val libraryItems = MutableStateFlow<DataState<List<AppMediaItem>>>(DataState.Loading())
    val library = libraryItems
        .withItemUpdates(mediaItemRepository, viewModelScope)
        .mapData {
            Section(
                items = it
                    .filterIsInstance<Album>()
                    .take(ARTIST_SECTION_LIMIT),
                itemList = ItemList.ArtistLibrary(artist.itemId),
            )
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, DataState.Loading())

    private val allItems = MutableStateFlow<DataState<List<AppMediaItem>>>(DataState.Loading())
    private val allProviderFilter = MutableStateFlow<Section.ProviderFilter?>(null)
    val all = allItems
        .withItemUpdates(mediaItemRepository, viewModelScope)
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

    private val topTrackItems = MutableStateFlow<DataState<List<AppMediaItem>>>(DataState.Loading())
    private val topTracksProviderInfo = MutableStateFlow<Section.ProviderFilter?>(null)
    val topTracks = topTrackItems
        .withItemUpdates(mediaItemRepository, viewModelScope)
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
            try {
                if (artist.isInLibrary) {
                    val result = mediaItemRepository.fetchMediaItems(
                        Request.Artist.getAlbums(artist.itemId, artist.provider),
                    )

                    libraryItems.value = DataState.Data(result.getOrEmptyList())
                } else {
                    libraryItems.value = DataState.NoData()
                }
            } catch (e: Exception) {
                Logger.e("Failed to load artist album sections", e)
                libraryItems.value = DataState.Error()
            }
        }

        viewModelScope.launch {
            val itemsWithMappings = fetchArtistItemsUseCase.run(
                artist,
                Request.Artist::getAlbums,
            )

            if (itemsWithMappings != null) {
                allItems.value = DataState.Data(itemsWithMappings.items)
                allProviderFilter.value = Section.ProviderFilter(
                    itemsWithMappings.mapping,
                    artist.providerMappings ?: emptyList(),
                )
            } else {
                allItems.value = DataState.Error()
            }
        }

        viewModelScope.launch {
            val itemsWithMappings = fetchArtistItemsUseCase.run(
                artist,
                Request.Artist::getTopTracks,
            )

            if (itemsWithMappings != null) {
                topTrackItems.value = DataState.Data(itemsWithMappings.items)
                topTracksProviderInfo.value = Section.ProviderFilter(
                    itemsWithMappings.mapping,
                    artist.providerMappings ?: emptyList(),
                )
            } else {
                topTrackItems.value = DataState.Error()
            }
        }
    }

    fun loadAlbumsForProvider(mapping: ProviderMapping) {
        allItems.value = DataState.Loading()
        allProviderFilter.value = Section.ProviderFilter(
            mapping,
            artist.providerMappings ?: emptyList(),
        )

        viewModelScope.launch {
            val itemId = mapping.itemId
            val providerInstance = mapping.providerInstance

            val albums = mediaItemRepository.fetchMediaItems(
                Request.Artist.getAlbums(itemId, providerInstance),
            ).getOrEmptyList()

            allItems.value = DataState.Data(albums)
        }
    }

    fun loadTopTracksForProvider(mapping: ProviderMapping) {
        topTrackItems.value = DataState.Loading()
        topTracksProviderInfo.value = Section.ProviderFilter(
            mapping,
            artist.providerMappings ?: emptyList(),
        )

        viewModelScope.launch {
            val itemId = mapping.itemId
            val providerInstance = mapping.providerInstance

            val tracks = mediaItemRepository.fetchMediaItems(
                Request.Artist.getTopTracks(itemId, providerInstance),
            ).getOrEmptyList()

            topTrackItems.value = DataState.Data(tracks)
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
