package io.music_assistant.client.ui.compose.item.artist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.music_assistant.client.api.Request
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.data.model.client.items.Album
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.client.items.Artist
import io.music_assistant.client.data.model.client.items.FetchArtistItemsUseCase
import io.music_assistant.client.data.model.client.items.Track
import io.music_assistant.client.data.model.client.items.itemList
import io.music_assistant.client.data.model.client.items.set
import io.music_assistant.client.data.model.server.ServerProviderInstance
import io.music_assistant.client.data.repository.MediaItemRepository
import io.music_assistant.client.ui.compose.common.DataState
import io.music_assistant.client.ui.compose.common.map
import io.music_assistant.client.ui.compose.common.mapData
import io.music_assistant.client.ui.compose.item.ItemDetailsViewModel.Companion.ARTIST_SECTION_LIMIT
import io.music_assistant.client.ui.compose.item.ItemList
import io.music_assistant.client.ui.compose.item.toRequests
import io.music_assistant.client.utils.resultAs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ArtistDetailsViewModel(
    private val artist: Artist,
    mediaItemRepository: MediaItemRepository,
    private val serviceClient: ServiceClient,
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
            items.map {
                Section(
                    items = it
                        .filterIsInstance<Album>()
                        .take(ARTIST_SECTION_LIMIT),
                    itemList = providerFilter?.current,
                    providerFilter = providerFilter,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, DataState.Loading())

    private val topTrackItems = itemList(mediaItemRepository)
    private val topTracksFilter = MutableStateFlow<Section.ProviderFilter?>(null)
    val topTracks = topTrackItems.asFlow()
        .combine(topTracksFilter) { items, providerFilter ->
            items.map {
                Section(
                    items = it
                        .filterIsInstance<Track>()
                        .take(ARTIST_SECTION_LIMIT),
                    itemList = providerFilter?.current,
                    providerFilter = providerFilter,
                )
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
            val artistItems = fetchArtistItemsUseCase.run(artist.providerGroups().map { ItemList.ArtistAlbums(it) })

            if (artistItems != null) {
                val (items, itemList, options) = artistItems
                allProviderFilter.value = Section.ProviderFilter(itemList, options)
                allItems.set(items, itemList.toRequests())
            } else {
                allItems.setEmpty()
            }
        }

        viewModelScope.launch {
            val providers = if (artist.isInLibrary) {
                serviceClient.sendRequest(Request.Provider.all())
                    .resultAs<List<ServerProviderInstance>>()
                    .orEmpty()
            } else {
                emptyList()
            }
            val result = fetchArtistItemsUseCase.run(artist.topTracksOptions(providers))

            if (result != null) {
                val (items, itemList, options) = result
                topTracksFilter.value = Section.ProviderFilter(itemList, options)
                topTrackItems.set(items, itemList.toRequests())
            } else {
                topTrackItems.setEmpty()
            }
        }
    }

    fun loadAll(itemList: ItemList) {
        allProviderFilter.update {
            it?.copy(current = itemList)
        }

        viewModelScope.launch {
            allItems.set(itemList.toRequests())
        }
    }

    fun loadTopTracks(itemList: ItemList) {
        topTracksFilter.update { it?.copy(current = itemList) }

        viewModelScope.launch {
            topTrackItems.set(itemList.toRequests())
        }
    }

    data class Section<T : AppMediaItem>(
        val items: List<T>,
        val itemList: ItemList?,
        val providerFilter: ProviderFilter? = null,
    ) {
        data class ProviderFilter(
            val current: ItemList,
            val options: List<ItemList>,
        )
    }
}
