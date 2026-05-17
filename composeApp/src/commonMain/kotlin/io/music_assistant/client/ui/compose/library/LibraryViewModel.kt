package io.music_assistant.client.ui.compose.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import io.music_assistant.client.api.Request
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.data.MainDataSource
import io.music_assistant.client.data.model.client.MediaType
import io.music_assistant.client.data.model.client.QueueOption
import io.music_assistant.client.data.model.client.SortConfig
import io.music_assistant.client.data.model.client.SortOption
import io.music_assistant.client.data.model.client.items.Album
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.client.items.Artist
import io.music_assistant.client.data.model.client.items.Audiobook
import io.music_assistant.client.data.model.client.items.Genre
import io.music_assistant.client.data.model.client.items.Playlist
import io.music_assistant.client.data.model.client.items.Podcast
import io.music_assistant.client.data.model.client.items.RadioStation
import io.music_assistant.client.data.model.client.items.Track
import io.music_assistant.client.data.repository.MediaItemChange
import io.music_assistant.client.data.repository.MediaItemRepository
import io.music_assistant.client.settings.SettingsRepository
import io.music_assistant.client.settings.ViewMode
import io.music_assistant.client.ui.compose.common.DataState
import io.music_assistant.client.utils.DataConnectionState
import io.music_assistant.client.utils.SessionState
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
class LibraryViewModel(
    private val apiClient: ServiceClient,
    private val mainDataSource: MainDataSource,
    private val settingsRepository: SettingsRepository,
    private val libraryNavCoordinator: LibraryNavCoordinator,
    private val mediaItemRepository: MediaItemRepository,
) : ViewModel() {
    companion object Companion {
        private const val PAGE_SIZE = 50
        const val LIBRARY_SORT_DEBOUNCE_MS = 500L

        private fun tabFor(type: MediaType): Tab = when (type) {
            MediaType.ARTIST -> Tab.ARTISTS
            MediaType.ALBUM -> Tab.ALBUMS
            MediaType.TRACK -> Tab.TRACKS
            MediaType.PLAYLIST -> Tab.PLAYLISTS
            MediaType.AUDIOBOOK -> Tab.AUDIOBOOKS
            MediaType.PODCAST -> Tab.PODCASTS
            MediaType.RADIO -> Tab.RADIOS
            MediaType.GENRE -> Tab.GENRES
            else -> Tab.ARTISTS
        }
    }

    enum class Tab {
        ARTISTS, ALBUMS, TRACKS, PLAYLISTS, AUDIOBOOKS, PODCASTS, RADIOS, GENRES;

        val mediaType: MediaType
            get() = when (this) {
                ARTISTS -> MediaType.ARTIST
                ALBUMS -> MediaType.ALBUM
                TRACKS -> MediaType.TRACK
                PLAYLISTS -> MediaType.PLAYLIST
                AUDIOBOOKS -> MediaType.AUDIOBOOK
                PODCASTS -> MediaType.PODCAST
                RADIOS -> MediaType.RADIO
                GENRES -> MediaType.GENRE
            }
    }

    data class TabState(
        val tab: Tab,
        val dataState: DataState<List<AppMediaItem>>,
        val isSelected: Boolean,
        val enabled: Boolean = true,
        val offset: Int = 0,
        val hasMore: Boolean = true,
        val isLoadingMore: Boolean = false,
        val searchQuery: String = "",
        val onlyFavorites: Boolean = false,
        val sortOption: SortOption = SortConfig.defaultFor(tab.mediaType),
        val viewMode: ViewMode = ViewMode.GRID,
    )

    data class State(
        val tabs: List<TabState>,
        val connectionState: SessionState,
        val showCreatePlaylistDialog: Boolean = false,
    )

    private val connectionState = apiClient.sessionState

    fun toggleViewMode(tab: Tab) {
        val current = settingsRepository.viewMode(tab.mediaType).value
        settingsRepository.setViewMode(tab.mediaType, current.toggled())
    }

    private val _state = MutableStateFlow(
        State(
            connectionState = SessionState.Disconnected.Initial,
            tabs = buildInitialTabs(),
        ),
    )
    val state = _state.asStateFlow()

    private fun buildInitialTabs(): List<TabState> {
        val stored = settingsRepository.libraryTabsConfig.value
        // Reconcile: keep stored order/enabled, append any new tabs at the end (enabled).
        val storedByName = stored?.associate { it.name to it.enabled }.orEmpty()
        val orderedNames = stored?.map { it.name }?.filter { name ->
            Tab.entries.any { it.name == name }
        }.orEmpty()
        val missing = Tab.entries.map { it.name }.filter { it !in orderedNames }
        val finalOrder = (orderedNames + missing).map { name -> Tab.valueOf(name) }
        var firstEnabledAssigned = false
        return finalOrder.map { tab ->
            val enabled = storedByName[tab.name] ?: true
            val isSelected = enabled && !firstEnabledAssigned
            if (isSelected) firstEnabledAssigned = true
            TabState(
                tab = tab,
                dataState = DataState.Loading(),
                isSelected = isSelected,
                enabled = enabled,
                sortOption = settingsRepository.getSortOption(tab.mediaType),
                viewMode = settingsRepository.viewMode(tab.mediaType).value,
            )
        }
    }

    fun onTabsConfigChanged(newOrder: List<Pair<Tab, Boolean>>) {
        // Persist
        settingsRepository.setLibraryTabsConfig(
            newOrder.map { (tab, enabled) ->
                SettingsRepository.LibraryTabPref(name = tab.name, enabled = enabled)
            },
        )
        // Reorder existing TabStates and update enabled flag, preserving their data.
        _state.update { s ->
            val byTab = s.tabs.associateBy { it.tab }
            val reordered = newOrder.mapNotNull { (tab, enabled) ->
                byTab[tab]?.copy(enabled = enabled)
            }
            // If currently selected tab is now disabled, select first enabled.
            val currentSelected = reordered.find { it.isSelected }
            val needsReselect = currentSelected == null || !currentSelected.enabled
            val finalTabs = if (needsReselect) {
                val firstEnabledTab = reordered.firstOrNull { it.enabled }?.tab
                reordered.map { it.copy(isSelected = it.tab == firstEnabledTab) }
            } else {
                reordered
            }
            s.copy(tabs = finalTabs)
        }
        // Trigger load for any tab that was just enabled and has no data yet.
        _state.value.tabs.filter { it.enabled && it.dataState is DataState.Loading }
            .forEach { triggerLoad(it.tab) }
    }

    private fun triggerLoad(tab: Tab) {
        when (tab) {
            Tab.ARTISTS -> loadArtists()
            Tab.ALBUMS -> loadAlbums()
            Tab.TRACKS -> loadTracks()
            Tab.PLAYLISTS -> loadPlaylists()
            Tab.AUDIOBOOKS -> loadAudiobooks()
            Tab.PODCASTS -> loadPodcasts()
            Tab.RADIOS -> loadRadios()
            Tab.GENRES -> loadGenres()
        }
    }

    init {
        viewModelScope.launch {
            connectionState.collect { connection ->
                _state.update { state -> state.copy(connectionState = connection) }
                if (connection is SessionState.Connected &&
                    connection.dataConnectionState == DataConnectionState.Authenticated
                ) {
                    // Load only enabled tabs when authenticated.
                    _state.value.tabs.filter { it.enabled }.forEach { triggerLoad(it.tab) }
                }
            }
        }

        // Listen to real-time library changes and patch any visible tab.
        viewModelScope.launch {
            mediaItemRepository.itemChanges.collect { change ->
                val modification = when (change) {
                    is MediaItemChange.Added -> ListModification.Add
                    is MediaItemChange.Updated -> ListModification.Update
                    is MediaItemChange.Deleted -> ListModification.Delete
                }
                updateItemInTabs(change.item, modification)
            }
        }

        // Debounced search for each tab
        Tab.entries.forEach { tab ->
            viewModelScope.launch {
                _state.map { state ->
                    state.tabs.find { it.tab == tab }.let {
                        Triple(
                            it?.searchQuery ?: "",
                            it?.onlyFavorites?.takeIf { favs -> favs },
                            it?.sortOption,
                        )
                    }
                }
                    .distinctUntilChanged()
                    .debounce { LIBRARY_SORT_DEBOUNCE_MS }
                    .collect {
                        when (tab) {
                            Tab.ARTISTS -> loadArtists()
                            Tab.ALBUMS -> loadAlbums()
                            Tab.TRACKS -> loadTracks()
                            Tab.PLAYLISTS -> loadPlaylists()
                            Tab.AUDIOBOOKS -> loadAudiobooks()
                            Tab.PODCASTS -> loadPodcasts()
                            Tab.RADIOS -> loadRadios()
                            Tab.GENRES -> loadGenres()
                        }
                    }
            }
        }

        viewModelScope.launch {
            libraryNavCoordinator.tabRequests.collect { type ->
                onTabSelected(tabFor(type))
            }
        }

        // Mirror per-MediaType view mode into TabState so toggles from elsewhere
        // (e.g. ItemDetailsScreen) propagate here too.
        Tab.entries.forEach { tab ->
            viewModelScope.launch {
                settingsRepository.viewMode(tab.mediaType).collect { mode ->
                    _state.update { s ->
                        s.copy(
                            tabs = s.tabs.map { ts ->
                                if (ts.tab == tab) ts.copy(viewMode = mode) else ts
                            },
                        )
                    }
                }
            }
        }
    }

    private var initialTabApplied = false

    fun applyInitialTabIfNeeded(type: MediaType?) {
        if (initialTabApplied) return
        initialTabApplied = true
        onTabSelected(type?.let { tabFor(it) })
    }

    fun onTabSelected(tab: Tab?) {
        // If tab == null, just select first enabled tab (default behaviour on app start).
        val tabToSelect = tab ?: _state.value.tabs.firstOrNull { it.enabled }?.tab ?: return
        // If selecting a currently disabled tab (deep link / coordinator request),
        // re-enable it AND move it to the bottom of the enabled section so the
        // "enabled first, disabled last" invariant holds.
        _state.value.tabs
            .find { it.tab == tabToSelect }
            ?.takeIf { !it.enabled }
            ?.run {
                moveToEnabledBoundary(
                    _state.value.tabs.map { it.tab to it.enabled },
                    target = tabToSelect,
                    newEnabled = true,
                ).let { newOrder -> onTabsConfigChanged(newOrder) }
            }
        _state.update { s ->
            s.copy(tabs = s.tabs.map { it.copy(isSelected = it.tab == tabToSelect) })
        }
    }

    fun onSearchQueryChanged(tab: Tab, query: String) {
        _state.update { s ->
            s.copy(
                tabs = s.tabs.map { tabState ->
                    if (tabState.tab == tab) {
                        tabState.copy(searchQuery = query)
                    } else {
                        tabState
                    }
                },
            )
        }
    }

    fun onOnlyFavoritesClicked(tab: Tab) {
        _state.update { s ->
            s.copy(
                tabs = s.tabs.map { tabState ->
                    if (tabState.tab == tab) {
                        tabState.copy(onlyFavorites = !tabState.onlyFavorites)
                    } else {
                        tabState
                    }
                },
            )
        }
    }

    fun onSortChanged(tab: Tab, sortOption: SortOption) {
        settingsRepository.setSortOption(tab.mediaType, sortOption)
        _state.update { s ->
            s.copy(
                tabs = s.tabs.map { tabState ->
                    if (tabState.tab == tab) {
                        tabState.copy(sortOption = sortOption)
                    } else {
                        tabState
                    }
                },
            )
        }
    }

    fun onCreatePlaylistClick() {
        _state.update { it.copy(showCreatePlaylistDialog = true) }
    }

    fun onDismissCreatePlaylistDialog() {
        _state.update { it.copy(showCreatePlaylistDialog = false) }
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            apiClient.sendRequest(Request.Playlist.create(name))
            _state.update { it.copy(showCreatePlaylistDialog = false) }
        }
    }

    fun onPlayClick(item: AppMediaItem, option: QueueOption, radio: Boolean) {
        viewModelScope.launch {
            val queueId = mainDataSource.selectedPlayer?.queueOrPlayerId ?: return@launch

            item.mediaUri?.let { mediaUri ->
                apiClient.sendRequest(
                    Request.Library.play(
                        media = listOf(mediaUri),
                        queueOrPlayerId = queueId,
                        option = option,
                        radioMode = radio && item !is Genre,
                    ),
                )
            }
        }
    }

    private fun loadArtists() {
        viewModelScope.launch {
            val tabState = _state.value.tabs.find { it.tab == Tab.ARTISTS }
            val searchQuery = tabState?.searchQuery?.takeIf { it.length >= 0 }
            val favoritesOnly = tabState?.onlyFavorites?.takeIf { it }
            val orderBy = tabState?.sortOption?.toServerString()
            updateTabState(Tab.ARTISTS, DataState.Loading())
            val result = mediaItemRepository.fetchMediaItems(
                Request.Artist.listLibrary(
                    limit = PAGE_SIZE,
                    offset = 0,
                    search = searchQuery,
                    favorite = favoritesOnly,
                    orderBy = orderBy,
                ),
            )
            result.getOrNull()
                ?.filterIsInstance<Artist>()
                ?.let { artists ->
                    updateTabStateWithData(
                        tab = Tab.ARTISTS,
                        items = artists,
                        offset = PAGE_SIZE,
                        hasMore = artists.size >= PAGE_SIZE,
                    )
                } ?: run {
                Logger.e("Error loading artists:", result.exceptionOrNull())
                updateTabState(Tab.ARTISTS, DataState.Error())
            }
        }
    }

    private fun loadAlbums() {
        viewModelScope.launch {
            val tabState = _state.value.tabs.find { it.tab == Tab.ALBUMS }
            val searchQuery = tabState?.searchQuery?.takeIf { it.length >= 0 }
            val favoritesOnly = tabState?.onlyFavorites?.takeIf { it }
            val orderBy = tabState?.sortOption?.toServerString()
            updateTabState(Tab.ALBUMS, DataState.Loading())
            val result = mediaItemRepository.fetchMediaItems(
                Request.Album.listLibrary(
                    limit = PAGE_SIZE,
                    offset = 0,
                    search = searchQuery,
                    favorite = favoritesOnly,
                    orderBy = orderBy,
                ),
            )
            result.getOrNull()
                ?.filterIsInstance<Album>()
                ?.let { albums ->
                    updateTabStateWithData(
                        tab = Tab.ALBUMS,
                        items = albums,
                        offset = PAGE_SIZE,
                        hasMore = albums.size >= PAGE_SIZE,
                    )
                } ?: run {
                Logger.e("Error loading albums:", result.exceptionOrNull())
                updateTabState(Tab.ALBUMS, DataState.Error())
            }
        }
    }

    private fun loadPlaylists() {
        viewModelScope.launch {
            val tabState = _state.value.tabs.find { it.tab == Tab.PLAYLISTS }
            val searchQuery = tabState?.searchQuery?.takeIf { it.length >= 0 }
            val favoritesOnly = tabState?.onlyFavorites?.takeIf { it }
            val orderBy = tabState?.sortOption?.toServerString()
            updateTabState(Tab.PLAYLISTS, DataState.Loading())
            val result = mediaItemRepository.fetchMediaItems(
                Request.Playlist.listLibrary(
                    limit = PAGE_SIZE,
                    offset = 0,
                    search = searchQuery,
                    favorite = favoritesOnly,
                    orderBy = orderBy,
                ),
            )
            result.getOrNull()
                ?.filterIsInstance<Playlist>()
                ?.let { playlists ->
                    updateTabStateWithData(
                        tab = Tab.PLAYLISTS,
                        items = playlists,
                        offset = PAGE_SIZE,
                        hasMore = playlists.size >= PAGE_SIZE,
                    )
                } ?: run {
                Logger.e("Error loading playlists:", result.exceptionOrNull())
                updateTabState(Tab.PLAYLISTS, DataState.Error())
            }
        }
    }

    private fun loadTracks() {
        viewModelScope.launch {
            val tabState = _state.value.tabs.find { it.tab == Tab.TRACKS }
            val searchQuery = tabState?.searchQuery?.takeIf { it.length >= 0 }
            val favoritesOnly = tabState?.onlyFavorites?.takeIf { it }
            val orderBy = tabState?.sortOption?.toServerString()
            updateTabState(Tab.TRACKS, DataState.Loading())
            val result = mediaItemRepository.fetchMediaItems(
                Request.Track.list(
                    limit = PAGE_SIZE,
                    offset = 0,
                    search = searchQuery,
                    favorite = favoritesOnly,
                    orderBy = orderBy,
                ),
            )
            result.getOrNull()
                ?.filterIsInstance<Track>()
                ?.let { tracks ->
                    updateTabStateWithData(
                        tab = Tab.TRACKS,
                        items = tracks,
                        offset = PAGE_SIZE,
                        hasMore = tracks.size >= PAGE_SIZE,
                    )
                } ?: run {
                Logger.e("Error loading tracks:", result.exceptionOrNull())
                updateTabState(Tab.TRACKS, DataState.Error())
            }
        }
    }

    private fun loadPodcasts() {
        viewModelScope.launch {
            val tabState = _state.value.tabs.find { it.tab == Tab.PODCASTS }
            val searchQuery = tabState?.searchQuery?.takeIf { it.length >= 0 }
            val favoritesOnly = tabState?.onlyFavorites?.takeIf { it }
            val orderBy = tabState?.sortOption?.toServerString()
            updateTabState(Tab.PODCASTS, DataState.Loading())
            val result = mediaItemRepository.fetchMediaItems(
                Request.Podcast.listLibrary(
                    limit = PAGE_SIZE,
                    offset = 0,
                    search = searchQuery,
                    favorite = favoritesOnly,
                    orderBy = orderBy,
                ),
            )
            result.getOrNull()
                ?.filterIsInstance<Podcast>()
                ?.let { podcasts ->
                    updateTabStateWithData(
                        tab = Tab.PODCASTS,
                        items = podcasts,
                        offset = PAGE_SIZE,
                        hasMore = podcasts.size >= PAGE_SIZE,
                    )
                } ?: run {
                Logger.e("Error loading podcasts:", result.exceptionOrNull())
                updateTabState(Tab.PODCASTS, DataState.Error())
            }
        }
    }

    private fun loadAudiobooks() {
        viewModelScope.launch {
            val tabState = _state.value.tabs.find { it.tab == Tab.AUDIOBOOKS }
            val searchQuery = tabState?.searchQuery?.takeIf { it.length >= 0 }
            val favoritesOnly = tabState?.onlyFavorites?.takeIf { it }
            val orderBy = tabState?.sortOption?.toServerString()
            updateTabState(Tab.AUDIOBOOKS, DataState.Loading())
            val result = mediaItemRepository.fetchMediaItems(
                Request.Audiobook.listLibrary(
                    limit = PAGE_SIZE,
                    offset = 0,
                    search = searchQuery,
                    favorite = favoritesOnly,
                    orderBy = orderBy,
                ),
            )
            result.getOrNull()
                ?.filterIsInstance<Audiobook>()
                ?.let { audiobooks ->
                    updateTabStateWithData(
                        tab = Tab.AUDIOBOOKS,
                        items = audiobooks,
                        offset = PAGE_SIZE,
                        hasMore = audiobooks.size >= PAGE_SIZE,
                    )
                } ?: run {
                Logger.e("Error loading audiobooks:", result.exceptionOrNull())
                updateTabState(Tab.AUDIOBOOKS, DataState.Error())
            }
        }
    }

    private fun loadRadios() {
        viewModelScope.launch {
            val tabState = _state.value.tabs.find { it.tab == Tab.RADIOS }
            val searchQuery = tabState?.searchQuery?.takeIf { it.length >= 0 }
            val favoritesOnly = tabState?.onlyFavorites?.takeIf { it }
            val orderBy = tabState?.sortOption?.toServerString()
            updateTabState(Tab.RADIOS, DataState.Loading())
            val result = mediaItemRepository.fetchMediaItems(
                Request.RadioStation.listLibrary(
                    limit = PAGE_SIZE,
                    offset = 0,
                    search = searchQuery,
                    favorite = favoritesOnly,
                    orderBy = orderBy,
                ),
            )
            result.getOrNull()
                ?.filterIsInstance<RadioStation>()
                ?.let { radios ->
                    updateTabStateWithData(
                        tab = Tab.RADIOS,
                        items = radios,
                        offset = PAGE_SIZE,
                        hasMore = radios.size >= PAGE_SIZE,
                    )
                } ?: run {
                Logger.e("Error loading radios:", result.exceptionOrNull())
                updateTabState(Tab.RADIOS, DataState.Error())
            }
        }
    }

    private fun loadGenres() {
        viewModelScope.launch {
            val tabState = _state.value.tabs.find { it.tab == Tab.GENRES }
            val searchQuery = tabState?.searchQuery?.takeIf { it.length >= 0 }
            val favoritesOnly = tabState?.onlyFavorites?.takeIf { it }
            val orderBy = tabState?.sortOption?.toServerString()
            updateTabState(Tab.GENRES, DataState.Loading())
            val result = mediaItemRepository.fetchMediaItems(
                Request.Genre.listLibrary(
                    limit = PAGE_SIZE,
                    offset = 0,
                    search = searchQuery,
                    favorite = favoritesOnly,
                    orderBy = orderBy,
                ),
            )
            result.getOrNull()
                ?.filterIsInstance<Genre>()
                ?.let { genres ->
                    updateTabStateWithData(
                        tab = Tab.GENRES,
                        items = genres,
                        offset = PAGE_SIZE,
                        hasMore = genres.size >= PAGE_SIZE,
                    )
                } ?: run {
                Logger.e("Error loading genres:", result.exceptionOrNull())
                updateTabState(Tab.GENRES, DataState.Error())
            }
        }
    }

    fun loadMore(tab: Tab) {
        val tabState = _state.value.tabs.find { it.tab == tab } ?: return

        // Don't load if already loading, no more data, or not in Data state
        if (tabState.isLoadingMore || !tabState.hasMore || tabState.dataState !is DataState.Data) {
            return
        }

        viewModelScope.launch {
            val searchQuery = tabState.searchQuery.takeIf { it.length >= 3 }
            val favoritesOnly = tabState.onlyFavorites.takeIf { it }
            val orderBy = tabState.sortOption.toServerString()

            // Mark as loading more
            _state.update { s ->
                s.copy(
                    tabs = s.tabs.map { ts ->
                        if (ts.tab == tab) ts.copy(isLoadingMore = true) else ts
                    },
                )
            }

            val request = when (tab) {
                Tab.ARTISTS -> Request.Artist.listLibrary(
                    limit = PAGE_SIZE,
                    offset = tabState.offset,
                    search = searchQuery,
                    favorite = favoritesOnly,
                    orderBy = orderBy,
                )

                Tab.ALBUMS -> Request.Album.listLibrary(
                    limit = PAGE_SIZE,
                    offset = tabState.offset,
                    search = searchQuery,
                    favorite = favoritesOnly,
                    orderBy = orderBy,
                )

                Tab.TRACKS -> Request.Track.list(
                    limit = PAGE_SIZE,
                    offset = tabState.offset,
                    search = searchQuery,
                    favorite = favoritesOnly,
                    orderBy = orderBy,
                )

                Tab.PLAYLISTS -> Request.Playlist.listLibrary(
                    limit = PAGE_SIZE,
                    offset = tabState.offset,
                    search = searchQuery,
                    favorite = favoritesOnly,
                    orderBy = orderBy,
                )

                Tab.AUDIOBOOKS -> Request.Audiobook.listLibrary(
                    limit = PAGE_SIZE,
                    offset = tabState.offset,
                    search = searchQuery,
                    favorite = favoritesOnly,
                    orderBy = orderBy,
                )

                Tab.PODCASTS -> Request.Podcast.listLibrary(
                    limit = PAGE_SIZE,
                    offset = tabState.offset,
                    search = searchQuery,
                    favorite = favoritesOnly,
                    orderBy = orderBy,
                )

                Tab.RADIOS -> Request.RadioStation.listLibrary(
                    limit = PAGE_SIZE,
                    offset = tabState.offset,
                    search = searchQuery,
                    favorite = favoritesOnly,
                    orderBy = orderBy,
                )

                Tab.GENRES -> Request.Genre.listLibrary(
                    limit = PAGE_SIZE,
                    offset = tabState.offset,
                    search = searchQuery,
                    favorite = favoritesOnly,
                    orderBy = orderBy,
                )
            }
            val result = mediaItemRepository.fetchMediaItems(request)

            result.getOrNull()
                ?.let { newItems ->
                    val currentItems = tabState.dataState.data
                    val allItems = currentItems + newItems
                    updateTabStateWithData(
                        tab = tab,
                        items = allItems,
                        offset = tabState.offset + PAGE_SIZE,
                        hasMore = newItems.size >= PAGE_SIZE,
                    )
                } ?: run {
                Logger.e("Error loading more for $tab:", result.exceptionOrNull())
                // Stop loading more on error
                _state.update { s ->
                    s.copy(
                        tabs = s.tabs.map { ts ->
                            if (ts.tab == tab) {
                                ts.copy(
                                isLoadingMore = false,
                                hasMore = false,
                            )
                            } else {
                                ts
                            }
                        },
                    )
                }
            }
        }
    }

    private fun updateTabState(tab: Tab, dataState: DataState<List<AppMediaItem>>) {
        _state.update { s ->
            s.copy(
                tabs = s.tabs.map { tabState ->
                    if (tabState.tab == tab) {
                        tabState.copy(dataState = dataState)
                    } else {
                        tabState
                    }
                },
            )
        }
    }

    private fun updateTabStateWithData(
        tab: Tab,
        items: List<AppMediaItem>,
        offset: Int,
        hasMore: Boolean,
    ) {
        val deduped = items.distinctBy { Triple(it.mediaType, it.provider, it.itemId) }
        _state.update { s ->
            s.copy(
                tabs = s.tabs.map { tabState ->
                    if (tabState.tab == tab) {
                        tabState.copy(
                            dataState = DataState.Data(deduped),
                            offset = offset,
                            hasMore = hasMore,
                            isLoadingMore = false,
                        )
                    } else {
                        tabState
                    }
                },
            )
        }
    }

    private fun updateItemInTabs(newItem: AppMediaItem, modification: ListModification) {
        _state.update { s ->
            s.copy(
                tabs = s.tabs.map { tabState ->
                    val shouldUpdate = when (newItem) {
                        is Artist -> tabState.tab == Tab.ARTISTS
                        is Album -> tabState.tab == Tab.ALBUMS
                        is Track -> tabState.tab == Tab.TRACKS
                        is Playlist -> tabState.tab == Tab.PLAYLISTS
                        is Audiobook -> tabState.tab == Tab.AUDIOBOOKS
                        is Podcast -> tabState.tab == Tab.PODCASTS
                        is RadioStation -> tabState.tab == Tab.RADIOS
                        is Genre -> tabState.tab == Tab.GENRES
                        else -> false
                    }

                    if (shouldUpdate && tabState.dataState is DataState.Data) {
                        val currentList = tabState.dataState.data
                        val updatedList = when (modification) {
                            ListModification.Add -> {
                                if (currentList.any { it.itemId == newItem.itemId }) {
                                    currentList
                                } else {
                                    currentList + newItem
                                }
                            }

                            ListModification.Update -> {
                                currentList.map { if (it.itemId == newItem.itemId) newItem else it }
                            }

                            ListModification.Delete -> {
                                currentList.filter { it.itemId != newItem.itemId }
                            }
                        }
                        tabState.copy(dataState = DataState.Data(updatedList))
                    } else {
                        tabState
                    }
                },
            )
        }
    }

    private enum class ListModification {
        Add, Update, Delete
    }
}
