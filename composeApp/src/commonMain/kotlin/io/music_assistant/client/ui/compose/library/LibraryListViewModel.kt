package io.music_assistant.client.ui.compose.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import io.music_assistant.client.api.Request
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.data.MainDataSource
import io.music_assistant.client.data.model.client.ArtistType
import io.music_assistant.client.data.model.client.LibraryFilters
import io.music_assistant.client.data.model.client.MediaType
import io.music_assistant.client.data.model.client.SortConfig
import io.music_assistant.client.data.model.client.SortOption
import io.music_assistant.client.data.model.client.genreMediaTypeFilter
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.client.items.Genre
import io.music_assistant.client.data.model.client.items.MediaCollection
import io.music_assistant.client.data.model.client.items.isAiRadioShow
import io.music_assistant.client.data.model.server.ListSort
import io.music_assistant.client.data.play.PlayDispatcher
import io.music_assistant.client.data.play.PlayMode
import io.music_assistant.client.data.play.PlayRequest
import io.music_assistant.client.data.repository.LibrarySortMenu
import io.music_assistant.client.data.repository.LibrarySortRepository
import io.music_assistant.client.data.repository.MediaItemChange
import io.music_assistant.client.data.repository.MediaItemRepository
import io.music_assistant.client.data.repository.ProviderRepository
import io.music_assistant.client.data.repository.favoriteAfter
import io.music_assistant.client.settings.SeriesGrouping
import io.music_assistant.client.settings.SettingsRepository
import io.music_assistant.client.ui.Timings
import io.music_assistant.client.ui.compose.common.DataState
import io.music_assistant.client.ui.compose.common.SelectOption
import io.music_assistant.client.ui.compose.common.mapData
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.toast_error_create_playlist
import org.jetbrains.compose.resources.getString

@OptIn(FlowPreview::class)
class LibraryListViewModel(
    private val mediaType: MediaType,
    private val apiClient: ServiceClient,
    private val mainDataSource: MainDataSource,
    private val settingsRepository: SettingsRepository,
    private val mediaItemRepository: MediaItemRepository,
    private val playDispatcher: PlayDispatcher,
    private val librarySortRepository: LibrarySortRepository,
    providerRepository: ProviderRepository,
) : ViewModel() {
    private val searchTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    // This VM is recreated on every tab re-enter, so the persisted sort is what makes the
    // choice survive leaving the screen. Null is the list default of the server.
    private var chosenSort: SortOption? = settingsRepository.getSortOption(mediaType)

    private val _state = MutableStateFlow(
        State(
            dataState = DataState.Loading(),
            mediaType = mediaType,
            seriesGrouping = settingsRepository.getSeriesGrouping(),
        ),
    )
    val state = _state.asStateFlow()

    private val _toasts = MutableSharedFlow<String>()
    val toasts = _toasts.asSharedFlow()

    // Provider filter options follow the shared provider list, so a provider change shows here
    // while the screen is open. Only music providers that are available and serve this media
    // type are offered (so a radio-only provider is not offered on Artists).
    val providerOptions: StateFlow<DataState<List<SelectOption<String>>>> =
        providerFeatureFor(mediaType)
            ?.let { feature ->
                providerRepository.providers.mapData { providers ->
                    providers
                        .filter { it.type == "music" && it.available && feature in it.supportedFeatures }
                        .map { SelectOption(it.instanceId, it.name) }
                        .sortedBy { it.label.lowercase() }
                }
            }
            ?.stateIn(viewModelScope, SharingStarted.Eagerly, DataState.NoData())
            ?: MutableStateFlow(DataState.NoData())

    // Genre filter options, loaded lazily the first time the filter sheet asks for them
    // (see loadFilterOptions).
    private val _genreOptions =
        MutableStateFlow<DataState<List<SelectOption<Int>>>>(DataState.NoData())
    val genreOptions = _genreOptions.asStateFlow()

    // The artist types the library holds besides singers; the Audiobooks sheet offers them.
    // Loaded with the genre options. Empty until then, and after a failed load.
    private val _audiobookArtistTypes = MutableStateFlow<List<ArtistType>>(emptyList())
    val audiobookArtistTypes = _audiobookArtistTypes.asStateFlow()
    private var optionsRequested = false

    init {
        viewModelScope.launch {
            searchTrigger
                .debounce { Timings.INPUT_DEBOUNCE }
                .collect {
                    loadFirstPage()
                }
        }

        // Settings are the source of truth for filters; fold emissions into state
        // (Apply-based writes land here and trigger the debounced refetch above).
        viewModelScope.launch {
            settingsRepository.libraryFilters(mediaType).collect { filters ->
                _state.update { it.copy(filters = filters) }
                searchTrigger.tryEmit(Unit)
            }
        }

        // Reflect server-confirmed library lifecycle changes on an already-open
        // list, so a mutation from any surface (this screen, context menu, another
        // client) keeps the list in sync without a manual refresh.
        viewModelScope.launch {
            mediaItemRepository.itemChanges.collect { change ->
                when (change) {
                    // In-place row patch, no refetch.
                    is MediaItemChange.Updated,
                    is MediaItemChange.FavoriteUpdated,
                        -> patchItem(change)
                    // Refetch first page so the new row lands in its sorted/filtered
                    // position (matches the "page refresh" semantics for creation).
                    is MediaItemChange.Added ->
                        if (change.item.mediaType == _state.value.listedMediaType) loadFirstPage()
                    // Cheap in-place removal; ordering of the rest is unaffected.
                    is MediaItemChange.Deleted -> removeItem(change.item)
                }
            }
        }

        searchTrigger.tryEmit(Unit)
    }

    private fun removeItem(deleted: AppMediaItem) {
        if (deleted.mediaType != _state.value.listedMediaType) return
        _state.update { st ->
            val data = st.dataState as? DataState.Data ?: return@update st
            val remaining = data.data.filterNot { it.matchesIdentityOf(deleted) }
            if (remaining.size == data.data.size) return@update st
            st.copy(dataState = DataState.Data(remaining))
        }
    }

    private fun patchItem(change: MediaItemChange) {
        val updated = change.item
        if (updated.mediaType != _state.value.listedMediaType) return
        _state.update { st ->
            val data = st.dataState as? DataState.Data ?: return@update st
            if (data.data.none { it.matchesIdentityOf(updated) }) return@update st
            val patched = data.data.mapNotNull { item ->
                if (!item.matchesIdentityOf(updated)) return@mapNotNull item
                updated.withFavorite(change.favoriteAfter(item.favorite))
                    // Dropped from the favorites filter; it would be gone on refetch.
                    .takeUnless { st.filters.favorite && it.favorite != true }
            }
            st.copy(dataState = DataState.Data(patched))
        }
    }

    // A favorited non-library item returns from the server re-keyed under the
    // `library` provider with a new itemId, so fall back to provider-mapping
    // identity — the convention the other `itemChanges` consumers use.
    private fun AppMediaItem.matchesIdentityOf(other: AppMediaItem): Boolean =
        (mediaType == other.mediaType && provider == other.provider && itemId == other.itemId) ||
            hasAnyMappingFrom(other)

    // Persistence is the source of truth (like view mode); the settings flow
    // collector folds the new value back into state and triggers a refetch.
    fun setFilters(filters: LibraryFilters) {
        settingsRepository.setLibraryFilters(mediaType, filters)
    }

    // Idempotent: fetch the options once per VM. The genres list has no genre filter,
    // so skip the call entirely there.
    fun loadFilterOptions() {
        if (optionsRequested || mediaType == MediaType.GENRE) return
        optionsRequested = true
        loadGenreOptions()
        if (mediaType == MediaType.AUDIOBOOK) loadAudiobookArtistTypes()
    }

    private fun loadAudiobookArtistTypes() {
        viewModelScope.launch {
            val types = apiClient.sendRequest(Request.Artist.libraryTypes())
                .onFailure { Logger.e("Failed to load library artist types", it) }
                .getOrNull()
                ?.resultAs<List<String>>()
                .orEmpty()
                .mapNotNull(ArtistType::fromServer)
            _audiobookArtistTypes.update { ArtistType.audiobookOptions.filter { it in types } }
        }
    }

    private fun loadGenreOptions() {
        viewModelScope.launch {
            _genreOptions.update { DataState.Loading() }
            val result = mediaItemRepository.fetchMediaItems(
                // media_type scopes to genres that have items of this list's type,
                // which also drops empty/irrelevant genres server-side.
                // No sort argument: every server version orders by sort name by default.
                Request.Genre.listLibrary(
                    limit = GENRE_OPTIONS_LIMIT,
                    mediaType = mediaType.serverValue,
                ),
            )
            _genreOptions.update {
                result.getOrNull()
                    ?.filterIsInstance<Genre>()
                    // Only library genres (integer id) are valid `genre` filter values.
                    ?.mapNotNull { g -> g.itemId.toIntOrNull()?.let { SelectOption(it, g.displayName) } }
                    ?.let { DataState.Data(it) }
                    ?: DataState.Error()
            }
        }
    }

    // Library capability a provider must declare to be offered on this media type's
    // list. Null for types without a provider filter (e.g. GENRE).
    private fun providerFeatureFor(mediaType: MediaType): String? = when (mediaType) {
        MediaType.ARTIST -> "library_artists"
        MediaType.ALBUM -> "library_albums"
        MediaType.TRACK -> "library_tracks"
        MediaType.PLAYLIST -> "library_playlists"
        MediaType.RADIO -> "library_radios"
        MediaType.PODCAST -> "library_podcasts"
        MediaType.AUDIOBOOK -> "library_audiobooks"
        else -> null
    }

    fun onSearch() {
        searchTrigger.tryEmit(Unit)
    }

    fun onSearchQueryChanged(query: String) {
        _state.update { it.copy(searchQuery = query) }
    }

    fun onSeriesGroupingToggled() {
        val grouping = _state.value.seriesGrouping.toggled()
        settingsRepository.setSeriesGrouping(grouping)
        _state.update { it.copy(seriesGrouping = grouping) }
        searchTrigger.tryEmit(Unit)
    }

    fun onSortChanged(sortOption: SortOption) {
        chosenSort = sortOption
        settingsRepository.setSortOption(mediaType, sortOption)
        _state.update { it.copy(sortOption = sortOption) }
        searchTrigger.tryEmit(Unit)
    }

    fun loadMore() {
        val currentState = _state.value

        // Don't load if already loading, no more data, or not in Data state
        if (currentState.isLoadingMore || !currentState.hasMore || currentState.dataState !is DataState.Data) {
            return
        }

        viewModelScope.launch {
            val searchQuery = currentState.serverSearchQuery()
            val sort = currentState.sortMenu?.sortFor(currentState.sortOption)

            _state.update {
                it.copy(isLoadingMore = true)
            }

            val request = getRequest(
                this@LibraryListViewModel.mediaType,
                currentState.offset,
                sort,
                searchQuery,
                currentState.filters,
                currentState.seriesGrouping,
            )
            val result = mediaItemRepository.fetchMediaItems(request)

            result.getOrNull()
                ?.let { newItems ->
                    val currentItems = currentState.dataState.data
                    val allItems = currentItems + newItems
                    updateStateWithData(
                        items = allItems,
                        offset = currentState.offset + PAGE_SIZE,
                        hasMore = newItems.size >= PAGE_SIZE,
                    )
                } ?: run {
                Logger.e("Error loading more for $mediaType:", result.exceptionOrNull())
                // Stop loading more on error
                _state.update {
                    it.copy(
                        isLoadingMore = false,
                        hasMore = false,
                    )
                }
            }
        }
    }

    private fun getRequest(
        mediaType: MediaType,
        offset: Int,
        sort: ListSort?,
        searchQuery: String?,
        filters: LibraryFilters,
        seriesGrouping: SeriesGrouping,
    ): Request {
        val favorite = filters.favorite.takeIf { it }
        val providers = filters.providers.takeIf { it.isNotEmpty() }
        val genres = filters.genres.takeIf { it.isNotEmpty() }
        val request = when (mediaType) {
            MediaType.ARTIST -> Request.Artist.listLibrary(
                limit = PAGE_SIZE,
                offset = offset,
                search = searchQuery,
                sort = sort,
                favorite = favorite,
                albumArtistsOnly = filters.albumArtistsOnly,
                providers = providers,
                genres = genres,
            )

            MediaType.ALBUM -> Request.Album.listLibrary(
                limit = PAGE_SIZE,
                offset = offset,
                search = searchQuery,
                sort = sort,
                favorite = favorite,
                albumTypes = filters.albumTypes.map { it.serverValue },
                providers = providers,
                genres = genres,
            )

            MediaType.TRACK -> Request.Track.list(
                limit = PAGE_SIZE,
                offset = offset,
                search = searchQuery,
                sort = sort,
                favorite = favorite,
                providers = providers,
                genres = genres,
            )

            MediaType.PLAYLIST -> Request.Playlist.listLibrary(
                limit = PAGE_SIZE,
                offset = offset,
                search = searchQuery,
                sort = sort,
                favorite = favorite,
                providers = providers,
                genres = genres,
            )

            // The list shows the library authors or narrators instead of the books while the
            // filter names an artist type; the sort then comes from the artists menu.
            MediaType.AUDIOBOOK -> filters.audiobookArtistType?.let { artistType ->
                Request.Artist.listLibrary(
                    limit = PAGE_SIZE,
                    offset = offset,
                    search = searchQuery,
                    sort = sort,
                    favorite = favorite,
                    providers = providers,
                    genres = genres,
                    artistType = artistType.serverValue,
                )
            } ?: Request.Audiobook.listLibrary(
                limit = PAGE_SIZE,
                offset = offset,
                search = searchQuery,
                sort = sort,
                favorite = favorite,
                providers = providers,
                genres = genres,
                collapseCollections = seriesGrouping == SeriesGrouping.GROUPED,
            )

            MediaType.PODCAST -> Request.Podcast.listLibrary(
                limit = PAGE_SIZE,
                offset = offset,
                search = searchQuery,
                sort = sort,
                favorite = favorite,
                providers = providers,
                genres = genres,
            )

            MediaType.RADIO -> Request.RadioStation.listLibrary(
                limit = PAGE_SIZE,
                offset = offset,
                search = searchQuery,
                sort = sort,
                favorite = favorite,
                providers = providers,
                genres = genres,
            )

            MediaType.GENRE -> Request.Genre.listLibrary(
                limit = PAGE_SIZE,
                offset = offset,
                search = searchQuery,
                sort = sort,
                favorite = favorite,
                providers = providers,
                hideEmpty = filters.hideEmpty.hideEmpty,
                mediaType = filters.genreMediaTypeFilter?.serverValue,
                contentType = filters.genreContentType?.serverValue,
            )

            else -> throw IllegalArgumentException("Invalid MediaType for ItemListViewModel!")
        }

        return request
    }

    // The list refresh is driven by the resulting MediaItemAddedEvent (see the
    // itemChanges collector), so success needs no explicit refetch here.
    fun createPlaylist(name: String) {
        viewModelScope.launch {
            apiClient.sendRequest(Request.Playlist.create(name))
                .onFailure {
                    Logger.e("Failed to create playlist", it)
                    _toasts.emit(getString(Res.string.toast_error_create_playlist))
                }
        }
    }

    fun onPlayClick(item: AppMediaItem, mode: PlayMode) {
        val queueId = mainDataSource.selectedPlayer?.queueOrPlayerId ?: return

        item.mediaUri?.let { mediaUri ->
            Logger.withTag("PlayDispatch")
                .i { "ItemListViewModel: uri=$mediaUri mode=$mode queue=$queueId" }
            playDispatcher.play(
                PlayRequest(
                    queueOrPlayerId = queueId,
                    media = listOf(mediaUri),
                    option = mode.option,
                    endlessMixMode = mode.endlessMix && item !is Genre,
                    shuffle = mode.shuffle,
                    startFromBeginning = mode.startFromBeginning,
                ),
            )
        }
    }

    /** The server accepts any non-blank query; both page loaders must agree or later pages leak unfiltered items. */
    private fun State.serverSearchQuery(): String? = searchQuery.takeIf { it.isNotBlank() }

    private fun loadFirstPage() {
        viewModelScope.launch {
            val searchQuery = state.value.serverSearchQuery()
            updateState(DataState.Loading())
            // Before the request: a 2.11 server rejects a field that the list does not offer.
            val listedMediaType = state.value.listedMediaType
            val sortMenu = librarySortRepository.menu(listedMediaType)
            val sortOption = sortMenu.resolve(chosenSort)
            _state.update { it.copy(sortMenu = sortMenu, sortOption = sortOption) }

            val request = getRequest(
                this@LibraryListViewModel.mediaType,
                0,
                sortMenu.sortFor(sortOption),
                searchQuery,
                state.value.filters,
                state.value.seriesGrouping,
            )
            val result = mediaItemRepository.fetchMediaItems(request)

            result.getOrNull()
                // A grouped audiobook listing mixes series rows in with the books.
                ?.filter { it.mediaType == listedMediaType || it is MediaCollection }
                ?.let { items ->
                    updateStateWithData(
                        items = items,
                        offset = PAGE_SIZE,
                        hasMore = items.size >= PAGE_SIZE,
                    )
                } ?: run {
                Logger.e("Error loading $mediaType:", result.exceptionOrNull())
                updateState(DataState.Error())
            }
        }
    }

    private fun updateState(dataState: DataState<List<AppMediaItem>>) {
        _state.update {
            it.copy(dataState = dataState)
        }
    }

    private fun updateStateWithData(
        items: List<AppMediaItem>,
        offset: Int,
        hasMore: Boolean,
    ) {
        // Callers compute `hasMore` from the raw page, so hiding rows here keeps paging intact.
        val deduped = items
            .filterNot { it.isAiRadioShow }
            .distinctBy { Triple(it.mediaType, it.provider, it.itemId) }
        _state.update {
            it.copy(
                dataState = DataState.Data(deduped),
                offset = offset,
                hasMore = hasMore,
                isLoadingMore = false,
            )
        }
    }

    companion object Companion {
        private const val PAGE_SIZE = 50
        private const val GENRE_OPTIONS_LIMIT = 1000
    }

    data class State(
        val dataState: DataState<List<AppMediaItem>>,
        val mediaType: MediaType,
        val isLoadingMore: Boolean = false,
        val hasMore: Boolean = true,
        val searchQuery: String = "",
        val sortOption: SortOption = SortConfig.defaultFor(mediaType),
        // Null until the first page load resolves the sort choices of the server.
        val sortMenu: LibrarySortMenu? = null,
        val offset: Int = 0,
        // Per-type filter set; only the fields applicable to [mediaType] are surfaced.
        val filters: LibraryFilters = LibraryFilters.DEFAULT,
        // Audiobooks only: GROUPED sends `collapse_collections`, so a series comes back as one row.
        val seriesGrouping: SeriesGrouping = SeriesGrouping.FLAT,
    ) {
        /** The type of the rows: the Audiobooks list shows artists while [filters] name an artist type. */
        val listedMediaType: MediaType
            get() = if (filters.audiobookArtistType != null) MediaType.ARTIST else mediaType
    }
}
