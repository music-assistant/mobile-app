package io.music_assistant.client.ui.compose.item

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import io.music_assistant.client.api.Request
import io.music_assistant.client.auth.AuthCoordinator
import io.music_assistant.client.auth.AuthState
import io.music_assistant.client.data.MainDataSource
import io.music_assistant.client.data.model.client.Lyrics
import io.music_assistant.client.data.model.client.MediaType
import io.music_assistant.client.data.model.client.QueueOption
import io.music_assistant.client.data.model.client.SortConfig
import io.music_assistant.client.data.model.client.SortOption
import io.music_assistant.client.data.model.client.SubItemContext
import io.music_assistant.client.data.model.client.clientFiltered
import io.music_assistant.client.data.model.client.clientSorted
import io.music_assistant.client.data.model.client.items.Album
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.client.items.Artist
import io.music_assistant.client.data.model.client.items.Audiobook
import io.music_assistant.client.data.model.client.items.Genre
import io.music_assistant.client.data.model.client.items.MediaCollection
import io.music_assistant.client.data.model.client.items.PlayableItem
import io.music_assistant.client.data.model.client.items.Playlist
import io.music_assistant.client.data.model.client.items.PlaylistOwnerLine
import io.music_assistant.client.data.model.client.items.Podcast
import io.music_assistant.client.data.model.client.items.PodcastEpisode
import io.music_assistant.client.data.model.client.items.RadioStation
import io.music_assistant.client.data.model.client.items.RecommendationFolder
import io.music_assistant.client.data.model.client.items.RefreshItemAction
import io.music_assistant.client.data.model.client.items.Track
import io.music_assistant.client.data.model.client.items.ownerLine
import io.music_assistant.client.data.model.client.items.refreshItemAction
import io.music_assistant.client.data.play.PlayDispatcher
import io.music_assistant.client.data.play.PlayMode
import io.music_assistant.client.data.play.PlayRequest
import io.music_assistant.client.data.repository.ItemRefresher
import io.music_assistant.client.data.repository.MediaItemChange
import io.music_assistant.client.data.repository.MediaItemRepository
import io.music_assistant.client.data.repository.ProviderRepository
import io.music_assistant.client.data.repository.TranscriptRepository
import io.music_assistant.client.data.repository.favoriteAfter
import io.music_assistant.client.settings.PlayedFilter
import io.music_assistant.client.settings.SettingsRepository
import io.music_assistant.client.ui.compose.common.DataState
import io.music_assistant.client.ui.compose.common.map
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class ItemDetailsViewModel(
    private val playDispatcher: PlayDispatcher,
    private val mainDataSource: MainDataSource,
    private val settingsRepository: SettingsRepository,
    private val mediaItemRepository: MediaItemRepository,
    private val providerRepository: ProviderRepository,
    private val itemRefresher: ItemRefresher,
    private val authCoordinator: AuthCoordinator,
    private val transcriptRepository: TranscriptRepository,
    private val itemId: String,
    private val mediaType: MediaType,
    private val providerId: String,
) : ViewModel() {
    data class State(
        val itemState: DataState<AppMediaItem>,
        val albumsState: DataState<List<Album>>,
        val playableItemsState: DataState<List<PlayableItem>>,
        val artistsState: DataState<List<Artist>> = DataState.Loading(),
        /** Lazily loaded on demand from the artist overflow menu; NoData until then. */
        val similarArtistsState: DataState<List<Artist>> = DataState.NoData(),
        val trackVersionsState: DataState<List<Track>> = DataState.NoData(),
        val radioVersionsState: DataState<List<RadioStation>> = DataState.NoData(),
        /** NoData while no provider has [SIMILAR_TRACKS_FEATURE]: then the tab is not shown. */
        val similarTracksState: DataState<List<Track>> = DataState.NoData(),
        /** The lyrics of the loaded [Track] or the transcript of the loaded [PodcastEpisode]; null without one. */
        val lyrics: Lyrics? = null,
        val albumsSortOption: SortOption? = null,
        val playableItemsSortOption: SortOption? = null,
        /** Podcast only: whether the episodes tab hides the episodes played to the end. */
        val episodesPlayedFilter: PlayedFilter = PlayedFilter.ALL,
        /** In-list text filter; null while the search field is closed, "" once opened. */
        val playableItemsQuery: String? = null,
        /** The user's manual tab choice; null means "follow the auto-selected default". */
        val userSelectedTab: ItemDetailsTab? = null,
        val radioTracksSupported: Boolean = false,
        /** True while `music/refresh_item` runs for the item, from this page or an earlier one. */
        val refreshingItem: Boolean = false,
        /** The library genres of a library artist or album; empty for every other item. */
        val genres: List<Genre> = emptyList(),
        /** The owner line of a playlist; null for every other item and for a playlist without one. */
        val playlistOwnerLine: PlaylistOwnerLine? = null,
    ) {
        /**
         * The count and the total playing time of the loaded tracks of an album or a playlist, for
         * the facts line of the header. Null while they load, for an empty list, and for a type
         * without a fixed track list: a dynamic playlist yields its tracks on demand.
         */
        val playableSummary: PlayableSummary?
            get() = when (val item = itemOrNull()) {
                is Album -> playableItemsState
                is Playlist -> playableItemsState.takeUnless { item.isDynamic }
                else -> null
            }?.dataOrNull?.takeIf { it.isNotEmpty() }
                ?.let { items -> PlayableSummary(items.size, items.sumOf { it.duration ?: 0.0 }) }

        /** Tabs derive purely from the loaded item, so there's nothing to store or keep in sync. */
        val tabs: List<ItemDetailsTab>
            get() = itemOrNull()?.let { item ->
                tabsFor(item).filterNot { tab ->
                    when (tab) {
                        ItemDetailsTab.SIMILAR_TRACKS ->
                            similarTracksState is DataState.NoData

                        ItemDetailsTab.RADIO_TRACKS -> {
                            val radio = item as? RadioStation
                            radio == null ||
                                    !radioTracksSupported ||
                                    radio.isEndless != false ||
                                    radio.isDynamic == true
                        }
                        ItemDetailsTab.RADIO_VERSIONS ->
                            when (val state = radioVersionsState) {
                                is DataState.Data -> state.data.isEmpty()
                                is DataState.Stale -> state.data.isEmpty()
                                else -> false
                            }
                        else -> false
                    }
                }
            } ?: emptyList()

        /** While any visible tab's backing list is still loading, defer the tabs bar entirely. */
        val subItemsLoading: Boolean get() = tabs.any { it.subState(this) is DataState.Loading }

        /**
         * The tab to display: none while loading; the user's pick if still valid; otherwise the
         * first tab that actually has data (so an artist with no albums but tracks opens on
         * Tracks), falling back to the first tab when everything is empty.
         */
        val selectedTab: ItemDetailsTab?
            get() = when {
                subItemsLoading -> null
                userSelectedTab in tabs -> userSelectedTab
                else -> tabs.firstOrNull { it.subState(this).hasItems() } ?: tabs.firstOrNull()
            }
    }

    private var rawAlbums: List<Album> = emptyList()
    private var rawPlayableItems: List<PlayableItem> = emptyList()
    private var playableItemsContext: SubItemContext? = null
    private var transcriptJob: Job? = null

    /** Inputs of the visible playable list. Null while that list is not Data; see [setPlayableItemsState]. */
    private val playableInputs = MutableStateFlow<PlayableInputs?>(null)

    // Identity equality on purpose: each publish emits without comparing the whole list on Main.
    private class PlayableInputs(
        val raw: List<PlayableItem>,
        val context: SubItemContext,
        val query: String,
        val sort: SortOption,
    )

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

    /** The refresh entry of the item menu; null hides it. */
    val refreshAction: StateFlow<RefreshItemAction?> =
        combine(
            _state.map { it.itemOrNull() }.distinctUntilChanged(),
            mainDataSource.refreshItemAccess,
            providerRepository.providers,
        ) { item, access, providers ->
            item?.refreshItemAction(access, providers.dataOrNull)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    init {
        // Listen to library changes; refresh the open item + its sub-lists.
        // The repository already handles the library-fallback re-keying that
        // used to live here for delete events.
        viewModelScope.launch {
            mediaItemRepository.itemChanges.collect { change ->
                val updated = change.item
                (_state.value.itemState as? DataState.Data)?.data?.let { current ->
                    if (current.hasAnyMappingFrom(updated)) {
                        val item = updated.withFavorite(change.favoriteAfter(current.favorite))
                        _state.update { it.copy(itemState = DataState.Data(item)) }
                    }
                }
                updateSubItemIfNeeded(updated, change)
            }
        }
        viewModelScope.launch {
            combine(
                _state.map { it.itemOrNull()?.uri }.distinctUntilChanged(),
                itemRefresher.refreshingUris,
            ) { uri, uris -> uri in uris }
                .distinctUntilChanged()
                .collect { refreshing -> _state.update { it.copy(refreshingItem = refreshing) } }
        }
        // The refresh changes the listings of the item, so the sub-items load again. The answer
        // replaces the item by uri, because a substitute shares no mapping with an unavailable item.
        viewModelScope.launch {
            itemRefresher.refreshed.collect { (uri, refreshed) ->
                val current = _state.value.itemOrNull()?.takeIf { it.uri == uri } ?: return@collect
                val item = refreshed.withFavorite(current.favorite)
                _state.update { it.copy(itemState = DataState.Data(item)) }
                loadSubItems(item)
            }
        }
        // Filter and sort off Main. mapLatest drops a superseded run; the identity check drops a
        // result that waited in the flowOn buffer while newer inputs (or a Loading) arrived.
        viewModelScope.launch {
            playableInputs.filterNotNull()
                .mapLatest { inputs ->
                    inputs to inputs.raw.clientFiltered(inputs.query).clientSorted(inputs.sort, inputs.context)
                }
                .flowOn(Dispatchers.Default)
                .collect { (inputs, visible) ->
                    if (inputs === playableInputs.value) {
                        _state.update { it.copy(playableItemsState = DataState.Data(visible)) }
                    }
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

    // A new type here also goes into `opensItemDetails` (ItemDetailsTab.kt).
    private suspend fun getItemById(
        itemId: String,
        mediaType: MediaType,
        providerId: String,
    ): AppMediaItem? {
        val request = when (mediaType) {
            MediaType.ARTIST -> Request.Artist.get(itemId, providerId)
            MediaType.ALBUM -> Request.Album.get(itemId, providerId)
            MediaType.PLAYLIST -> Request.Playlist.get(itemId, providerId)
            MediaType.RADIO -> Request.RadioStation.get(itemId, providerId)
            MediaType.PODCAST -> Request.Podcast.get(itemId, providerId)
            MediaType.AUDIOBOOK -> Request.Audiobook.get(itemId, providerId)
            MediaType.GENRE -> Request.Genre.get(itemId, providerId)
            MediaType.COLLECTION -> Request.Audiobook.getCollection(itemId)
            MediaType.TRACK -> Request.Track.get(itemId, providerId)
            MediaType.PODCAST_EPISODE -> Request.Podcast.getEpisode(itemId, providerId)
            else -> return null
        }

        return mediaItemRepository.fetchMediaItem(request).getOrNull()
    }

    private fun loadSubItems(item: AppMediaItem) {
        when (item) {
            is Artist -> {
                // Prefetch similar artists in the background so the sheet opens warm: the server's
                // first per-artist lookup (lastfm + matching) is the slow part, so we pay it while
                // the user browses albums/tracks rather than on the menu tap.
                loadSimilarArtists()
                loadGenres(item)
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
                loadGenres(item)
            }

            is Playlist -> {
                _state.update {
                    it.copy(
                        albumsState = DataState.NoData(),
                        albumsSortOption = null,
                        playableItemsSortOption = settingsRepository.getSortOption(SubItemContext.PLAYLIST_ITEMS),
                    )
                }
                loadPlaylistTracks(item.itemId, item.provider)
                loadPlaylistOwner(item)
            }
            is RadioStation -> {
                loadRadioVersions(item)
                val supportsRadioTracks = mediaItemRepository.supportsRadioTracks()

                _state.update {
                    it.copy(
                        albumsState = DataState.NoData(),
                        albumsSortOption = null,
                        playableItemsSortOption =
                            settingsRepository.getSortOption(SubItemContext.RADIO_TRACKS),
                        radioTracksSupported = supportsRadioTracks,
                    )
                }

                if (
                    supportsRadioTracks &&
                    item.isEndless == false &&
                    item.isDynamic != true
                ) {
                    loadRadioTracks(item.itemId, item.provider)
                } else {
                    setPlayableItemsState(DataState.NoData())
                }
            }

            is Podcast -> {
                _state.update {
                    it.copy(
                        albumsState = DataState.NoData(),
                        albumsSortOption = null,
                        playableItemsSortOption = settingsRepository.getSortOption(SubItemContext.PODCAST_EPISODES),
                        episodesPlayedFilter = settingsRepository.getPodcastEpisodesPlayedFilter(),
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

            // get_collection already carries the books, so there is nothing more to load.
            is MediaCollection -> {
                _state.update {
                    it.copy(
                        albumsState = DataState.NoData(),
                        albumsSortOption = null,
                        playableItemsSortOption = null,
                    )
                }
                setPlayableItems(item.items.filterIsInstance<PlayableItem>(), SubItemContext.COLLECTION_ITEMS)
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

            is Track -> {
                _state.update {
                    it.copy(
                        playableItemsState = DataState.NoData(),
                        albumsSortOption = null,
                        playableItemsSortOption = null,
                    )
                }
                loadTrackDetails(item)
            }

            is PodcastEpisode -> {
                _state.update {
                    it.copy(
                        albumsState = DataState.NoData(),
                        albumsSortOption = null,
                        playableItemsSortOption = settingsRepository.getSortOption(SubItemContext.PODCAST_EPISODES),
                        episodesPlayedFilter = settingsRepository.getPodcastEpisodesPlayedFilter(),
                    )
                }
                loadOtherEpisodes(item)
                loadTranscript(item)
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

    /** The library genres mapped to a library item. The server maps none to an item of a music service. */
    private fun loadGenres(item: AppMediaItem) {
        if (!item.isInLibrary) {
            _state.update { it.copy(genres = emptyList()) }
            return
        }
        viewModelScope.launch {
            val genres = fetchList<Genre>(Request.Genre.forItem(item.mediaType, item.itemId))
            _state.update { it.copy(genres = genres.dataOrNull.orEmpty()) }
        }
    }

    /**
     * The owner line at once, then with the name of another owner when the viewer may list the
     * members (`library.write`). A refused or failed listing keeps the line without the name.
     */
    private fun loadPlaylistOwner(playlist: Playlist) {
        val viewerId = (authCoordinator.authState.value as? AuthState.Authenticated)?.user?.userId
        val accessSupported = mainDataSource.playlistAccessSupported.value
        _state.update { it.copy(playlistOwnerLine = playlist.ownerLine(accessSupported, viewerId, null)) }
        val ownerId = playlist.access?.owner?.takeIf { accessSupported && it != viewerId } ?: return
        if (!mainDataSource.grantedScopes.value.canEditLibrary) return
        viewModelScope.launch {
            val name = providerRepository.shareCandidates()?.firstOrNull { it.userId == ownerId }?.name ?: return@launch
            _state.update { it.copy(playlistOwnerLine = playlist.ownerLine(accessSupported, viewerId, name)) }
        }
    }

    private fun loadAlbumTracks(itemId: String, provider: String) {
        viewModelScope.launch {
            setPlayableItemsState(DataState.Loading())

            try {
                val tracks = mediaItemRepository.fetchMediaItems(
                    Request.Album.getTracks(
                        itemId = itemId,
                        providerInstanceIdOrDomain = provider,
                    ),
                ).getOrNull()
                    ?.filterIsInstance<Track>()
                    ?: emptyList()

                setPlayableItems(tracks, SubItemContext.ALBUM_TRACKS)
            } catch (e: Exception) {
                Logger.e("Failed to load album tracks", e)
                setPlayableItemsState(DataState.Error())
            }
        }
    }

    private fun loadPlaylistTracks(
        itemId: String,
        provider: String,
        forceRefresh: Boolean = false,
    ) {
        viewModelScope.launch {
            setPlayableItemsState(DataState.Loading())

            try {
                val tracks = mediaItemRepository.fetchAllMediaItems(
                    Request.Playlist.getTracks(
                        itemId = itemId,
                        providerInstanceIdOrDomain = provider,
                        forceRefresh = forceRefresh.takeIf { it },
                    ),
                )
                    ?.filterIsInstance<PlayableItem>()
                    ?: emptyList()

                setPlayableItems(tracks, SubItemContext.PLAYLIST_ITEMS)
            } catch (e: Exception) {
                Logger.e("Failed to load playlist tracks", e)
                setPlayableItemsState(DataState.Error())
            }
        }
    }

    private fun loadRadioTracks(
        itemId: String,
        provider: String,
    ) {
        viewModelScope.launch {
            setPlayableItemsState(DataState.Loading())

            val result = mediaItemRepository.fetchMediaItems(
                Request.RadioStation.getTracks(
                    itemId = itemId,
                    providerInstanceIdOrDomain = provider,
                ),
            )

            val items = result.getOrElse { error ->
                if (error is CancellationException) throw error

                Logger.e("Failed to load radio tracks", error)
                setPlayableItemsState(DataState.Error())
                return@launch
            }

            val tracks = items.filterIsInstance<PlayableItem>()
            setPlayableItems(tracks, SubItemContext.RADIO_TRACKS)
        }
    }

    // Server 2.11 filters played episodes before paging; older servers may ignore
    // the argument, so filter locally too. Cancel previous loads when the filter changes.
    private fun loadPodcastEpisodes(
        itemId: String,
        provider: String,
        exceptItemId: String? = null,
    ) {
        episodesLoad?.cancel()
        episodesLoad = viewModelScope.launch {
            setPlayableItemsState(DataState.Loading())
            val hidePlayed = _state.value.episodesPlayedFilter == PlayedFilter.HIDE_PLAYED

            try {
                val episodes = mediaItemRepository.fetchAllMediaItems(
                    Request.Podcast.getEpisodes(
                        itemId = itemId,
                        providerInstanceIdOrDomain = provider,
                        fullyPlayed = false.takeIf { hidePlayed },
                    ),
                )
                    ?.filterIsInstance<PodcastEpisode>()
                    ?.filterNot { hidePlayed && it.fullyPlayed == true }
                    ?.filterNot { it.itemId == exceptItemId }
                    ?: emptyList()

                setPlayableItems(episodes, SubItemContext.PODCAST_EPISODES)
            } catch (e: Exception) {
                Logger.e("Failed to load podcast episodes", e)
                setPlayableItemsState(DataState.Error())
            }
        }
    }

    /** The other episodes of the podcast of [episode], like the web page. Nothing without the podcast. */
    private fun loadOtherEpisodes(episode: PodcastEpisode) {
        val podcast = episode.podcast ?: return setPlayableItemsState(DataState.NoData())
        loadPodcastEpisodes(podcast.itemId, podcast.provider, exceptItemId = episode.itemId)
    }

    /** Follows the transcript of [episode] into [State.lyrics]; the repository loads it once. */
    private fun loadTranscript(episode: PodcastEpisode) {
        transcriptJob?.cancel()
        transcriptJob = viewModelScope.launch {
            transcriptRepository.also { it.load(episode) }.transcript(episode).collect { transcript ->
                _state.update { it.copy(lyrics = transcript.dataOrNull) }
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

    /** The albums, versions, similar tracks and lyrics of [track], each loaded on its own. */
    private fun loadTrackDetails(track: Track) {
        // Read once and fail closed: a provider list that never loads must not hold the tabs on Loading.
        val similarSupported = providerRepository.providers.value.dataOrNull.orEmpty()
            .any { it.available && SIMILAR_TRACKS_FEATURE in it.supportedFeatures }
        _state.update {
            it.copy(
                albumsState = DataState.Loading(),
                trackVersionsState = DataState.Loading(),
                similarTracksState = if (similarSupported) DataState.Loading() else DataState.NoData(),
            )
        }
        viewModelScope.launch {
            val albums = fetchList<Album>(Request.Track.getAlbums(track.itemId, track.provider))
            _state.update { it.copy(albumsState = albums) }
        }
        viewModelScope.launch {
            val versions = fetchList<Track>(Request.Track.getVersions(track.itemId, track.provider))
            _state.update { it.copy(trackVersionsState = versions) }
        }
        if (similarSupported) {
            viewModelScope.launch {
                // A provider can answer the track itself under another mapping.
                val similar = fetchList<Track>(Request.Track.getSimilar(track.itemId, track.provider))
                    .map { tracks -> tracks.filterNot { it.hasAnyMappingFrom(track) } }
                _state.update { it.copy(similarTracksState = similar) }
            }
        }
        viewModelScope.launch {
            val lyrics = track.lyrics ?: mediaItemRepository.fetchTrackLyrics(track)
                .onFailure { Logger.e("Failed to load track lyrics", it) }
                .getOrNull()
            _state.update { it.copy(lyrics = lyrics) }
        }
    }

    private fun loadRadioVersions(radio: RadioStation) {
        _state.update {
            it.copy(radioVersionsState = DataState.Loading())
        }

        viewModelScope.launch {
            val versions = fetchList<RadioStation>(
                Request.RadioStation.getVersions(radio.itemId, radio.provider),
            )
            _state.update {
                it.copy(radioVersionsState = versions)
            }
        }
    }

    private suspend inline fun <reified T : AppMediaItem> fetchList(request: Request): DataState<List<T>> =
        mediaItemRepository.fetchMediaItems(request).fold(
            onSuccess = { items -> DataState.Data(items.filterIsInstance<T>()) },
            onFailure = {
                Logger.e("Failed to load ${request.command}", it)
                DataState.Error()
            },
        )

    /**
     * Fetches similar artists for the currently loaded [Artist]. Called both as a background
     * prefetch when the artist loads and (idempotently) when the sheet opens: loaded once — a repeat
     * call reuses the cached result, but a prior failure is retried.
     */
    fun loadSimilarArtists() {
        val artist = _state.value.itemState.dataOrNull as? Artist ?: return
        val current = _state.value.similarArtistsState
        if (current is DataState.Data || current is DataState.Loading) return

        viewModelScope.launch {
            _state.update { it.copy(similarArtistsState = DataState.Loading()) }

            try {
                val artists = mediaItemRepository.fetchMediaItems(
                    Request.Artist.getSimilarArtists(
                        itemId = artist.itemId,
                        providerInstanceIdOrDomain = artist.provider,
                    ),
                ).getOrNull()
                    ?.filterIsInstance<Artist>()
                    ?: emptyList()

                _state.update { it.copy(similarArtistsState = DataState.Data(artists)) }
            } catch (e: Exception) {
                Logger.e("Failed to load similar artists", e)
                _state.update { it.copy(similarArtistsState = DataState.Error()) }
            }
        }
    }

    fun onPlayClick(mode: PlayMode) {
        (_state.value.itemState as? DataState.Data)?.data?.let { onPlayClick(it, mode) }
    }

    fun onPlayClick(item: AppMediaItem, mode: PlayMode) {
        // The rows of an episode page belong to its podcast, so "from here" plays the podcast.
        val parent = (_state.value.itemState as? DataState.Data)?.data?.let { (it as? PodcastEpisode)?.podcast ?: it }
        val (itemToPlay, startItem) = if (mode.fromHereInParent && parent != null) {
            Pair(parent, item)
        } else {
            Pair(item, null)
        }

        val mediaUri = itemToPlay.mediaUri ?: return
        mainDataSource.selectedPlayer?.queueOrPlayerId?.let { queueId ->
            Logger.withTag("PlayDispatch").i {
                "ItemDetailsViewModel.onPlayClick: uri=$mediaUri mode=$mode " +
                        "startItem=${startItem?.itemId} queue=$queueId"
            }
            playDispatcher.play(
                PlayRequest(
                    queueOrPlayerId = queueId,
                    media = listOf(mediaUri),
                    option = mode.option,
                    endlessMixMode = mode.endlessMix && item !is Genre,
                    startItem = startItem?.itemId,
                    shuffle = mode.shuffle,
                    startFromBeginning = mode.startFromBeginning,
                ),
            )
        }
    }

    fun onChapterClick(chapterPosition: Int) {
        (_state.value.itemState as? DataState.Data)?.data?.let { item ->
            item.uri?.let { uri ->
                mainDataSource.selectedPlayer?.queueOrPlayerId?.let { queueId ->
                    Logger.withTag("PlayDispatch").i {
                        "ItemDetailsViewModel.onChapterClick: uri=$uri " +
                                "chapter=$chapterPosition queue=$queueId"
                    }
                    playDispatcher.play(
                        PlayRequest(
                            queueOrPlayerId = queueId,
                            media = listOf(uri),
                            option = QueueOption.REPLACE,
                            startItem = chapterPosition.toString(),
                        ),
                    )
                }
            }
        }
    }

    private var episodesLoad: Job? = null

    /** The filter is a server argument on 2.11, so the episodes load again. */
    fun onEpisodesPlayedFilterToggled() {
        val item = _state.value.itemOrNull()
        if (item !is Podcast && item !is PodcastEpisode) return
        val filter = _state.value.episodesPlayedFilter.toggled()
        settingsRepository.setPodcastEpisodesPlayedFilter(filter)
        _state.update { it.copy(episodesPlayedFilter = filter) }
        when (item) {
            is Podcast -> loadPodcastEpisodes(item.itemId, item.provider)
            is PodcastEpisode -> loadOtherEpisodes(item)
            else -> Unit
        }
    }

    fun onPlayableItemsSortChanged(context: SubItemContext, sortOption: SortOption) {
        settingsRepository.setSortOption(context, sortOption)
        _state.update { it.copy(playableItemsSortOption = sortOption) }
        publishPlayableItems()
    }

    /** null closes the filter and shows the full list again; any string filters live. */
    fun onPlayableItemsQueryChanged(query: String?) {
        _state.update { it.copy(playableItemsQuery = query) }
        publishPlayableItems()
    }

    private fun setPlayableItems(items: List<PlayableItem>, context: SubItemContext) {
        rawPlayableItems = items
        playableItemsContext = context
        publishPlayableItems()
    }

    /**
     * Single derivation of the visible list: raw → filter → sort. Every input change (load, sort,
     * query, item update) funnels through here so the three never drift apart. The derivation
     * itself runs in the [playableInputs] collector, off Main.
     */
    private fun publishPlayableItems() {
        val context = playableItemsContext ?: return
        val st = _state.value
        playableInputs.value = PlayableInputs(
            raw = rawPlayableItems,
            context = context,
            query = st.playableItemsQuery.orEmpty(),
            sort = st.playableItemsSortOption ?: SortConfig.defaultFor(context),
        )
    }

    /** A non-Data list state. Clears [playableInputs], so a derivation in flight cannot replace it. */
    private fun setPlayableItemsState(state: DataState<List<PlayableItem>>) {
        playableInputs.value = null
        _state.update { it.copy(playableItemsState = state) }
    }

    // Bypasses the server's playlist-tracks cache, which is the only way to make a
    // provider playlist (e.g. "Random Album") produce a new selection on demand.
    fun refreshPlaylistTracks() {
        if (_state.value.playableItemsState is DataState.Loading) return
        val playlist = (state.value.itemState as? DataState.Data)?.data as? Playlist ?: return
        loadPlaylistTracks(playlist.itemId, playlist.provider, forceRefresh = true)
    }

    /** The [ItemRefresher] sends it in the app scope; [ItemRefresher.refreshed] gives the answer. */
    fun refreshItem() {
        _state.value.itemOrNull()?.uri?.let(itemRefresher::refresh)
    }

    fun reload() {
        (state.value.itemState as? DataState.Data)?.data?.let {
            loadSubItems(it)
        }
    }

    private fun updateSubItemIfNeeded(changed: AppMediaItem, change: MediaItemChange) {
        when (changed) {
            is Artist -> {
                val artistsData = (_state.value.artistsState as? DataState.Data)?.data ?: return
                val updated = artistsData.map {
                    if (it.itemId == changed.itemId) changed.withFavorite(change.favoriteAfter(it.favorite)) else it
                }
                _state.update { it.copy(artistsState = DataState.Data(updated)) }
            }

            is Album -> {
                val albumsData = (_state.value.albumsState as? DataState.Data)?.data ?: return
                val replace = { album: Album ->
                    if (album.itemId == changed.itemId) {
                        changed.withFavorite(change.favoriteAfter(album.favorite))
                    } else {
                        album
                    }
                }
                rawAlbums = rawAlbums.map(replace)
                _state.update { it.copy(albumsState = DataState.Data(albumsData.map(replace))) }
            }

            is PlayableItem -> {
                (changed as? Track)?.let { track ->
                    _state.update {
                        it.copy(
                            trackVersionsState = it.trackVersionsState.replacing(track, change),
                            similarTracksState = it.similarTracksState.replacing(track, change),
                        )
                    }
                }
                // Non-null inputs mean Data or Data pending: the derivation runs off Main, so the
                // state can still read Loading while the raw list is already set.
                if (playableInputs.value == null) return
                if (rawPlayableItems.none { it.itemId == changed.itemId }) return
                rawPlayableItems = rawPlayableItems.map { existing ->
                    if (existing.itemId == changed.itemId) {
                        changed.withFavorite(change.favoriteAfter(existing.favorite))
                    } else {
                        existing
                    }
                }
                publishPlayableItems()
            }

            else -> Unit
        }
    }

    private fun DataState<List<Track>>.replacing(changed: Track, change: MediaItemChange): DataState<List<Track>> {
        val tracks = (this as? DataState.Data)?.data
            ?.takeIf { list -> list.any { it.itemId == changed.itemId } }
            ?: return this
        return DataState.Data(
            tracks.map {
                if (it.itemId == changed.itemId) {
                    changed.withFavorite(
                    change.favoriteAfter(it.favorite),
                )
                } else {
                    it
                }
            },
        )
    }

    companion object {
        const val SIMILAR_TRACKS_FEATURE = "similar_tracks"
        const val ARTIST_SECTION_LIMIT = 10
    }
}

private fun ItemDetailsViewModel.State.itemOrNull(): AppMediaItem? = when (itemState) {
    is DataState.Data -> itemState.data
    is DataState.Stale -> itemState.data
    else -> null
}

/**
 * The [DataState] driving this tab's loading/selection. For the artist tabs it's the aggregate of
 * the Library/All sub-sections; for the others, the single backing list. Chapters are carried
 * by the item itself.
 */
private fun ItemDetailsTab.subState(
    state: ItemDetailsViewModel.State,
): DataState<List<Any>> = when (this) {
    ItemDetailsTab.GENRE_ALBUMS,
    ItemDetailsTab.TRACK_ALBUMS,
        -> state.albumsState

    ItemDetailsTab.TRACK_VERSIONS -> state.trackVersionsState
    ItemDetailsTab.RADIO_VERSIONS -> state.radioVersionsState
    ItemDetailsTab.SIMILAR_TRACKS -> state.similarTracksState
    ItemDetailsTab.ALBUM_TRACKS,
    ItemDetailsTab.PLAYLIST_ITEMS,
    ItemDetailsTab.RADIO_TRACKS,
    ItemDetailsTab.PODCAST_EPISODES,
    ItemDetailsTab.OTHER_EPISODES,
    ItemDetailsTab.COLLECTION_ITEMS,
        -> state.playableItemsState

    ItemDetailsTab.GENRE_ARTISTS -> state.artistsState
    ItemDetailsTab.AUDIOBOOK_CHAPTERS ->
        DataState.Data((state.itemOrNull() as? Audiobook)?.chapters.orEmpty())
}

private fun DataState<List<*>>.hasItems(): Boolean = when (this) {
    is DataState.Data -> data.isNotEmpty()
    is DataState.Stale -> data.isNotEmpty()
    else -> false
}

/** The count and the total playing time, in seconds, of a loaded track list. */
data class PlayableSummary(val count: Int, val totalSeconds: Double)
