package io.music_assistant.client.ui.compose.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import io.music_assistant.client.api.Request
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.data.MainDataSource
import io.music_assistant.client.data.model.client.AppMediaItem
import io.music_assistant.client.data.model.client.AppMediaItem.Companion.toAppMediaItem
import io.music_assistant.client.data.model.client.AppMediaItem.Companion.toAppMediaItemList
import io.music_assistant.client.data.model.client.SortConfig
import io.music_assistant.client.data.model.client.SortOption
import io.music_assistant.client.data.model.server.MediaType
import io.music_assistant.client.data.model.server.QueueOption
import io.music_assistant.client.data.model.server.ServerMediaItem
import io.music_assistant.client.data.model.server.events.MediaItemAddedEvent
import io.music_assistant.client.data.model.server.events.MediaItemDeletedEvent
import io.music_assistant.client.data.model.server.events.MediaItemUpdatedEvent
import io.music_assistant.client.settings.SettingsRepository
import io.music_assistant.client.ui.compose.common.DataState
import io.music_assistant.client.utils.DataConnectionState
import io.music_assistant.client.utils.SessionState
import io.music_assistant.client.utils.resultAs
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
) : ViewModel() {
    companion object Companion {
        private const val PAGE_SIZE = 50
        const val LIBRARY_SORT_DEBOUNCE_MS = 500L

        fun tabFor(type: MediaType?): Tab = when (type) {
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
        val offset: Int = 0,
        val hasMore: Boolean = true,
        val isLoadingMore: Boolean = false,
        val searchQuery: String = "",
        val onlyFavorites: Boolean = false,
        val sortOption: SortOption = SortConfig.defaultFor(tab.mediaType),
    )

    data class State(
        val tabs: List<TabState>,
        val connectionState: SessionState,
        val showCreatePlaylistDialog: Boolean = false,
    )

    private val connectionState = apiClient.sessionState

    val serverUrl = apiClient.serverBaseUrl

    val itemsRowMode = settingsRepository.itemsRowMode

    fun toggleItemsRowMode() {
        settingsRepository.setItemsRowMode(!settingsRepository.itemsRowMode.value)
    }

    private val _state = MutableStateFlow(
        State(
            connectionState = SessionState.Disconnected.Initial,
            tabs = Tab.entries.map { tab ->
                TabState(
                    tab = tab,
                    dataState = DataState.Loading(),
                    isSelected = tab == Tab.ARTISTS,
                    sortOption = settingsRepository.getSortOption(tab.mediaType),
                )
            },
        ),
    )
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            connectionState.collect { connection ->
                _state.update { state -> state.copy(connectionState = connection) }
                if (connection is SessionState.Connected &&
                    connection.dataConnectionState == DataConnectionState.Authenticated
                ) {
                    // Load all tabs when authenticated
                    loadArtists()
                    loadAlbums()
                    loadTracks()
                    loadPlaylists()
                    loadAudiobooks()
                    loadPodcasts()
                    loadRadios()
                    loadGenres()
                }
            }
        }

        // Listen to real-time events for updates
        viewModelScope.launch {
            apiClient.events.collect { event ->
                when (event) {
                    is MediaItemUpdatedEvent -> event.data.toAppMediaItem()?.let { newItem ->
                        updateItemInTabs(newItem, ListModification.Update)
                    }

                    is MediaItemAddedEvent -> event.data.toAppMediaItem()?.let { newItem ->
                        updateItemInTabs(newItem, ListModification.Add)
                    }

                    is MediaItemDeletedEvent -> event.data.toAppMediaItem()?.let { newItem ->
                        updateItemInTabs(newItem, ListModification.Delete)
                    }

                    else -> Unit
                }
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
    }

    private var initialTabApplied = false

    fun applyInitialTabIfNeeded(tab: Tab) {
        if (initialTabApplied) return
        initialTabApplied = true
        onTabSelected(tab)
    }

    fun onTabSelected(tab: Tab) {
        _state.update { s ->
            s.copy(tabs = s.tabs.map { it.copy(isSelected = it.tab == tab) })
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
                        radioMode = radio && item !is AppMediaItem.Genre,
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
            val result = apiClient.sendRequest(
                Request.Artist.listLibrary(
                    limit = PAGE_SIZE,
                    offset = 0,
                    search = searchQuery,
                    favorite = favoritesOnly,
                    orderBy = orderBy,
                ),
            )
            result.resultAs<List<ServerMediaItem>>()
                ?.toAppMediaItemList()
                ?.filterIsInstance<AppMediaItem.Artist>()
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
            val result = apiClient.sendRequest(
                Request.Album.listLibrary(
                    limit = PAGE_SIZE,
                    offset = 0,
                    search = searchQuery,
                    favorite = favoritesOnly,
                    orderBy = orderBy,
                ),
            )
            result.resultAs<List<ServerMediaItem>>()
                ?.toAppMediaItemList()
                ?.filterIsInstance<AppMediaItem.Album>()
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
            val result = apiClient.sendRequest(
                Request.Playlist.listLibrary(
                    limit = PAGE_SIZE,
                    offset = 0,
                    search = searchQuery,
                    favorite = favoritesOnly,
                    orderBy = orderBy,
                ),
            )
            result.resultAs<List<ServerMediaItem>>()
                ?.toAppMediaItemList()
                ?.filterIsInstance<AppMediaItem.Playlist>()
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
            val result = apiClient.sendRequest(
                Request.Track.list(
                    limit = PAGE_SIZE,
                    offset = 0,
                    search = searchQuery,
                    favorite = favoritesOnly,
                    orderBy = orderBy,
                ),
            )
            result.resultAs<List<ServerMediaItem>>()
                ?.toAppMediaItemList()
                ?.filterIsInstance<AppMediaItem.Track>()
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
            val result = apiClient.sendRequest(
                Request.Podcast.listLibrary(
                    limit = PAGE_SIZE,
                    offset = 0,
                    search = searchQuery,
                    favorite = favoritesOnly,
                    orderBy = orderBy,
                ),
            )
            result.resultAs<List<ServerMediaItem>>()
                ?.toAppMediaItemList()
                ?.filterIsInstance<AppMediaItem.Podcast>()
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
            val result = apiClient.sendRequest(
                Request.Audiobook.listLibrary(
                    limit = PAGE_SIZE,
                    offset = 0,
                    search = searchQuery,
                    favorite = favoritesOnly,
                    orderBy = orderBy,
                ),
            )
            result.resultAs<List<ServerMediaItem>>()
                ?.toAppMediaItemList()
                ?.filterIsInstance<AppMediaItem.Audiobook>()
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
            val result = apiClient.sendRequest(
                Request.RadioStation.listLibrary(
                    limit = PAGE_SIZE,
                    offset = 0,
                    search = searchQuery,
                    favorite = favoritesOnly,
                    orderBy = orderBy,
                ),
            )
            result.resultAs<List<ServerMediaItem>>()
                ?.toAppMediaItemList()
                ?.filterIsInstance<AppMediaItem.RadioStation>()
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
            val result = apiClient.sendRequest(
                Request.Genre.listLibrary(
                    limit = PAGE_SIZE,
                    offset = 0,
                    search = searchQuery,
                    favorite = favoritesOnly,
                    orderBy = orderBy,
                ),
            )
            result.resultAs<List<ServerMediaItem>>()
                ?.toAppMediaItemList()
                ?.filterIsInstance<AppMediaItem.Genre>()
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

            val result = when (tab) {
                Tab.ARTISTS -> apiClient.sendRequest(
                    Request.Artist.listLibrary(
                        limit = PAGE_SIZE,
                        offset = tabState.offset,
                        search = searchQuery,
                        favorite = favoritesOnly,
                        orderBy = orderBy,
                    ),
                )

                Tab.ALBUMS -> apiClient.sendRequest(
                    Request.Album.listLibrary(
                        limit = PAGE_SIZE,
                        offset = tabState.offset,
                        search = searchQuery,
                        favorite = favoritesOnly,
                        orderBy = orderBy,
                    ),
                )

                Tab.TRACKS -> apiClient.sendRequest(
                    Request.Track.list(
                        limit = PAGE_SIZE,
                        offset = tabState.offset,
                        search = searchQuery,
                        favorite = favoritesOnly,
                        orderBy = orderBy,
                    ),
                )

                Tab.PLAYLISTS -> apiClient.sendRequest(
                    Request.Playlist.listLibrary(
                        limit = PAGE_SIZE,
                        offset = tabState.offset,
                        search = searchQuery,
                        favorite = favoritesOnly,
                        orderBy = orderBy,
                    ),
                )

                Tab.AUDIOBOOKS -> apiClient.sendRequest(
                    Request.Audiobook.listLibrary(
                        limit = PAGE_SIZE,
                        offset = tabState.offset,
                        search = searchQuery,
                        favorite = favoritesOnly,
                        orderBy = orderBy,
                    ),
                )

                Tab.PODCASTS -> apiClient.sendRequest(
                    Request.Podcast.listLibrary(
                        limit = PAGE_SIZE,
                        offset = tabState.offset,
                        search = searchQuery,
                        favorite = favoritesOnly,
                        orderBy = orderBy,
                    ),
                )

                Tab.RADIOS -> apiClient.sendRequest(
                    Request.RadioStation.listLibrary(
                        limit = PAGE_SIZE,
                        offset = tabState.offset,
                        search = searchQuery,
                        favorite = favoritesOnly,
                        orderBy = orderBy,
                    ),
                )

                Tab.GENRES -> apiClient.sendRequest(
                    Request.Genre.listLibrary(
                        limit = PAGE_SIZE,
                        offset = tabState.offset,
                        search = searchQuery,
                        favorite = favoritesOnly,
                        orderBy = orderBy,
                    ),
                )
            }

            result.resultAs<List<ServerMediaItem>>()
                ?.toAppMediaItemList()
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
                        if (ts.tab == tab) ts.copy(isLoadingMore = false, hasMore = false) else ts
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
        _state.update { s ->
            s.copy(
                tabs = s.tabs.map { tabState ->
                if (tabState.tab == tab) {
                    tabState.copy(
                        dataState = DataState.Data(items),
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
                    is AppMediaItem.Artist -> tabState.tab == Tab.ARTISTS
                    is AppMediaItem.Album -> tabState.tab == Tab.ALBUMS
                    is AppMediaItem.Track -> tabState.tab == Tab.TRACKS
                    is AppMediaItem.Playlist -> tabState.tab == Tab.PLAYLISTS
                    is AppMediaItem.Audiobook -> tabState.tab == Tab.AUDIOBOOKS
                    is AppMediaItem.Podcast -> tabState.tab == Tab.PODCASTS
                    is AppMediaItem.RadioStation -> tabState.tab == Tab.RADIOS
                    is AppMediaItem.Genre -> tabState.tab == Tab.GENRES
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
