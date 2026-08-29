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
import io.music_assistant.client.ui.compose.common.DataState
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
                val library = if (artist.isInLibrary) {
                    fetchArtistItems(Request.Artist.getAlbums(artist.itemId, artist.provider))
                        .filterIsInstance<Album>()
                } else {
                    emptyList()
                }

                _state.update {
                    it.copy(
                        library = DataState.Data(
                            Section(
                                items = library.take(ARTIST_SECTION_LIMIT),
                                itemList = ItemList.ArtistLibrary(artist.itemId),
                            ),
                        ),
                    )
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
            val list = fetchArtistItemsUseCase.run(
                artist,
                Request.Artist::getAlbums,
                Album::class,
            )

            if (list != null) {
                _state.update {
                    it.copy(
                        all = DataState.Data(
                            Section(
                                list.items.take(ARTIST_SECTION_LIMIT),
                                providerDomain = list.mapping.providerDomain,
                                itemList = ItemList.ArtistAlbums(
                                    list.mapping.providerInstance,
                                    list.mapping.itemId,
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
            val list = fetchArtistItemsUseCase.run(
                artist,
                Request.Artist::getTopTracks,
                Track::class,
            )

            if (list != null) {
                _state.update {
                    it.copy(
                        topTracks = DataState.Data(
                            Section(
                                list.items.take(ARTIST_SECTION_LIMIT),
                                providerDomain = list.mapping.providerDomain,
                                itemList = ItemList.ArtistTopTracks(
                                    list.mapping.providerInstance,
                                    list.mapping.itemId,
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
