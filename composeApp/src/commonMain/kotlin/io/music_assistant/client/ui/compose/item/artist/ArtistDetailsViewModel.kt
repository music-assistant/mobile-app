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
import io.music_assistant.client.data.repository.updateItems
import io.music_assistant.client.ui.compose.common.DataState
import io.music_assistant.client.ui.compose.common.getOrEmptyList
import io.music_assistant.client.ui.compose.item.FetchArtistItemsUseCase
import io.music_assistant.client.ui.compose.item.ItemDetailsViewModel.Companion.ARTIST_SECTION_LIMIT
import io.music_assistant.client.ui.compose.item.ItemList
import io.music_assistant.client.ui.compose.item.Section
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ArtistDetailsViewModel(
    private val artist: Artist,
    private val mediaItemRepository: MediaItemRepository,
) : ViewModel() {
    private val fetchArtistItemsUseCase = FetchArtistItemsUseCase(mediaItemRepository)

    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()

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

                    _state.updateItems(mediaItemRepository, result.getOrEmptyList()) { value, items ->
                        value.copy(
                            library = DataState.Data(
                                Section(
                                    items = items
                                        .filterIsInstance<Album>()
                                        .take(ARTIST_SECTION_LIMIT),
                                    itemList = ItemList.ArtistLibrary(artist.itemId),
                                ),
                            ),
                        )
                    }
                } else {
                    _state.update {
                        it.copy(library = DataState.NoData())
                    }
                }
            } catch (e: Exception) {
                Logger.e("Failed to load artist album sections", e)
                _state.update {
                    it.copy(
                        library = DataState.Error(),
                    )
                }
            }
        }

        viewModelScope.launch {
            val itemsWithMappings = fetchArtistItemsUseCase.run(
                artist,
                Request.Artist::getAlbums,
            )

            if (itemsWithMappings != null) {
                _state.updateItems(mediaItemRepository, itemsWithMappings.items) { value, items ->
                    value.copy(
                        all = DataState.Data(
                            Section(
                                items.filterIsInstance<Album>().take(ARTIST_SECTION_LIMIT),
                                providerDomain = itemsWithMappings.mapping.providerDomain,
                                itemList = ItemList.ArtistAlbums(
                                    itemsWithMappings.mapping.providerInstance,
                                    itemsWithMappings.mapping.itemId,
                                ),
                            ),
                        ),
                    )
                }
            } else {
                _state.update {
                    it.copy(
                        all = DataState.Error(),
                    )
                }
            }
        }

        viewModelScope.launch {
            val itemsWithMappings = fetchArtistItemsUseCase.run(
                artist,
                Request.Artist::getTopTracks,
            )

            if (itemsWithMappings != null) {
                _state.updateItems(mediaItemRepository, itemsWithMappings.items) { value, items ->
                    value.copy(
                        topTracks = DataState.Data(
                            Section(
                                items.filterIsInstance<Track>().take(ARTIST_SECTION_LIMIT),
                                providerDomain = itemsWithMappings.mapping.providerDomain,
                                itemList = ItemList.ArtistTopTracks(
                                    itemsWithMappings.mapping.providerInstance,
                                    itemsWithMappings.mapping.itemId,
                                ),
                            ),
                        ),
                    )
                }
            } else {
                _state.update {
                    it.copy(
                        topTracks = DataState.Error(),
                    )
                }
            }
        }
    }

    fun loadAlbumsForProvider(mapping: ProviderMapping) {
        viewModelScope.launch {
            val itemId = mapping.itemId
            val providerInstance = mapping.providerInstance

            val albums = fetchArtistItems(
                Request.Artist.getAlbums(
                    itemId,
                    providerInstance,
                ),
            ).filterIsInstance<Album>()

            _state.update {
                it.copy(
                    all = DataState.Data(
                        Section(
                            albums.take(ARTIST_SECTION_LIMIT),
                            providerDomain = mapping.providerDomain,
                            itemList = ItemList.ArtistAlbums(providerInstance, itemId),
                        ),
                    ),
                )
            }
        }
    }

    fun loadTopTracksForProvider(mapping: ProviderMapping) {
        viewModelScope.launch {
            val itemId = mapping.itemId
            val providerInstance = mapping.providerInstance

            val tracks = fetchArtistItems(
                Request.Artist.getTopTracks(
                    itemId,
                    providerInstance,
                ),
            ).filterIsInstance<Track>()

            _state.update {
                it.copy(
                    topTracks = DataState.Data(
                        Section(
                            tracks.take(ARTIST_SECTION_LIMIT),
                            providerDomain = mapping.providerDomain,
                            itemList = ItemList.ArtistTopTracks(providerInstance, itemId),
                        ),
                    ),
                )
            }
        }
    }

    private suspend fun fetchArtistItems(request: Request): List<AppMediaItem> =
        mediaItemRepository.fetchMediaItems(request).getOrNull() ?: emptyList()

    data class State(
        val library: DataState<Section<Album>> = DataState.Loading(),
        val all: DataState<Section<Album>> = DataState.Loading(),
        val topTracks: DataState<Section<Track>> = DataState.Loading(),
    ) {
        companion object {
            fun loading() =
                State(DataState.Loading(), DataState.Loading(), DataState.Loading())

            fun error() = State(DataState.Error(), DataState.Error(), DataState.Error())
        }
    }
}
