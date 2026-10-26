package io.music_assistant.client.ui.compose.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import io.music_assistant.client.api.Request
import io.music_assistant.client.data.MainDataSource
import io.music_assistant.client.data.UserPreferences
import io.music_assistant.client.data.model.client.MediaType
import io.music_assistant.client.data.model.client.items.Album
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.client.items.Artist
import io.music_assistant.client.data.model.client.items.Audiobook
import io.music_assistant.client.data.model.client.items.Genre
import io.music_assistant.client.data.model.client.items.Playlist
import io.music_assistant.client.data.model.client.items.Podcast
import io.music_assistant.client.data.model.client.items.RadioStation
import io.music_assistant.client.data.model.client.items.Track
import io.music_assistant.client.data.model.server.ServerProviderInstance
import io.music_assistant.client.data.play.PlayDispatcher
import io.music_assistant.client.data.play.PlayMode
import io.music_assistant.client.data.play.PlayRequest
import io.music_assistant.client.data.repository.MediaItemChange
import io.music_assistant.client.data.repository.MediaItemRepository
import io.music_assistant.client.data.repository.ProviderRepository
import io.music_assistant.client.data.repository.favoriteAfter
import io.music_assistant.client.ui.Timings
import io.music_assistant.client.ui.compose.common.DataState
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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
 *   null (or a type with no chip) searches all types.
 */
data class GlobalSearchRequest(val query: String, val mediaType: MediaType?)

@OptIn(FlowPreview::class, ExperimentalAtomicApi::class)
class SearchViewModel(
    private val playDispatcher: PlayDispatcher,
    private val mainDataSource: MainDataSource,
    private val mediaItemRepository: MediaItemRepository,
    private val userPreferences: UserPreferences,
    private val providerRepository: ProviderRepository,
) : ViewModel() {
    val searchJob = AtomicReference<Job?>(null)

    private val searchTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Recent search terms, newest first, shared with the web frontend. */
    val recentSearches: StateFlow<List<String>> = userPreferences.recentSearches

    // The query of the shown results, not the edited text: a result belongs to this query.
    private var searchedQuery = ""

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
                    MediaType.GENRE,
                ).map { MediaTypeSelect(it, false) },
                libraryOnly = false,
                sources = null,
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
                    // Read the sources now: a save just before the search is in the flows already,
                    // but not yet in the state.
                    val searchState = _state.value.searchState.copy(sources = currentSources())
                    if (searchState.query.isNotBlank()) {
                        searchedQuery = searchState.query
                        performSearch(searchState)
                    } else {
                        _state.update { it.copy(resultsState = DataState.NoData()) }
                    }
                }
        }

        // The source picker follows the server, the provider list and the saved choice.
        viewModelScope.launch {
            combine(
                mainDataSource.searchSourcesSupported,
                providerRepository.providers,
                userPreferences.searchSources,
                ::sourcesOf,
            ).collect { sources ->
                _state.update { it.copy(searchState = it.searchState.copy(sources = sources)) }
            }
        }

        // Listen to real-time library changes; refresh any tracks already shown.
        viewModelScope.launch {
            mediaItemRepository.itemChanges.collect { change ->
                (change.item as? Track)?.let { updateSearchResultsIfNeeded(it, change) }
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
                        // Select only the referring type; an unmatched/null type (one with
                        // no chip) leaves all unselected → search all types.
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

    fun onSearch() {
        searchTrigger.tryEmit(Unit)
    }

    /**
     * [sources] is the new source choice, null on a server without it. A changed choice is saved
     * first, so the search and the picker show the same choice, also after a failed save.
     */
    fun onFiltersChanged(mediaTypes: List<MediaType>, libraryOnly: Boolean, sources: List<String>?) {
        _state.update { state ->
            state.copy(
                searchState = state.searchState.copy(
                    mediaTypes = state.searchState.mediaTypes.map { mediaTypeSelect ->
                        mediaTypeSelect.copy(isSelected = mediaTypes.contains(mediaTypeSelect.type))
                    },
                    libraryOnly = libraryOnly,
                ),
            )
        }

        sources
            ?.takeIf { it.toSet() != currentSources()?.selected?.toSet() }
            ?.let {
                viewModelScope.launch {
                    userPreferences.setSearchSources(it)
                    searchTrigger.tryEmit(Unit)
                }
            }
            ?: searchTrigger.tryEmit(Unit)
    }

    private fun currentSources() = sourcesOf(
        mainDataSource.searchSourcesSupported.value,
        providerRepository.providers.value,
        userPreferences.searchSources.value,
    )

    /** A tap on a recent search term runs that search again. */
    fun onRecentSearchClick(term: String) {
        onQueryChanged(term)
        onSearch()
    }

    /** The user opened a result: like the web frontend, the query becomes a recent search. */
    fun onResultOpened() {
        searchedQuery.takeIf { it.isNotBlank() }?.let { query ->
            viewModelScope.launch { userPreferences.addRecentSearch(query) }
        }
    }

    fun onPlayClick(track: AppMediaItem, mode: PlayMode) {
        onResultOpened()
        mainDataSource.selectedPlayer?.queueOrPlayerId?.let { queueId ->
            track.mediaUri?.let { mediaUri ->
                Logger.withTag("PlayDispatch")
                    .i { "SearchViewModel: uri=$mediaUri mode=$mode queue=$queueId" }
                playDispatcher.play(
                    PlayRequest(
                        queueOrPlayerId = queueId,
                        media = listOf(mediaUri),
                        option = mode.option,
                        endlessMixMode = mode.endlessMix && track !is Genre,
                        shuffle = mode.shuffle,
                        startFromBeginning = mode.startFromBeginning,
                    ),
                )
            }
        }
    }

    private fun updateSearchResultsIfNeeded(changed: Track, change: MediaItemChange) {
        val resultsData = (_state.value.resultsState as? DataState.Data)?.data
        if (resultsData != null) {
            val updatedTracks = resultsData.tracks.map { track ->
                if (track.hasAnyMappingFrom(changed)) {
                    changed.withFavorite(change.favoriteAfter(track.favorite))
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

                val result = mediaItemRepository.search(searchRequest(searchState))
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
        // Null on a server without `providers`, which keeps [libraryOnly].
        val sources: SearchSources?,
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

    internal companion object {
        // music/search applies `limit` PER MEDIA TYPE, and streaming providers page it in
        // chunks of 10 behind a rate limiter (~1.0-1.5s per page). The server abandons a
        // provider that takes longer than its soft timeout (8s) and reports it as empty,
        // so ceil(limit / 10) pages must stay well inside that budget. See #929.

        /** Multi-type results render as carousels; a shallow list per type is enough. */
        const val SEARCH_LIMIT_OVERVIEW = 10

        /** A single selected type renders as a full list, so fetch deeper. */
        const val SEARCH_LIMIT_FOCUSED = 30

        /** Pick the limit from the number of selected filter chips (0 = all types). */
        private fun sourcesOf(
            supported: Boolean,
            providers: DataState<List<ServerProviderInstance>>,
            saved: List<String>,
        ): SearchSources? = SearchSources.of((providers as? DataState.Data)?.data, saved).takeIf { supported }

        fun searchLimitFor(selectedMediaTypeCount: Int) =
            if (selectedMediaTypeCount == 1) SEARCH_LIMIT_FOCUSED else SEARCH_LIMIT_OVERVIEW

        /**
         * Genres live in the library only, so a genre-only search skips the providers
         * instead of waiting on them for nothing. The server searches genres from 2.10.0.
         */
        fun searchRequest(searchState: SearchState): Request {
            val genreOnly = searchState.selectedMediaTypes == listOf(MediaType.GENRE)
            return Request.Library.search(
                query = searchState.query,
                mediaTypes = searchState.selectedMediaTypes,
                limit = searchLimitFor(searchState.selectedMediaTypes.size),
                libraryOnly = searchState.sources == null && (searchState.libraryOnly || genreOnly),
                providers = searchState.sources?.let { sources ->
                    if (genreOnly) listOf(SearchSources.LIBRARY) else sources.selected.ifEmpty { null }
                },
            )
        }
    }
}
