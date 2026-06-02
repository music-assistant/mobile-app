package io.music_assistant.client.ui.compose.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.music_assistant.client.api.Request
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.data.MainDataSource
import io.music_assistant.client.data.model.client.MediaType
import io.music_assistant.client.data.model.client.QueueOption
import io.music_assistant.client.data.model.client.items.Album
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.client.items.Artist
import io.music_assistant.client.data.model.client.items.Audiobook
import io.music_assistant.client.data.model.client.items.Genre
import io.music_assistant.client.data.model.client.items.Playlist
import io.music_assistant.client.data.model.client.items.Podcast
import io.music_assistant.client.data.model.client.items.RadioStation
import io.music_assistant.client.data.model.client.items.Track
import io.music_assistant.client.data.repository.MediaItemRepository
import io.music_assistant.client.ui.Timings
import io.music_assistant.client.ui.compose.common.DataState
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
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

/**
 * Intent to escalate an empty in-library quick search to the global Search tab.
 *
 * @param mediaType the referring library's type, used to pre-select the matching filter chip;
 *   null (or a type with no chip, e.g. GENRE) searches all types.
 */
data class GlobalSearchRequest(val query: String, val mediaType: MediaType?)

@OptIn(FlowPreview::class, ExperimentalAtomicApi::class)
class SearchViewModel(
    private val apiClient: ServiceClient,
    private val mainDataSource: MainDataSource,
    private val mediaItemRepository: MediaItemRepository,
) : ViewModel() {
    val searchJob = AtomicReference<Job?>(null)

    private val searchTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

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
        // Explicit-trigger search, debounced for sanity (rapid taps, filter toggles).
        viewModelScope.launch {
            searchTrigger
                .debounce(Timings.INPUT_DEBOUNCE)
                .collect {
                    val searchState = _state.value.searchState
                    if (searchState.query.isNotBlank()) {
                        performSearch(searchState)
                    } else {
                        _state.update { it.copy(resultsState = DataState.NoData()) }
                    }
                }
        }

        // Listen to real-time library changes; refresh any tracks already shown.
        viewModelScope.launch {
            mediaItemRepository.itemChanges.collect { change ->
                (change.item as? Track)?.let { updateSearchResultsIfNeeded(it) }
            }
        }
    }

    /**
     * Escalation from an empty in-library quick search: pre-fill the query, pre-select the
     * referring library's filter chip, and run the search.
     */
    fun applyGlobalSearch(request: GlobalSearchRequest) {
        _state.update { state ->
            state.copy(
                searchState = state.searchState.copy(
                    query = request.query,
                    mediaTypes = state.searchState.mediaTypes.map { mediaTypeSelect ->
                        // Select only the referring type; an unmatched/null type (e.g. GENRE,
                        // which has no chip) leaves all unselected → search all types.
                        mediaTypeSelect.copy(isSelected = mediaTypeSelect.type == request.mediaType)
                    },
                ),
            )
        }
        searchTrigger.tryEmit(Unit)
    }

    fun onQueryChanged(query: String) {
        _state.update { it.copy(searchState = it.searchState.copy(query = query)) }
    }

    fun onSearchTriggered() {
        searchTrigger.tryEmit(Unit)
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
        searchTrigger.tryEmit(Unit)
    }

    fun onLibraryOnlyToggled(libraryOnly: Boolean) {
        _state.update { it.copy(searchState = it.searchState.copy(libraryOnly = libraryOnly)) }
        searchTrigger.tryEmit(Unit)
    }

    fun onPlayClick(
        track: AppMediaItem,
        option: QueueOption,
        radio: Boolean,
    ) {
        viewModelScope.launch {
            mainDataSource.selectedPlayer?.queueOrPlayerId?.let { queueId ->
                track.mediaUri?.let { mediaUri ->
                    apiClient.sendRequest(
                        Request.Library.play(
                            media = listOf(mediaUri),
                            queueOrPlayerId = queueId,
                            option = option,
                            radioMode = radio && track !is Genre,
                        ),
                    )
                }
            }
        }
    }

    private fun updateSearchResultsIfNeeded(changed: Track) {
        val resultsData = (_state.value.resultsState as? DataState.Data)?.data
        if (resultsData != null) {
            val updatedTracks = resultsData.tracks.map { track ->
                if (track.hasAnyMappingFrom(changed)) changed else track
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

                val result = mediaItemRepository.search(
                    Request.Library.search(
                        query = searchState.query,
                        mediaTypes = searchState.selectedMediaTypes,
                        limit = 200,
                        libraryOnly = searchState.libraryOnly,
                    ),
                )
                if (isActive) {
                    result.getOrNull()?.let { data ->
                        val results = SearchResults(
                            artists = data.artists,
                            albums = data.albums,
                            tracks = data.tracks,
                            playlists = data.playlists,
                            audiobooks = data.audiobooks,
                            podcasts = data.podcasts,
                            radios = data.radios,
                            genres = data.genres,
                        )
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
        val artists: List<Artist>,
        val albums: List<Album>,
        val tracks: List<Track>,
        val playlists: List<Playlist>,
        val audiobooks: List<Audiobook>,
        val podcasts: List<Podcast>,
        val radios: List<RadioStation>,
        val genres: List<Genre>,
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
}
