package io.music_assistant.client.ui.compose.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.music_assistant.client.api.Request
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.data.MainDataSource
import io.music_assistant.client.data.model.client.AppMediaItem
import io.music_assistant.client.data.model.client.AppMediaItem.Companion.toAppMediaItem
import io.music_assistant.client.data.model.server.MediaType
import io.music_assistant.client.data.model.server.QueueOption
import io.music_assistant.client.data.model.server.SearchResult
import io.music_assistant.client.data.model.server.ServerMediaItem
import io.music_assistant.client.data.model.server.events.MediaItemAddedEvent
import io.music_assistant.client.data.model.server.events.MediaItemDeletedEvent
import io.music_assistant.client.data.model.server.events.MediaItemUpdatedEvent
import io.music_assistant.client.ui.Timings
import io.music_assistant.client.ui.compose.common.DataState
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.media_type_albums
import musicassistantclient.composeapp.generated.resources.media_type_artists
import musicassistantclient.composeapp.generated.resources.media_type_audiobooks
import musicassistantclient.composeapp.generated.resources.media_type_genres
import musicassistantclient.composeapp.generated.resources.media_type_playlists
import musicassistantclient.composeapp.generated.resources.media_type_podcasts
import musicassistantclient.composeapp.generated.resources.media_type_radio
import musicassistantclient.composeapp.generated.resources.media_type_tracks
import org.jetbrains.compose.resources.StringResource
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(FlowPreview::class, ExperimentalAtomicApi::class)
class SearchViewModel(
    private val apiClient: ServiceClient,
    private val mainDataSource: MainDataSource,
) : ViewModel() {
    val serverUrl = apiClient.serverBaseUrl

    val searchJob = AtomicReference<Job?>(null)

    private val _state = MutableStateFlow(
        State(
            searchState = SearchState(
                query = "",
                mediaTypes = listOf(
                    MediaType.ARTIST,
                    MediaType.ALBUM,
                    MediaType.TRACK,
                    MediaType.PLAYLIST,
                    MediaType.AUDIOBOOK,
                    MediaType.PODCAST,
                    MediaType.RADIO,
                    // TODO server doesn't return genre in this endpoint yet,
                    //  need to fetch separately if we want to show it
                    // MediaType.GENRE,
                ).map { MediaTypeSelect(it, false) },
                libraryOnly = false,
            ),
            resultsState = DataState.NoData(),
        ),
    )
    val state = _state.asStateFlow()

    init {
        // Debounced search
        viewModelScope.launch {
            _state.map { it.searchState }
                .distinctUntilChanged()
                .filter { it.query.trim().length > 2 || it.query.isEmpty() }
                .debounce { Timings.INPUT_DEBOUNCE }
                .collect { searchState ->
                    if (searchState.query.isNotEmpty()) {
                        performSearch(searchState)
                    } else {
                        _state.update { it.copy(resultsState = DataState.NoData()) }
                    }
                }
        }

        // Listen to real-time events for track updates
        viewModelScope.launch {
            apiClient.events.collect { event ->
                when (event) {
                    is MediaItemUpdatedEvent,
                    is MediaItemAddedEvent,
                    is MediaItemDeletedEvent,
                        -> {
                        event.data?.let { updateSearchResultsIfNeeded(it) }
                    }

                    else -> Unit
                }
            }
        }
    }

    fun onQueryChanged(query: String) {
        _state.update { it.copy(searchState = it.searchState.copy(query = query)) }
    }

    fun onMediaTypeToggled(type: MediaType, isSelected: Boolean) {
        _state.update { state ->
            state.copy(
                searchState = state.searchState.copy(
                    mediaTypes = state.searchState.mediaTypes.map { mediaTypeSelect ->
                        if (mediaTypeSelect.type == type) {
                            mediaTypeSelect.copy(isSelected = isSelected)
                        } else {
                            mediaTypeSelect
                        }
                    },
                ),
            )
        }
    }

    fun onLibraryOnlyToggled(libraryOnly: Boolean) {
        _state.update { it.copy(searchState = it.searchState.copy(libraryOnly = libraryOnly)) }
    }

    fun onPlayClick(track: AppMediaItem, option: QueueOption, radio: Boolean) {
        viewModelScope.launch {
            mainDataSource.selectedPlayer?.queueOrPlayerId?.let { queueId ->
                track.mediaUri?.let { mediaUri ->
                    apiClient.sendRequest(
                        Request.Library.play(
                            media = listOf(mediaUri),
                            queueOrPlayerId = queueId,
                            option = option,
                            radioMode = radio && track !is AppMediaItem.Genre,
                        ),
                    )
                }
            }
        }
    }

    private fun updateSearchResultsIfNeeded(serverItem: ServerMediaItem) {
        val resultsData = (_state.value.resultsState as? DataState.Data)?.data
        if (resultsData != null) {
            val updatedTracks = resultsData.tracks.map { track ->
                if (track.hasAnyMappingFrom(serverItem)) {
                    serverItem.toAppMediaItem() as? AppMediaItem.Track ?: track
                } else {
                    track
                }
            }
            _state.update {
                it.copy(resultsState = DataState.Data(resultsData.copy(tracks = updatedTracks)))
            }
        }
    }

    private fun performSearch(searchState: SearchState) {
        searchJob.exchange(
            viewModelScope.launch {
                _state.update { it.copy(resultsState = DataState.Loading()) }

                val result = apiClient.sendRequest(
                    Request.Library.search(
                        query = searchState.query,
                        mediaTypes = searchState.selectedMediaTypes,
                        limit = 200,
                        libraryOnly = searchState.libraryOnly,
                    ),
                )
                if (isActive) {
                    result.getOrNull()?.resultAs<SearchResult>()?.let { search ->
                        val results = search.toAppSearchResults()
                        if (isActive) {
                            _state.update { it.copy(resultsState = DataState.Data(results)) }
                        }
                    } ?: run {
                        _state.update { it.copy(resultsState = DataState.Error()) }
                    }
                }
            },
        )?.cancel()
    }

    data class State(
        val searchState: SearchState,
        val resultsState: DataState<SearchResults>,
    )

    data class SearchState(
        val query: String,
        val mediaTypes: List<MediaTypeSelect>,
        val libraryOnly: Boolean,
    ) {
        val selectedMediaTypes = mediaTypes.filter { it.isSelected }.map { it.type }
    }

    data class MediaTypeSelect(
        val type: MediaType,
        val isSelected: Boolean,
    )

    data class SearchResults(
        val artists: List<AppMediaItem.Artist>,
        val albums: List<AppMediaItem.Album>,
        val tracks: List<AppMediaItem.Track>,
        val playlists: List<AppMediaItem.Playlist>,
        val audiobooks: List<AppMediaItem.Audiobook>,
        val podcasts: List<AppMediaItem.Podcast>,
        val radios: List<AppMediaItem.RadioStation>,
        val genres: List<AppMediaItem.Genre>,
    ) {
        val nonEmptyLists = listOf(
            Item(Res.string.media_type_tracks, tracks),
            Item(Res.string.media_type_artists, artists),
            Item(Res.string.media_type_albums, albums),
            Item(Res.string.media_type_playlists, playlists),
            Item(Res.string.media_type_podcasts, podcasts),
            Item(Res.string.media_type_audiobooks, audiobooks),
            Item(Res.string.media_type_radio, radios),
            Item(Res.string.media_type_genres, genres),
        ).filter { it.items.isNotEmpty() }

        data class Item(
            val mediaTypeName: StringResource,
            val items: List<AppMediaItem>,
        )
    }

    private fun SearchResult.toAppSearchResults() = SearchResults(
        artists = artists.mapNotNull { it.toAppMediaItem() as? AppMediaItem.Artist },
        albums = albums.mapNotNull { it.toAppMediaItem() as? AppMediaItem.Album },
        tracks = tracks.mapNotNull { it.toAppMediaItem() as? AppMediaItem.Track },
        playlists = playlists.mapNotNull { it.toAppMediaItem() as? AppMediaItem.Playlist },
        audiobooks = audiobooks.mapNotNull { it.toAppMediaItem() as? AppMediaItem.Audiobook },
        podcasts = podcasts.mapNotNull { it.toAppMediaItem() as? AppMediaItem.Podcast },
        radios = radio.mapNotNull { it.toAppMediaItem() as? AppMediaItem.RadioStation },
        genres = genres.mapNotNull { it.toAppMediaItem() as? AppMediaItem.Genre },
    )
}
