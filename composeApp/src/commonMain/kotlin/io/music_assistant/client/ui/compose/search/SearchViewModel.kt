package io.music_assistant.client.ui.compose.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.music_assistant.client.api.Request
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.data.MainDataSource
import io.music_assistant.client.data.model.client.AppMediaItem
import io.music_assistant.client.data.model.client.AppMediaItem.Companion.toAppMediaItem
import io.music_assistant.client.data.model.client.AppMediaItem.Companion.toAppMediaItemList
import io.music_assistant.client.data.model.server.MediaType
import io.music_assistant.client.data.model.server.QueueOption
import io.music_assistant.client.data.model.server.SearchResult
import io.music_assistant.client.data.model.server.ServerMediaItem
import io.music_assistant.client.data.model.server.events.MediaItemAddedEvent
import io.music_assistant.client.data.model.server.events.MediaItemDeletedEvent
import io.music_assistant.client.data.model.server.events.MediaItemUpdatedEvent
import io.music_assistant.client.ui.compose.common.DataState
import io.music_assistant.client.utils.SessionState
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
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(FlowPreview::class, ExperimentalAtomicApi::class)
class SearchViewModel(
    private val apiClient: ServiceClient,
    private val mainDataSource: MainDataSource,
) : ViewModel() {

    val serverUrl = apiClient.sessionState.map {
        (it as? SessionState.Connected)?.serverInfo?.baseUrl
    }

    val searchJob = AtomicReference<Job?>(null)

    private val _state = MutableStateFlow(
        State(
            searchState = SearchState(
                query = "",
                mediaTypes = listOf(
                    MediaTypeSelect(MediaType.ARTIST, false),
                    MediaTypeSelect(MediaType.ALBUM, false),
                    MediaTypeSelect(MediaType.TRACK, false),
                    MediaTypeSelect(MediaType.PLAYLIST, false),
                    MediaTypeSelect(MediaType.AUDIOBOOK, false),
                    MediaTypeSelect(MediaType.PODCAST, false),
                    MediaTypeSelect(MediaType.RADIO, false),
                ),
                libraryOnly = false
            ),
            resultsState = DataState.NoData()
        )
    )
    val state = _state.asStateFlow()

    init {
        // Debounced search
        viewModelScope.launch {
            _state.map { it.searchState }
                .distinctUntilChanged()
                .filter { it.query.trim().length > 2 || it.query.isEmpty() }
                .debounce { 500 }
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
                    is MediaItemDeletedEvent -> {
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
                    }
                )
            )
        }
    }

    fun onLibraryOnlyToggled(libraryOnly: Boolean) {
        _state.update { it.copy(searchState = it.searchState.copy(libraryOnly = libraryOnly)) }
    }

    fun onPlayClick(track: AppMediaItem, option: QueueOption, radio: Boolean) {
        viewModelScope.launch {
            mainDataSource.selectedPlayer?.queueOrPlayerId?.let { queueId ->
                track.uri?.let { uri ->
                    apiClient.sendRequest(
                        Request.Library.play(
                            media = listOf(uri),
                            queueOrPlayerId = queueId,
                            option = option,
                            radioMode = radio
                        )
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
                        libraryOnly = searchState.libraryOnly
                    )
                )
                if (isActive) {
                    result.getOrNull()?.resultAs<SearchResult>()?.toAppMediaItemList()
                        ?.let { items ->
                            val results = SearchResults(
                                artists = items.filterIsInstance<AppMediaItem.Artist>(),
                                albums = items.filterIsInstance<AppMediaItem.Album>(),
                                tracks = items.filterIsInstance<AppMediaItem.Track>(),
                                playlists = items.filterIsInstance<AppMediaItem.Playlist>(),
                                audiobooks = items.filterIsInstance<AppMediaItem.Audiobook>(),
                                podcasts = items.filterIsInstance<AppMediaItem.Podcast>(),
                                radios = items.filterIsInstance<AppMediaItem.RadioStation>()
                            )
                            if (isActive) {
                                _state.update { it.copy(resultsState = DataState.Data(results)) }
                            }
                        } ?: run {
                        _state.update { it.copy(resultsState = DataState.Error()) }
                    }
                }
            }
        )?.cancel()
    }

    data class State(
        val searchState: SearchState,
        val resultsState: DataState<SearchResults>
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
        val isSelected: Boolean
    )

    data class SearchResults(
        val artists: List<AppMediaItem.Artist>,
        val albums: List<AppMediaItem.Album>,
        val tracks: List<AppMediaItem.Track>,
        val playlists: List<AppMediaItem.Playlist>,
        val audiobooks: List<AppMediaItem.Audiobook>,
        val podcasts: List<AppMediaItem.Podcast>,
        val radios: List<AppMediaItem.RadioStation>
    )
}
