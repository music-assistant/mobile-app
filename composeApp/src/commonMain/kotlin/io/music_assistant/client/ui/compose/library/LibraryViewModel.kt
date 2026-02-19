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
import io.music_assistant.client.data.model.server.QueueOption
import io.music_assistant.client.data.model.server.ServerMediaItem
import io.music_assistant.client.data.model.server.events.MediaItemAddedEvent
import io.music_assistant.client.data.model.server.events.MediaItemDeletedEvent
import io.music_assistant.client.data.model.server.events.MediaItemUpdatedEvent
import io.music_assistant.client.settings.SettingsRepository
import io.music_assistant.client.ui.compose.common.DataState
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
) : ViewModel() {

    companion object Companion {
        private const val PAGE_SIZE = 50
    }

    enum class Tab {
        ARTISTS, ALBUMS, TRACKS, PLAYLISTS, AUDIOBOOKS, PODCASTS, RADIOS
    }

    data class TabState(
        val tab: Tab,
        val dataState: DataState<List<AppMediaItem>>,
        val isSelected: Boolean,
        val offset: Int = 0,
        val hasMore: Boolean = true,
        val isLoadingMore: Boolean = false,
        val searchQuery: String = "",
        val onlyFavorites: Boolean = false
    )

    data class State(
        val tabs: List<TabState>,
        val connectionState: SessionState,
        val showCreatePlaylistDialog: Boolean = false
    )

    private val connectionState = apiClient.sessionState

    val serverUrl =
        apiClient.sessionState.map { (it as? SessionState.Connected)?.serverInfo?.baseUrl }

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
                )
            }
        )
    )
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            connectionState.collect { connection ->
                _state.update { state -> state.copy(connectionState = connection) }
                if (connection is SessionState.Connected) {
                    // Load all tabs when connected
                    loadArtists()
                    loadAlbums()
                    loadTracks()
                    loadPlaylists()
                    loadAudiobooks()
                    loadPodcasts()
                    loadRadios()
                    // Tracks tab stays as NoData since there's no API
                    updateTabState(Tab.TRACKS, DataState.NoData())
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
                        Pair(
                            it?.searchQuery ?: "",
                            it?.onlyFavorites?.takeIf { favs -> favs })
                    }
                }
                    .distinctUntilChanged()
                    .debounce { 500 }
                    .collect {
                        when (tab) {
                            Tab.ARTISTS -> loadArtists()
                            Tab.ALBUMS -> loadAlbums()
                            Tab.TRACKS -> loadTracks()
                            Tab.PLAYLISTS -> loadPlaylists()
                            Tab.AUDIOBOOKS -> loadAudiobooks()
                            Tab.PODCASTS -> loadPodcasts()
                            Tab.RADIOS -> loadRadios()
                        }
                    }
            }
        }
    }

    fun onTabSelected(tab: Tab) {
        _state.update { s ->
            s.copy(tabs = s.tabs.map { it.copy(isSelected = it.tab == tab) })
        }
    }

    fun onSearchQueryChanged(tab: Tab, query: String) {
        _state.update { s ->
            s.copy(tabs = s.tabs.map { tabState ->
                if (tabState.tab == tab) {
                    tabState.copy(searchQuery = query)
                } else {
                    tabState
                }
            })
        }
    }

    fun onOnlyFavoritesClicked(tab: Tab) {
        _state.update { s ->
            s.copy(tabs = s.tabs.map { tabState ->
                if (tabState.tab == tab) {
                    tabState.copy(onlyFavorites = !tabState.onlyFavorites)
                } else {
                    tabState
                }
            })
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

            item.uri?.let { uri ->
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

    private fun loadArtists() {
        viewModelScope.launch {
            val tabState = _state.value.tabs.find { it.tab == Tab.ARTISTS }
            val searchQuery = tabState?.searchQuery?.takeIf { it.length >= 0 }
            val favoritesOnly = tabState?.onlyFavorites?.takeIf { it }
            updateTabState(Tab.ARTISTS, DataState.Loading())
            val result = apiClient.sendRequest(
                Request.Artist.listLibrary(
                    limit = PAGE_SIZE,
                    offset = 0,
                    search = searchQuery,
                    favorite = favoritesOnly
                )
            )
            result.resultAs<List<ServerMediaItem>>()
                ?.toAppMediaItemList()
                ?.filterIsInstance<AppMediaItem.Artist>()
                ?.let { artists ->
                    updateTabStateWithData(
                        tab = Tab.ARTISTS,
                        items = artists,
                        offset = PAGE_SIZE,
                        hasMore = artists.size >= PAGE_SIZE
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
            updateTabState(Tab.ALBUMS, DataState.Loading())
            val result = apiClient.sendRequest(
                Request.Album.listLibrary(
                    limit = PAGE_SIZE,
                    offset = 0,
                    search = searchQuery,
                    favorite = favoritesOnly
                )
            )
            result.resultAs<List<ServerMediaItem>>()
                ?.toAppMediaItemList()
                ?.filterIsInstance<AppMediaItem.Album>()
                ?.let { albums ->
                    updateTabStateWithData(
                        tab = Tab.ALBUMS,
                        items = albums,
                        offset = PAGE_SIZE,
                        hasMore = albums.size >= PAGE_SIZE
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
            updateTabState(Tab.PLAYLISTS, DataState.Loading())
            val result = apiClient.sendRequest(
                Request.Playlist.listLibrary(
                    limit = PAGE_SIZE,
                    offset = 0,
                    search = searchQuery,
                    favorite = favoritesOnly
                )
            )
            result.resultAs<List<ServerMediaItem>>()
                ?.toAppMediaItemList()
                ?.filterIsInstance<AppMediaItem.Playlist>()
                ?.let { playlists ->
                    updateTabStateWithData(
                        tab = Tab.PLAYLISTS,
                        items = playlists,
                        offset = PAGE_SIZE,
                        hasMore = playlists.size >= PAGE_SIZE
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
            updateTabState(Tab.TRACKS, DataState.Loading())
            val result = apiClient.sendRequest(
                Request.Track.list(
                    limit = PAGE_SIZE,
                    offset = 0,
                    search = searchQuery,
                    favorite = favoritesOnly
                )
            )
            result.resultAs<List<ServerMediaItem>>()
                ?.toAppMediaItemList()
                ?.filterIsInstance<AppMediaItem.Track>()
                ?.let { tracks ->
                    updateTabStateWithData(
                        tab = Tab.TRACKS,
                        items = tracks,
                        offset = PAGE_SIZE,
                        hasMore = tracks.size >= PAGE_SIZE
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
            updateTabState(Tab.PODCASTS, DataState.Loading())
            val result = apiClient.sendRequest(
                Request.Podcast.listLibrary(
                    limit = PAGE_SIZE,
                    offset = 0,
                    search = searchQuery,
                    favorite = favoritesOnly
                )
            )
            result.resultAs<List<ServerMediaItem>>()
                ?.toAppMediaItemList()
                ?.filterIsInstance<AppMediaItem.Podcast>()
                ?.let { podcasts ->
                    updateTabStateWithData(
                        tab = Tab.PODCASTS,
                        items = podcasts,
                        offset = PAGE_SIZE,
                        hasMore = podcasts.size >= PAGE_SIZE
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
            updateTabState(Tab.AUDIOBOOKS, DataState.Loading())
            val result = apiClient.sendRequest(
                Request.Audiobook.listLibrary(
                    limit = PAGE_SIZE,
                    offset = 0,
                    search = searchQuery,
                    favorite = favoritesOnly
                )
            )
            result.resultAs<List<ServerMediaItem>>()
                ?.toAppMediaItemList()
                ?.filterIsInstance<AppMediaItem.Audiobook>()
                ?.let { audiobooks ->
                    updateTabStateWithData(
                        tab = Tab.AUDIOBOOKS,
                        items = audiobooks,
                        offset = PAGE_SIZE,
                        hasMore = audiobooks.size >= PAGE_SIZE
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
            updateTabState(Tab.RADIOS, DataState.Loading())
            val result = apiClient.sendRequest(
                Request.RadioStation.listLibrary(
                    limit = PAGE_SIZE,
                    offset = 0,
                    search = searchQuery,
                    favorite = favoritesOnly
                )
            )
            result.resultAs<List<ServerMediaItem>>()
                ?.toAppMediaItemList()
                ?.filterIsInstance<AppMediaItem.RadioStation>()
                ?.let { radios ->
                    updateTabStateWithData(
                        tab = Tab.RADIOS,
                        items = radios,
                        offset = PAGE_SIZE,
                        hasMore = radios.size >= PAGE_SIZE
                    )
                } ?: run {
                Logger.e("Error loading radios:", result.exceptionOrNull())
                updateTabState(Tab.RADIOS, DataState.Error())
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

            // Mark as loading more
            _state.update { s ->
                s.copy(tabs = s.tabs.map { ts ->
                    if (ts.tab == tab) ts.copy(isLoadingMore = true) else ts
                })
            }

            val result = when (tab) {
                Tab.ARTISTS -> apiClient.sendRequest(
                    Request.Artist.listLibrary(
                        limit = PAGE_SIZE,
                        offset = tabState.offset,
                        search = searchQuery
                    )
                )

                Tab.ALBUMS -> apiClient.sendRequest(
                    Request.Album.listLibrary(
                        limit = PAGE_SIZE,
                        offset = tabState.offset,
                        search = searchQuery
                    )
                )

                Tab.TRACKS -> apiClient.sendRequest(
                    Request.Track.list(
                        limit = PAGE_SIZE,
                        offset = tabState.offset,
                        search = searchQuery
                    )
                )

                Tab.PLAYLISTS -> apiClient.sendRequest(
                    Request.Playlist.listLibrary(
                        limit = PAGE_SIZE,
                        offset = tabState.offset,
                        search = searchQuery
                    )
                )

                Tab.AUDIOBOOKS -> apiClient.sendRequest(
                    Request.Audiobook.listLibrary(
                        limit = PAGE_SIZE,
                        offset = tabState.offset,
                        search = searchQuery
                    )
                )

                Tab.PODCASTS -> apiClient.sendRequest(
                    Request.Podcast.listLibrary(
                        limit = PAGE_SIZE,
                        offset = tabState.offset,
                        search = searchQuery
                    )
                )

                Tab.RADIOS -> apiClient.sendRequest(
                    Request.RadioStation.listLibrary(
                        limit = PAGE_SIZE,
                        offset = tabState.offset,
                        search = searchQuery
                    )
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
                        hasMore = newItems.size >= PAGE_SIZE
                    )
                } ?: run {
                Logger.e("Error loading more for $tab:", result.exceptionOrNull())
                // Stop loading more on error
                _state.update { s ->
                    s.copy(tabs = s.tabs.map { ts ->
                        if (ts.tab == tab) ts.copy(isLoadingMore = false, hasMore = false) else ts
                    })
                }
            }
        }
    }

    private fun updateTabState(tab: Tab, dataState: DataState<List<AppMediaItem>>) {
        _state.update { s ->
            s.copy(tabs = s.tabs.map { tabState ->
                if (tabState.tab == tab) {
                    tabState.copy(dataState = dataState)
                } else {
                    tabState
                }
            })
        }
    }

    private fun updateTabStateWithData(
        tab: Tab,
        items: List<AppMediaItem>,
        offset: Int,
        hasMore: Boolean
    ) {
        _state.update { s ->
            s.copy(tabs = s.tabs.map { tabState ->
                if (tabState.tab == tab) {
                    tabState.copy(
                        dataState = DataState.Data(items),
                        offset = offset,
                        hasMore = hasMore,
                        isLoadingMore = false
                    )
                } else {
                    tabState
                }
            })
        }
    }

    private fun updateItemInTabs(newItem: AppMediaItem, modification: ListModification) {
        _state.update { s ->
            s.copy(tabs = s.tabs.map { tabState ->
                val shouldUpdate = when (newItem) {
                    is AppMediaItem.Artist -> tabState.tab == Tab.ARTISTS
                    is AppMediaItem.Album -> tabState.tab == Tab.ALBUMS
                    is AppMediaItem.Track -> tabState.tab == Tab.TRACKS
                    is AppMediaItem.Playlist -> tabState.tab == Tab.PLAYLISTS
                    is AppMediaItem.Audiobook -> tabState.tab == Tab.AUDIOBOOKS
                    is AppMediaItem.Podcast -> tabState.tab == Tab.PODCASTS
                    is AppMediaItem.RadioStation -> tabState.tab == Tab.RADIOS
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
            })
        }
    }

    private enum class ListModification {
        Add, Update, Delete
    }
}
