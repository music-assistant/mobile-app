package io.music_assistant.client.ui.compose.item

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
import io.music_assistant.client.data.model.client.SubItemContext
import io.music_assistant.client.data.model.client.clientSorted
import io.music_assistant.client.data.model.client.items.Album
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.client.items.Artist
import io.music_assistant.client.data.model.client.items.Audiobook
import io.music_assistant.client.data.model.client.items.Genre
import io.music_assistant.client.data.model.client.items.PlayableItem
import io.music_assistant.client.data.model.client.items.Playlist
import io.music_assistant.client.data.model.client.items.Podcast
import io.music_assistant.client.data.model.client.items.PodcastEpisode
import io.music_assistant.client.data.model.client.items.RecommendationFolder
import io.music_assistant.client.data.model.client.items.Track
import io.music_assistant.client.data.repository.MediaItemRepository
import io.music_assistant.client.settings.SettingsRepository
import io.music_assistant.client.ui.compose.common.DataState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ItemDetailsViewModel(
    private val apiClient: ServiceClient,
    private val mainDataSource: MainDataSource,
    private val settingsRepository: SettingsRepository,
    private val mediaItemRepository: MediaItemRepository,
    private val itemId: String,
    private val mediaType: MediaType,
    private val providerId: String,
) : ViewModel() {
    data class State(
        val itemState: DataState<AppMediaItem>,
        val albumsState: DataState<List<Album>>,
        val playableItemsState: DataState<List<PlayableItem>>,
        val artistsState: DataState<List<Artist>> = DataState.Loading(),
        val albumsSortOption: SortOption? = null,
        val playableItemsSortOption: SortOption? = null,
        /** The user's manual tab choice; null means "follow the auto-selected default". */
        val userSelectedTab: ItemDetailsTab? = null,
    ) {
        /** Tabs derive purely from the loaded item, so there's nothing to store or keep in sync. */
        val tabs: List<ItemDetailsTab> get() = itemOrNull()?.let { tabsFor(it) } ?: emptyList()

        /** While any visible tab's backing list is still loading, defer the tabs bar entirely. */
        val subItemsLoading: Boolean get() = tabs.any { it.subState(this) is DataState.Loading }

        /**
         * The tab to display: none while loading; the user's pick if still valid; otherwise the
         * first tab that actually has data (so an artist with no albums but tracks opens on
         * Tracks), falling back to the first tab when everything is empty.
         */
        val selectedTab: ItemDetailsTab? get() = when {
            subItemsLoading -> null
            userSelectedTab in tabs -> userSelectedTab
            else -> tabs.firstOrNull { it.subState(this).hasItems() } ?: tabs.firstOrNull()
        }
    }

    private var rawAlbums: List<Album> = emptyList()
    private var rawPlayableItems: List<PlayableItem> = emptyList()

    private val _toasts = MutableSharedFlow<String>()
    val toasts = _toasts.asSharedFlow()

    fun viewMode(mediaType: MediaType) = settingsRepository.viewMode(mediaType)

    fun toggleViewMode(mediaType: MediaType) {
        val current = settingsRepository.viewMode(mediaType).value
        settingsRepository.setViewMode(mediaType, current.toggled())
    }

    fun onTabSelected(tab: ItemDetailsTab) {
        _state.update { it.copy(userSelectedTab = tab) }
    }

    private val _state = MutableStateFlow(
        State(
            itemState = DataState.Loading(),
            albumsState = DataState.Loading(),
            playableItemsState = DataState.Loading(),
        ),
    )
    val state = _state.asStateFlow()

    init {
        // Listen to library changes; refresh the open item + its sub-lists.
        // The repository already handles the library-fallback re-keying that
        // used to live here for delete events.
        viewModelScope.launch {
            mediaItemRepository.itemChanges.collect { change ->
                val updated = change.item
                (_state.value.itemState as? DataState.Data)?.data?.let { current ->
                    if (current.hasAnyMappingFrom(updated)) {
                        _state.update { it.copy(itemState = DataState.Data(updated)) }
                    }
                }
                updateSubItemIfNeeded(updated)
            }
        }

        loadItem()
    }

    private fun loadItem() {
        viewModelScope.launch {
            _state.update { it.copy(itemState = DataState.Loading()) }

            try {
                val item = getItemById(itemId, mediaType, providerId)
                if (item != null) {
                    _state.update { it.copy(itemState = DataState.Data(item)) }
                    loadSubItems(item)
                } else {
                    _state.update { it.copy(itemState = DataState.Error()) }
                }
            } catch (e: Exception) {
                Logger.e("Failed to load item", e)
                _state.update { it.copy(itemState = DataState.Error()) }
            }
        }
    }

    private suspend fun getItemById(
        itemId: String,
        mediaType: MediaType,
        providerId: String,
    ): AppMediaItem? {
        val request = when (mediaType) {
            MediaType.ARTIST -> Request.Artist.get(itemId, providerId)
            MediaType.ALBUM -> Request.Album.get(itemId, providerId)
            MediaType.PLAYLIST -> Request.Playlist.get(itemId, providerId)
            MediaType.PODCAST -> Request.Podcast.get(itemId, providerId)
            MediaType.AUDIOBOOK -> Request.Audiobook.get(itemId, providerId)
            MediaType.GENRE -> Request.Genre.get(itemId, providerId)
            else -> return null
        }

        return mediaItemRepository.fetchMediaItem(request).getOrNull()
    }

    private fun loadSubItems(item: AppMediaItem) {
        when (item) {
            is Artist -> {
                _state.update {
                    it.copy(
                        albumsSortOption = settingsRepository.getSortOption(SubItemContext.ARTIST_ALBUMS),
                        playableItemsSortOption = settingsRepository.getSortOption(SubItemContext.ARTIST_TRACKS),
                    )
                }
                loadArtistAlbums(item.itemId, item.provider)
                loadArtistTracks(item.itemId, item.provider)
            }

            is Album -> {
                _state.update {
                    it.copy(
                        albumsState = DataState.NoData(),
                        albumsSortOption = null,
                        playableItemsSortOption = settingsRepository.getSortOption(SubItemContext.ALBUM_TRACKS),
                    )
                }
                loadAlbumTracks(item.itemId, item.provider)
            }

            is Playlist -> {
                _state.update {
                    it.copy(
                        albumsState = DataState.NoData(),
                        albumsSortOption = null,
                        playableItemsSortOption = settingsRepository.getSortOption(SubItemContext.PLAYLIST_TRACKS),
                    )
                }
                loadPlaylistTracks(item.itemId, item.provider)
            }

            is Podcast -> {
                _state.update {
                    it.copy(
                        albumsState = DataState.NoData(),
                        albumsSortOption = null,
                        playableItemsSortOption = settingsRepository.getSortOption(SubItemContext.PODCAST_EPISODES),
                    )
                }
                loadPodcastEpisodes(item.itemId, item.provider)
            }

            is Genre -> {
                _state.update {
                    it.copy(
                        playableItemsState = DataState.NoData(),
                        albumsSortOption = null,
                        playableItemsSortOption = null,
                    )
                }
                loadGenreOverview(item.itemId, item.provider)
            }

            is Audiobook -> {
                _state.update {
                    it.copy(
                        albumsState = DataState.NoData(),
                        albumsSortOption = null,
                        playableItemsState = DataState.NoData(),
                        playableItemsSortOption = null,
                    )
                }
            }

            else -> {
                _state.update {
                    it.copy(
                        artistsState = DataState.NoData(),
                        albumsState = DataState.NoData(),
                        playableItemsState = DataState.NoData(),
                        albumsSortOption = null,
                        playableItemsSortOption = null,
                    )
                }
            }
        }
    }

    private fun loadArtistAlbums(itemId: String, providerDomain: String) {
        viewModelScope.launch {
            _state.update { it.copy(albumsState = DataState.Loading()) }

            try {
                val albums = mediaItemRepository.fetchMediaItems(
                    Request.Artist.getAlbums(
                        itemId = itemId,
                        providerInstanceIdOrDomain = providerDomain,
                        inLibraryOnly = false,
                    ),
                ).getOrNull()
                    ?.filterIsInstance<Album>()
                    ?: emptyList()

                rawAlbums = albums
                val sort = _state.value.albumsSortOption ?: SortConfig.defaultFor(SubItemContext.ARTIST_ALBUMS)
                _state.update { it.copy(albumsState = DataState.Data(albums.clientSorted(sort))) }
            } catch (e: Exception) {
                Logger.e("Failed to load artist albums", e)
                _state.update { it.copy(albumsState = DataState.Error()) }
            }
        }
    }

    private fun loadArtistTracks(itemId: String, providerDomain: String) {
        viewModelScope.launch {
            _state.update { it.copy(playableItemsState = DataState.Loading()) }

            try {
                val tracks = mediaItemRepository.fetchMediaItems(
                    Request.Artist.getTracks(
                        itemId = itemId,
                        providerInstanceIdOrDomain = providerDomain,
                        inLibraryOnly = false,
                    ),
                ).getOrNull()
                    ?.filterIsInstance<Track>()
                    ?: emptyList()

                rawPlayableItems = tracks
                val sort = _state.value.playableItemsSortOption ?: SortConfig.defaultFor(SubItemContext.ARTIST_TRACKS)
                _state.update { it.copy(playableItemsState = DataState.Data(tracks.clientSorted(sort))) }
            } catch (e: Exception) {
                Logger.e("Failed to load artist tracks", e)
                _state.update { it.copy(playableItemsState = DataState.Error()) }
            }
        }
    }

    private fun loadAlbumTracks(itemId: String, provider: String) {
        viewModelScope.launch {
            _state.update { it.copy(playableItemsState = DataState.Loading()) }

            try {
                val tracks = mediaItemRepository.fetchMediaItems(
                    Request.Album.getTracks(
                        itemId = itemId,
                        providerInstanceIdOrDomain = provider,
                        inLibraryOnly = false,
                    ),
                ).getOrNull()
                    ?.filterIsInstance<Track>()
                    ?: emptyList()

                rawPlayableItems = tracks
                val sort = _state.value.playableItemsSortOption ?: SortConfig.defaultFor(SubItemContext.ALBUM_TRACKS)
                _state.update {
                    it.copy(playableItemsState = DataState.Data(tracks.clientSorted(sort, SubItemContext.ALBUM_TRACKS)))
                }
            } catch (e: Exception) {
                Logger.e("Failed to load album tracks", e)
                _state.update { it.copy(playableItemsState = DataState.Error()) }
            }
        }
    }

    private fun loadPlaylistTracks(itemId: String, provider: String) {
        viewModelScope.launch {
            _state.update { it.copy(playableItemsState = DataState.Loading()) }

            try {
                val tracks = mediaItemRepository.fetchMediaItems(
                    Request.Playlist.getTracks(
                        itemId = itemId,
                        providerInstanceIdOrDomain = provider,
                        forceRefresh = null,
                    ),
                ).getOrNull()
                    ?.filterIsInstance<Track>()
                    ?: emptyList()

                rawPlayableItems = tracks
                val sort = _state.value.playableItemsSortOption ?: SortConfig.defaultFor(SubItemContext.PLAYLIST_TRACKS)
                _state.update {
                    it.copy(
                        playableItemsState = DataState.Data(tracks.clientSorted(sort, SubItemContext.PLAYLIST_TRACKS)),
                    )
                }
            } catch (e: Exception) {
                Logger.e("Failed to load playlist tracks", e)
                _state.update { it.copy(playableItemsState = DataState.Error()) }
            }
        }
    }

    private fun loadPodcastEpisodes(itemId: String, provider: String) {
        viewModelScope.launch {
            _state.update { it.copy(playableItemsState = DataState.Loading()) }

            try {
                val episodes = mediaItemRepository.fetchMediaItems(
                    Request.Podcast.getEpisodes(
                        itemId = itemId,
                        providerInstanceIdOrDomain = provider,
                        inLibraryOnly = false,
                    ),
                ).getOrNull()
                    ?.filterIsInstance<PodcastEpisode>()
                    ?: emptyList()

                rawPlayableItems = episodes
                val sort = _state.value.playableItemsSortOption ?: SortConfig.defaultFor(SubItemContext.PODCAST_EPISODES)
                _state.update { it.copy(playableItemsState = DataState.Data(episodes.clientSorted(sort))) }
            } catch (e: Exception) {
                Logger.e("Failed to load podcast episodes", e)
                _state.update { it.copy(playableItemsState = DataState.Error()) }
            }
        }
    }

    private fun loadGenreOverview(itemId: String, provider: String) {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    artistsState = DataState.Loading(),
                    albumsState = DataState.Loading(),
                )
            }

            try {
                val folders = mediaItemRepository.fetchMediaItems(
                    Request.Genre.overview(
                        itemId = itemId,
                        providerInstanceIdOrDomain = provider,
                    ),
                ).getOrNull()
                    ?.filterIsInstance<RecommendationFolder>()
                    ?: emptyList()

                val allItems = folders.flatMap { it.items.orEmpty() }
                val artists = allItems.filterIsInstance<Artist>()
                val albums = allItems.filterIsInstance<Album>()

                _state.update {
                    it.copy(
                        artistsState = DataState.Data(artists),
                        albumsState = DataState.Data(albums),
                    )
                }
            } catch (e: Exception) {
                Logger.e("Failed to load genre overview", e)
                _state.update {
                    it.copy(
                        artistsState = DataState.Error(),
                        albumsState = DataState.Error(),
                    )
                }
            }
        }
    }

    fun onPlayClick(option: QueueOption, radio: Boolean) {
        (_state.value.itemState as? DataState.Data)?.data?.let {
            onPlayClick(it, option, radio, false)
        }
    }

    fun onPlayClick(
        item: AppMediaItem,
        option: QueueOption,
        radio: Boolean,
        fromHereInParent: Boolean,
    ) {
        val parent = (_state.value.itemState as? DataState.Data)?.data
        val (itemToPlay, startItem) = if (fromHereInParent && parent != null) {
            Pair(parent, item)
        } else {
            Pair(item, null)
        }

        viewModelScope.launch {
            val mediaUri = itemToPlay.mediaUri ?: return@launch
            mainDataSource.selectedPlayer?.queueOrPlayerId?.let { queueId ->
                apiClient.sendRequest(
                    Request.Library.play(
                        media = listOf(mediaUri),
                        queueOrPlayerId = queueId,
                        option = option,
                        radioMode = radio && item !is Genre,
                        startItem = startItem?.itemId,
                    ),
                )
            }
        }
    }

    fun onChapterClick(chapterPosition: Int) {
        (_state.value.itemState as? DataState.Data)?.data?.let { item ->
            viewModelScope.launch {
                item.uri?.let { uri ->
                    mainDataSource.selectedPlayer?.queueOrPlayerId?.let { queueId ->
                        apiClient.sendRequest(
                            Request.Library.play(
                                media = listOf(uri),
                                queueOrPlayerId = queueId,
                                option = QueueOption.REPLACE,
                                radioMode = false,
                                startItem = chapterPosition.toString(),
                            ),
                        )
                    }
                }
            }
        }
    }

    fun onAlbumsSortChanged(context: SubItemContext, sortOption: SortOption) {
        settingsRepository.setSortOption(context, sortOption)
        _state.update {
            it.copy(
                albumsSortOption = sortOption,
                albumsState = DataState.Data(rawAlbums.clientSorted(sortOption)),
            )
        }
    }

    fun onPlayableItemsSortChanged(context: SubItemContext, sortOption: SortOption) {
        settingsRepository.setSortOption(context, sortOption)
        _state.update {
            it.copy(
                playableItemsSortOption = sortOption,
                playableItemsState = DataState.Data(rawPlayableItems.clientSorted(sortOption, context)),
            )
        }
    }

    fun reload() {
        (state.value.itemState as? DataState.Data)?.data?.let {
            loadSubItems(it)
        }
    }

    private fun updateSubItemIfNeeded(changed: AppMediaItem) {
        when (changed) {
            is Artist -> {
                val artistsData = (_state.value.artistsState as? DataState.Data)?.data ?: return
                val updated = artistsData.map { if (it.itemId == changed.itemId) changed else it }
                _state.update { it.copy(artistsState = DataState.Data(updated)) }
            }

            is Album -> {
                val albumsData = (_state.value.albumsState as? DataState.Data)?.data ?: return
                val updated = albumsData.map { if (it.itemId == changed.itemId) changed else it }
                rawAlbums = rawAlbums.map { if (it.itemId == changed.itemId) changed else it }
                _state.update { it.copy(albumsState = DataState.Data(updated)) }
            }

            is PlayableItem -> {
                val tracksData = (_state.value.playableItemsState as? DataState.Data)?.data ?: return
                val updated = tracksData.map { existing ->
                    if (existing.itemId == changed.itemId) changed else existing
                }
                rawPlayableItems = rawPlayableItems.map { existing ->
                    if (existing.itemId == changed.itemId) changed else existing
                }
                _state.update { it.copy(playableItemsState = DataState.Data(updated)) }
            }

            else -> Unit
        }
    }
}

private fun ItemDetailsViewModel.State.itemOrNull(): AppMediaItem? = when (itemState) {
    is DataState.Data -> itemState.data
    is DataState.Stale -> itemState.data
    else -> null
}

/** The single [DataState] backing this tab's list. Chapters are carried by the item itself. */
private fun ItemDetailsTab.subState(
    state: ItemDetailsViewModel.State,
): DataState<out List<Any>> = when (this) {
    ItemDetailsTab.ARTIST_ALBUMS, ItemDetailsTab.GENRE_ALBUMS -> state.albumsState
    ItemDetailsTab.ARTIST_TRACKS,
    ItemDetailsTab.ALBUM_TRACKS,
    ItemDetailsTab.PLAYLIST_TRACKS,
    ItemDetailsTab.PODCAST_EPISODES,
        -> state.playableItemsState

    ItemDetailsTab.GENRE_ARTISTS -> state.artistsState
    ItemDetailsTab.AUDIOBOOK_CHAPTERS ->
        DataState.Data((state.itemOrNull() as? Audiobook)?.chapters.orEmpty())
}

private fun DataState<out List<*>>.hasItems(): Boolean = when (this) {
    is DataState.Data -> data.isNotEmpty()
    is DataState.Stale -> data.isNotEmpty()
    else -> false
}
