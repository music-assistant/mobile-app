package io.music_assistant.client.ui.compose.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import io.music_assistant.client.api.APICommands
import io.music_assistant.client.api.Request
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.data.MainDataSource
import io.music_assistant.client.data.model.client.Player
import io.music_assistant.client.data.model.client.PlayerData
import io.music_assistant.client.data.model.client.QueueOption
import io.music_assistant.client.data.model.client.RepeatMode
import io.music_assistant.client.data.model.client.Shortcut
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.client.items.Genre
import io.music_assistant.client.data.model.client.items.RecommendationFolder
import io.music_assistant.client.data.model.client.items.Track
import io.music_assistant.client.data.model.client.items.browsePlaybackUri
import io.music_assistant.client.data.model.server.ServerUser
import io.music_assistant.client.data.repository.MediaItemRepository
import io.music_assistant.client.player.sendspin.SendspinState
import io.music_assistant.client.settings.SettingsRepository
import io.music_assistant.client.ui.compose.common.DataState
import io.music_assistant.client.ui.compose.common.action.PlayerAction
import io.music_assistant.client.ui.compose.common.action.QueueAction
import io.music_assistant.client.utils.AuthProcessState
import io.music_assistant.client.utils.DataConnectionState
import io.music_assistant.client.utils.SessionState
import io.music_assistant.client.utils.resultAs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

val HomeScreenViewModel.PlayersState.Data.selectedPlayer: PlayerData?
    get() = selectedPlayerIndex?.let(playerData::getOrNull)

@OptIn(FlowPreview::class)
class HomeScreenViewModel(
    private val apiClient: ServiceClient,
    private val dataSource: MainDataSource,
    private val settings: SettingsRepository,
    private val mediaItemRepository: MediaItemRepository,
) : ViewModel() {
    private val jobs = mutableListOf<Job>()
    private var loadDataJob: Job? = null

    private var browseFolderCache: List<RecommendationFolder>? =
        settings.cachedBrowseFolders.value
            .takeIf { it.isNotEmpty() }
            ?.map { stored ->
                RecommendationFolder(
                    itemId = stored.itemId,
                    provider = stored.provider,
                    name = stored.name,
                    uri = stored.uri,
                    images = emptyMap(),
                    path = stored.path,
                )
            }

    // A persistent cache means a normal app launch does not need to crawl
    // the Browse tree again. A manual Home refresh explicitly invalidates it.
    private var forceBrowseFolderRefresh = false

    private val _links = MutableSharedFlow<String>()
    val links = _links.asSharedFlow()

    // Local (Sendspin) player identity — used by the group dialog to decide
    // whether to show the playback-delay adjuster.
    val localPlayerId: String
        get() = settings.sendspinEffectivePlayerId.value

    fun adjustSendspinStaticDelayMs(deltaMs: Int) {
        settings.setSendspinStaticDelayMs(settings.sendspinStaticDelayMs.value + deltaMs)
    }

    /** Live elapsed-time flow for the slider. Ticks at 500 ms only while playing + subscribed. */
    fun observePosition(queueId: String) = dataSource.positionTracker.observe(queueId)

    /**
     * Server-synced `audiobook_chapter_progress` gate for the chapter-relative timeline.
     * The web frontend owns the toggle; this client refreshes it on connect.
     */
    val chapterProgressEnabled: StateFlow<Boolean> =
        dataSource.userPreferences.chapterProgressEnabled
            .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    /**
     * Seconds of audio buffered ahead of the local playhead, sampled to ~2 Hz so the buffered
     * segment on the slider tracks the position tick without spamming recomposition.
     */
    fun observeLocalBufferedSeconds() = dataSource.localBufferedSeconds.sample(BUFFER_REAL_INTERVAL)

    /** User toggle: whether the now-playing slider draws the buffered-ahead segment. */
    val showBufferVisualization = settings.showBufferVisualization

    private val _connectionState = MutableStateFlow<SessionState>(SessionState.Disconnected.Initial)
    val connectionState = _connectionState.asStateFlow()

    private val _state = MutableStateFlow(
        State(
            shortcuts = DataState.Loading(),
            recommendations = DataState.Loading(),
            favorites = DataState.Loading(),
            randomFolders = DataState.Loading(),
            homeRowsConfig = settings.homeRowsConfig.value,
        ),
    )
    val state = _state.asStateFlow()

    private val _playersState =
        MutableStateFlow<PlayersState>(PlayersState.Loading)
    val playersState = _playersState.asStateFlow()

    // These fields must be initialized before the collectors started in init;
    // test dispatchers can run those collectors immediately.
    private var randomPlaybackSessionUris: List<String> = emptyList()
    private var randomPlaybackSessionIndex: Int = -1
    private var randomPlaybackSessionTargetId: String? = null
    private var randomPlaybackSessionQueueId: String? = null
    private var randomPlaybackFolderSequence: List<RecommendationFolder> = emptyList()
    private var randomPlaybackFolderIndex: Int = -1
    private var randomPlaybackFolderMode = SettingsRepository.RandomPlaybackMode.OFF
    private var randomPlaybackFolderTransitionInFlight = false
    private var randomPlaybackWasPlaying = false
    private var randomPlaybackLastElapsedSec = 0.0
    private var randomPlaybackAutoAdvanceInFlight = false

    init {
        viewModelScope.launch {
            apiClient.sessionState.collect { connection ->
                _connectionState.value = connection
                when (connection) {
                    is SessionState.Reconnecting -> {
                        // Preserve UI state during reconnection - don't stop jobs or reload data
                        // UI stays in current state (e.g., showing players, recommendations)
                    }

                    is SessionState.Connected -> {
                        when (val connState = connection.dataConnectionState) {
                            is DataConnectionState.Authenticated -> {
                                if (_state.value.recommendations !is DataState.Data) {
                                    loadData()
                                }
                                // Only show loading if we don't have cached data (e.g. fresh connect).
                                // During reconnection the existing player list stays visible.
                                if (_playersState.value !is PlayersState.Data) {
                                    _playersState.update { PlayersState.Loading }
                                }
                                stopJobs()
                                jobs.add(watchPlayersData())
                                jobs.add(watchSelectedPlayerData())
                            }

                            is DataConnectionState.AwaitingAuth -> {
                                when (connState.authProcessState) {
                                    AuthProcessState.NotStarted,
                                    AuthProcessState.InProgress,
                                        -> {
                                        if (_playersState.value !is PlayersState.Data) {
                                            _playersState.update { PlayersState.Loading }
                                        }
                                        stopJobs()
                                    }

                                    AuthProcessState.LoggedOut,
                                    is AuthProcessState.Failed,
                                        -> {
                                        _playersState.update { PlayersState.NoAuth }
                                        stopJobs()
                                    }
                                }
                            }

                            DataConnectionState.AwaitingServerInfo -> {
                                if (_playersState.value !is PlayersState.Data) {
                                    _playersState.update { PlayersState.Loading }
                                }
                                stopJobs()
                            }
                        }
                    }

                    SessionState.Connecting -> {
                        if (_playersState.value !is PlayersState.Data) {
                            _playersState.update { PlayersState.Loading }
                        }
                        loadDataJob?.cancel()
                        stopJobs()
                    }

                    is SessionState.Disconnected -> {
                        loadDataJob?.cancel()
                        when (connection) {
                            is SessionState.Disconnected.Error,
                            SessionState.Disconnected.Initial,
                            SessionState.Disconnected.ByUser,
                                -> {
                                _playersState.update { PlayersState.Disconnected }
                                stopJobs()
                            }

                            SessionState.Disconnected.NoServerData -> {
                                _playersState.update { PlayersState.NoServer }
                                stopJobs()
                            }

                            SessionState.Disconnected.Backgrounded -> {
                                // Preserve current state for instant foreground reconnect
                            }
                        }
                    }
                }
            }
        }

        viewModelScope.launch {
            settings.homeRowsConfig.collect { config ->
                _state.update { it.copy(homeRowsConfig = config) }
            }
        }

        // Listen to real-time library changes to refresh tracks already shown
        // in the recommendations grid.
        viewModelScope.launch {
            settings.favoriteBrowseFolders.collect {
                loadData()
            }
        }

        viewModelScope.launch {
            mediaItemRepository.itemChanges.collect { change ->
                (change.item as? Track)?.let { updateRecommendationsIfNeeded(it) }

                // Refresh Home so Favorites immediately reflects favorite changes.
                loadData()
            }
        }
    }

    fun loadData(refreshBrowseFolders: Boolean = false) {
        if (refreshBrowseFolders) {
            forceBrowseFolderRefresh = true
        }
        loadDataJob?.cancel()

        _state.update {
            it.copy(
                recommendations = DataState.Loading(),
                shortcuts = DataState.Loading(),
                favorites = DataState.Loading(),
                randomFolders = DataState.Loading(),
            )
        }

        loadDataJob = viewModelScope.launch {
            launch { loadShortcuts() }
            launch { loadBrowseFolderRows() }
            loadRecommendations()
        }
    }

    private suspend fun loadRecommendations() {
        val folders = mediaItemRepository.fetchRecommendationRows().getOrElse { error ->
            if (error is CancellationException) throw error
            Logger.e("Error fetching recommendations: $error")
            _state.update { it.copy(recommendations = DataState.Error()) }
            return
        }

        if (!mediaItemRepository.supportsRecommendationRowItems()) {
            setRecommendationRows(
                folders.map { RecommendationRowState(it, DataState.Data(it.items.orEmpty())) },
            )
            return
        }

        // Show every row as a loading placeholder, then fetch each row's items
        // as its own job.
        setRecommendationRows(folders.map { RecommendationRowState(it, DataState.Loading()) })
        coroutineScope {
            folders.forEach { folder ->
                launch {
                    setRowItems(
                        folder,
                        mediaItemRepository.fetchRecommendationRowItems(folder).orEmpty(),
                    )
                }
            }
        }
    }

    private fun setRecommendationRows(rows: List<RecommendationRowState>) {
        _state.update { it.copy(recommendations = DataState.Data(rows)) }
    }

    private fun setRowItems(folder: RecommendationFolder, items: List<AppMediaItem>) {
        _state.update { state ->
            val rows = (state.recommendations as? DataState.Data)?.data
                ?: return@update state
            val updated = rows.map { row ->
                if (row.folder.itemId == folder.itemId && row.folder.provider == folder.provider) {
                    row.copy(items = DataState.Data(items))
                } else {
                    row
                }
            }
            state.copy(recommendations = DataState.Data(updated))
        }
    }

    private suspend fun loadShortcuts() {
        val shortcutUris = apiClient.sendRequest(Request(APICommands.AUTH_ME))
            .resultAs<ServerUser>()?.preferences?.shortcuts
        val shortcuts = shortcutUris?.mapNotNull {
            mediaItemRepository.fetchMediaItem(
                Request(
                    command = APICommands.MUSIC_ITEM_BY_URI,
                    args = buildJsonObject {
                        put("uri", JsonPrimitive(it))
                    },
                ),
            ).getOrNull()
        }?.map { Shortcut(it) }
        _state.update {
            it.copy(
                shortcuts = if (shortcuts != null) DataState.Data(shortcuts) else DataState.NoData(),
            )
        }
    }

    private suspend fun loadBrowseFolderRows() {
        try {
            val folderPool = if (browseFolderCache.isNullOrEmpty() || forceBrowseFolderRefresh) {
                val discovered = mutableListOf<RecommendationFolder>()

                suspend fun discover(path: String?, current: RecommendationFolder? = null, depth: Int = 0) {
                    if (depth > 12) return
                    val items = mediaItemRepository.fetchMediaItems(Request.Browse.atPath(path)).getOrNull()
                        ?: return
                    val tracks = items.filterIsInstance<Track>().filterNot {
                        it.displayName.trim().equals("(Empty)", true) ||
                            it.displayName.trim().equals("Empty", true)
                    }
                    if (
                        current != null &&
                        tracks.isNotEmpty() &&
                        !current.path.orEmpty().endsWith("://")
                    ) {
                        discovered += current
                    }
                    items.filterIsInstance<RecommendationFolder>()
                        .filter {
                            !it.isParentLink &&
                                it.path != null &&
                                (
                                    it.provider.startsWith("filesystem_local") ||
                                        it.path.startsWith("filesystem_local")
                                )
                        }
                        .forEach { discover(it.path, it, depth + 1) }
                }

                discover(null)
                discovered.distinctBy { it.path }.also { folders ->
                    browseFolderCache = folders
                    settings.setCachedBrowseFolders(
                        folders.map { folder ->
                            SettingsRepository.CachedBrowseFolder(
                                path = requireNotNull(folder.path),
                                itemId = folder.itemId,
                                provider = folder.provider,
                                name = folder.name,
                                uri = folder.uri,
                            )
                        },
                    )
                    forceBrowseFolderRefresh = false
                }
            } else {
                browseFolderCache.orEmpty()
            }

            val libraryFavorites = buildList<AppMediaItem> {
                getList<AppMediaItem>(Request.Artist.listLibrary(favorite = true, limit = 100))?.let(::addAll)
                getList<AppMediaItem>(Request.Album.listLibrary(favorite = true, limit = 100))?.let(::addAll)
                getList<AppMediaItem>(Request.Track.list(favorite = true, limit = 100))?.let(::addAll)
                getList<AppMediaItem>(Request.Playlist.listLibrary(favorite = true, limit = 100))?.let(::addAll)
                getList<AppMediaItem>(Request.Audiobook.listLibrary(favorite = true, limit = 100))?.let(::addAll)
                getList<AppMediaItem>(Request.Podcast.listLibrary(favorite = true, limit = 100))?.let(::addAll)
                getList<AppMediaItem>(Request.RadioStation.listLibrary(favorite = true, limit = 100))?.let(::addAll)
                getList<AppMediaItem>(Request.Genre.listLibrary(favorite = true, limit = 100))?.let(::addAll)
            }
            val favoriteFolders = settings.favoriteBrowseFolders.value.map { stored ->
                RecommendationFolder(
                    itemId = stored.itemId,
                    provider = stored.provider,
                    name = stored.name,
                    uri = stored.uri,
                    images = emptyMap(),
                    path = stored.path,
                )
            }
            val favorites = (favoriteFolders + libraryFavorites).distinctBy { item ->
                if (item is RecommendationFolder) {
                    "folder:${item.path}"
                } else {
                    "${item.mediaType.serverValue}:${item.itemId}"
                }
            }
            _state.update {
                it.copy(
                    favorites = DataState.Data(favorites),
                    randomFolders = DataState.Data(folderPool.shuffled().take(RANDOM_FOLDER_COUNT)),
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            Logger.e("Error loading Home folder rows", error)
            _state.update {
                it.copy(favorites = DataState.Error(), randomFolders = DataState.Error())
            }
        }
    }

    fun setBrowseFolderFavorite(folder: RecommendationFolder, favorite: Boolean) {
        val path = folder.path ?: return
        settings.setBrowseFolderFavorite(
            SettingsRepository.FavoriteBrowseFolder(
                path = path,
                itemId = folder.itemId,
                provider = folder.provider,
                name = folder.displayName,
                uri = folder.uri,
            ),
            favorite = favorite,
        )
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun <T : AppMediaItem> getList(request: Request): List<T>? =
        mediaItemRepository.fetchMediaItems(request).let { result ->
            if (result.isFailure) {
                Logger.e("Error fetching list for request $request: ${result.exceptionOrNull()}")
            }
            result.getOrNull()?.mapNotNull { it as? T }
        }

    fun onPlayClick(
        item: AppMediaItem,
        option: QueueOption,
        radio: Boolean,
    ) {
        dataSource.selectedPlayer?.queueOrPlayerId?.let { queueId ->
            item.mediaUri?.let { mediaUri ->
                viewModelScope.launch {
                    Logger.withTag("PlayDispatch")
                        .i { "HomeScreenViewModel: uri=$mediaUri option=$option radio=$radio queue=$queueId" }
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
    }

    /**
     * Starts randomized playback from a Browse folder.
     *
     * The Browse API returns all items at this folder level. Playable items
     * are sent to Music Assistant as one shuffled REPLACE request.
     *
     * Random Folder playback deliberately does not enable Music Assistant's
     * Don't Stop The Music mode. Folder-to-folder continuation is controlled
     * separately by the app.
     */
    /**
     * Plays the Random Folders collection as one continuous media sequence.
     *
     * Playback starts with the folder the user tapped, then continues through
     * the remaining folders in the current Random Folders Home-row order,
     * wrapping around to folders that appeared before the tapped folder.
     *
     * Music Assistant receives one complete REPLACE playback request, which
     * gives normal media-player semantics: automatic track advancement and
     * working Previous / Next controls without Don't Stop The Music.
     */
    /**
     * Starts continuous playback across the discovered Browse-folder pool.
     *
     * OFF:
     *   selected folder first, then folders alphabetically.
     *
     * RANDOM_SONGS:
     *   first song from the selected folder starts normally; all remaining
     *   available songs are shuffled globally.
     *
     * RANDOM_FOLDERS:
     *   selected folder plays completely in normal order; remaining folders
     *   are randomized while preserving normal song order inside each folder.
     *
     * The whole sequence is sent in one REPLACE request so normal Previous,
     * Next and automatic track advancement continue to work.
     */
    // App-owned playback sequence for the custom folder playback modes.
    //
    // Music Assistant currently exposes this playback to the client as a
    // one-item queue, so Next / Previous cannot rely on MA queue navigation.
    // We therefore keep the sequence here and submit one track at a time.
    /**
     * Handles Next / Previous for an active custom playback session.
     *
     * Returns true when the action was consumed here. All other player
     * actions must continue through the normal Music Assistant path.
     */
    private suspend fun loadRandomPlaybackFolderTracks(
        folder: RecommendationFolder,
    ): List<String> {
        val path = folder.path ?: return emptyList()

        val result =
            mediaItemRepository.fetchMediaItems(
                Request.Browse.atPath(path),
            )

        val items = result.getOrNull()

        if (items == null) {
            Logger.e(
                "Failed to load playback folder '${folder.displayName}'",
                result.exceptionOrNull(),
            )
            return emptyList()
        }

        return items
            .filterIsInstance<Track>()
            .filterNot { track ->
                val name = track.displayName.trim()

                name.equals("(Empty)", ignoreCase = true) ||
                    name.equals("Empty", ignoreCase = true)
            }
            .sortedBy { it.displayName.lowercase() }
            .mapNotNull { it.browsePlaybackUri }
            .distinct()
    }

    private fun switchRandomPlaybackFolder(
        direction: Int,
    ): Boolean {
        if (randomPlaybackFolderTransitionInFlight) return true

        val folders = randomPlaybackFolderSequence
        if (folders.isEmpty()) return false

        randomPlaybackFolderTransitionInFlight = true

        viewModelScope.launch {
            try {
                var index = randomPlaybackFolderIndex
                var attempts = 0

                while (attempts < folders.size) {
                    index =
                        (index + direction + folders.size) %
                            folders.size

                    val folder = folders[index]
                    val songs =
                        loadRandomPlaybackFolderTracks(folder)

                    if (songs.isNotEmpty()) {
                        randomPlaybackFolderIndex = index
                        randomPlaybackSessionUris = songs

                        val trackIndex =
                            if (direction >= 0) {
                                0
                            } else {
                                songs.lastIndex
                            }

                        Logger.withTag("RandomFolderPlay").i {
                            "FOLDER SWITCH " +
                                "folder=${folder.displayName} " +
                                "tracks=${songs.size}"
                        }

                        playRandomPlaybackSessionIndex(trackIndex)
                        return@launch
                    }

                    attempts++
                }

                Logger.withTag("RandomFolderPlay").i {
                    "No playable next folder found"
                }
            } finally {
                randomPlaybackFolderTransitionInFlight = false
            }
        }

        return true
    }

    private fun appendAndPlayRandomLibrarySong(): Boolean {
        if (randomPlaybackFolderTransitionInFlight) return true

        val folders = randomPlaybackFolderSequence
        if (folders.isEmpty()) return false

        randomPlaybackFolderTransitionInFlight = true

        viewModelScope.launch {
            try {
                val candidates = folders.shuffled()

                for (folder in candidates) {
                    val songs =
                        loadRandomPlaybackFolderTracks(folder)
                            .filterNot {
                                it in randomPlaybackSessionUris
                            }

                    if (songs.isEmpty()) continue

                    val uri = songs.random()

                    randomPlaybackSessionUris =
                        randomPlaybackSessionUris + uri

                    Logger.withTag("RandomFolderPlay").i {
                        "RANDOM GLOBAL uri=$uri"
                    }

                    playRandomPlaybackSessionIndex(
                        randomPlaybackSessionUris.lastIndex,
                    )

                    return@launch
                }

                // Everything has already been heard: allow a new cycle.
                randomPlaybackSessionUris =
                    randomPlaybackSessionUris
                        .getOrNull(randomPlaybackSessionIndex)
                        ?.let(::listOf)
                        .orEmpty()

                appendAndPlayRandomLibrarySong()
            } finally {
                randomPlaybackFolderTransitionInFlight = false
            }
        }

        return true
    }

    private fun advanceRandomPlaybackNext(
        source: String,
    ): Boolean {
        val session = randomPlaybackSessionUris

        if (
            session.isEmpty() ||
            randomPlaybackSessionIndex !in session.indices
        ) {
            return false
        }

        if (randomPlaybackSessionIndex < session.lastIndex) {
            val nextIndex = randomPlaybackSessionIndex + 1

            Logger.withTag("RandomFolderPlay").i {
                "ADVANCE source=$source " +
                    "$randomPlaybackSessionIndex -> $nextIndex"
            }

            playRandomPlaybackSessionIndex(nextIndex)
            return true
        }

        return when (randomPlaybackFolderMode) {
            SettingsRepository.RandomPlaybackMode.RANDOM_SONGS ->
                appendAndPlayRandomLibrarySong()

            SettingsRepository.RandomPlaybackMode.OFF,
            SettingsRepository.RandomPlaybackMode.RANDOM_FOLDERS,
            ->
                switchRandomPlaybackFolder(direction = 1)
        }
    }

    private fun advanceRandomPlaybackPrevious(): Boolean {
        val session = randomPlaybackSessionUris

        if (
            session.isEmpty() ||
            randomPlaybackSessionIndex !in session.indices
        ) {
            return false
        }

        if (randomPlaybackSessionIndex > 0) {
            playRandomPlaybackSessionIndex(
                randomPlaybackSessionIndex - 1,
            )
            return true
        }

        return when (randomPlaybackFolderMode) {
            SettingsRepository.RandomPlaybackMode.RANDOM_SONGS ->
                false

            SettingsRepository.RandomPlaybackMode.OFF,
            SettingsRepository.RandomPlaybackMode.RANDOM_FOLDERS,
            ->
                switchRandomPlaybackFolder(direction = -1)
        }
    }

    fun handleRandomPlaybackTransport(
        playerData: PlayerData,
        action: PlayerAction,
    ): Boolean {
        if (
            action != PlayerAction.Next &&
            action != PlayerAction.Previous
        ) {
            return false
        }

        val session = randomPlaybackSessionUris

        if (
            session.isEmpty() ||
            randomPlaybackSessionIndex !in session.indices
        ) {
            return false
        }

        val currentQueueId = playerData.queueInfo?.id

        if (
            randomPlaybackSessionQueueId != null &&
            currentQueueId != null &&
            currentQueueId != randomPlaybackSessionQueueId
        ) {
            return false
        }

        return when (action) {
            PlayerAction.Next ->
                advanceRandomPlaybackNext("MANUAL_NEXT")

            PlayerAction.Previous ->
                advanceRandomPlaybackPrevious()

            else ->
                false
        }
    }

    /**
     * Detects natural completion of the currently playing custom-session track.
     *
     * Music Assistant sees each custom-session song as Queue 1/1, so it stops
     * at the end. When we observe playing -> stopped near the track duration,
     * advance through the same client-owned sequence used by the Next button.
     */
    private fun handleRandomPlaybackNaturalEnd(
        playerData: PlayerData,
    ) {
        val session = randomPlaybackSessionUris

        if (
            session.isEmpty() ||
            randomPlaybackSessionIndex !in session.indices
        ) {
            randomPlaybackWasPlaying = false
            randomPlaybackLastElapsedSec = 0.0
            return
        }

        val sessionQueueId = randomPlaybackSessionQueueId
        val currentQueueId = playerData.queueInfo?.id

        if (
            sessionQueueId != null &&
            currentQueueId != null &&
            currentQueueId != sessionQueueId
        ) {
            return
        }

        val queue = playerData.queueInfo ?: return
        val duration = queue.currentItem?.track?.duration
        val elapsed =
            queue.elapsedTime ?: randomPlaybackLastElapsedSec
        val isPlaying = playerData.player.isPlaying

        if (isPlaying) {
            randomPlaybackWasPlaying = true
            randomPlaybackLastElapsedSec = elapsed
            randomPlaybackAutoAdvanceInFlight = false
            return
        }

        val endPosition =
            maxOf(
                randomPlaybackLastElapsedSec,
                elapsed,
            )

        val endedNaturally =
            randomPlaybackWasPlaying &&
                duration != null &&
                duration > 0.0 &&
                endPosition >= duration - 3.0

        if (
            endedNaturally &&
            !randomPlaybackAutoAdvanceInFlight
        ) {
            randomPlaybackAutoAdvanceInFlight = true
            randomPlaybackWasPlaying = false

            Logger.withTag("RandomFolderPlay").i {
                "NATURAL END " +
                    "index=$randomPlaybackSessionIndex " +
                    "elapsed=$endPosition duration=$duration"
            }

            advanceRandomPlaybackNext("NATURAL_END")
        }
    }

    private fun playRandomPlaybackSessionIndex(
        index: Int,
    ) {
        val session = randomPlaybackSessionUris
        val targetId = randomPlaybackSessionTargetId ?: return

        if (index !in session.indices) return

        randomPlaybackSessionIndex = index
        randomPlaybackWasPlaying = false
        randomPlaybackLastElapsedSec = 0.0

        val uri = session[index]

        Logger.withTag("RandomFolderPlay").i {
            "session track=${index + 1}/${session.size} uri=$uri"
        }

        viewModelScope.launch {
            apiClient.sendRequest(
                Request.Library.play(
                    media = listOf(uri),
                    queueOrPlayerId = targetId,
                    option = QueueOption.REPLACE,
                    radioMode = false,
                ),
            )
        }
    }

    fun playRandomFolder(
        folder: RecommendationFolder,
    ) {
        val selectedPath = folder.path ?: return
        val selectedPlayer =
            dataSource.selectedPlayer ?: return

        val playbackTargetId =
            selectedPlayer.queueOrPlayerId

        val actualQueueId =
            selectedPlayer.queueInfo?.id

        viewModelScope.launch {
            val mode = settings.randomPlaybackMode.value

            val allFolders =
                (
                    listOf(folder) +
                        browseFolderCache.orEmpty()
                )
                    .filter {
                        it.path != null &&
                            !it.isParentLink
                    }
                    .distinctBy { it.path }

            val selectedFolder =
                allFolders.firstOrNull {
                    it.path == selectedPath
                } ?: folder

            val alphabetic =
                allFolders.sortedBy {
                    it.displayName.lowercase()
                }

            val selectedAlphabeticIndex =
                alphabetic.indexOfFirst {
                    it.path == selectedPath
                }

            randomPlaybackFolderSequence =
                when (mode) {
                    SettingsRepository.RandomPlaybackMode.OFF -> {
                        if (selectedAlphabeticIndex >= 0) {
                            alphabetic.drop(selectedAlphabeticIndex) +
                                alphabetic.take(selectedAlphabeticIndex)
                        } else {
                            listOf(selectedFolder) + alphabetic
                        }
                    }

                    SettingsRepository.RandomPlaybackMode.RANDOM_FOLDERS ->
                        listOf(selectedFolder) +
                            allFolders
                                .filterNot {
                                    it.path == selectedPath
                                }
                                .shuffled()

                    SettingsRepository.RandomPlaybackMode.RANDOM_SONGS ->
                        allFolders
                }

            randomPlaybackFolderIndex =
                randomPlaybackFolderSequence
                    .indexOfFirst {
                        it.path == selectedPath
                    }
                    .coerceAtLeast(0)

            randomPlaybackFolderMode = mode
            randomPlaybackFolderTransitionInFlight = false

            val selectedTracks =
                loadRandomPlaybackFolderTracks(selectedFolder)

            if (selectedTracks.isEmpty()) {
                Logger.withTag("RandomFolderPlay").i {
                    "Selected folder has no playable tracks"
                }
                return@launch
            }

            // RANDOM_SONGS starts with the selected folder's first song.
            // Every following Next/natural-end chooses globally.
            randomPlaybackSessionUris =
                if (
                    mode ==
                    SettingsRepository.RandomPlaybackMode.RANDOM_SONGS
                ) {
                    listOf(selectedTracks.first())
                } else {
                    selectedTracks
                }

            randomPlaybackSessionIndex = 0
            randomPlaybackSessionTargetId = playbackTargetId
            randomPlaybackSessionQueueId = actualQueueId
            randomPlaybackWasPlaying = false
            randomPlaybackLastElapsedSec = 0.0
            randomPlaybackAutoAdvanceInFlight = false

            actualQueueId?.let { queueId ->
                apiClient.sendRequest(
                    Request.Queue.setRepeatMode(
                        queueId = queueId,
                        repeatMode = RepeatMode.OFF,
                    ),
                )

                apiClient.sendRequest(
                    Request.Queue.setShuffle(
                        queueId = queueId,
                        enabled = false,
                    ),
                )

                apiClient.sendRequest(
                    Request.Queue.setDontStopTheMusic(
                        queueId = queueId,
                        enabled = false,
                    ),
                )
            }

            Logger.withTag("RandomFolderPlay").i {
                "START tapped=${folder.displayName} " +
                    "mode=$mode " +
                    "tracks=${randomPlaybackSessionUris.size} " +
                    "folders=${randomPlaybackFolderSequence.size}"
            }

            playRandomPlaybackSessionIndex(0)
        }
    }

    private fun updateRecommendationsIfNeeded(changed: Track) {
        // Read-and-map inside the update lambda so a concurrent recommendations
        // write can never be clobbered with rows derived from a stale read.
        _state.update { state ->
            val rows = (state.recommendations as? DataState.Data)?.data
                ?: return@update state
            val updated = rows.map { row ->
                val items = (row.items as? DataState.Data)?.data ?: return@map row
                val updatedItems = items.map { item ->
                    if (item is Track && item.hasAnyMappingFrom(changed)) changed else item
                }
                row.copy(items = DataState.Data(updatedItems))
            }
            state.copy(recommendations = DataState.Data(updated))
        }
    }

    private fun stopJobs() {
        jobs.forEach { job -> job.cancel() }
        jobs.clear()
    }

    private fun watchPlayersData(): Job = viewModelScope.launch {
        combine(
            dataSource.playersData,
            dataSource.sendspinState,
        ) { playerData, sendspinState ->
            playerData to sendspinState
        }.collect { (playerData, sendspinState) ->
            val selectedPlayerIndex =
                dataSource.selectedPlayerIndex.value

            when (playerData) {
                is DataState.Data ->
                    selectedPlayerIndex
                        ?.let { playerData.data.getOrNull(it) }
                        ?.let(::handleRandomPlaybackNaturalEnd)

                is DataState.Stale ->
                    selectedPlayerIndex
                        ?.let { playerData.data.getOrNull(it) }
                        ?.let(::handleRandomPlaybackNaturalEnd)

                else -> Unit
            }

            // Update when in Loading or Data state
            // This allows transitioning from Loading to Data and updating existing Data
            // Don't update terminal states (Disconnected, NoAuth, NoServer)
            val currentState = _playersState.value
            if (currentState is PlayersState.Loading || currentState is PlayersState.Data) {
                _playersState.update {
                    when (playerData) {
                        is DataState.Data -> PlayersState.Data(
                            playerData.data,
                            dataSource.selectedPlayerIndex.value,
                            dataSource.localPlayer.value?.playerId,
                            sendspinState,
                        )

                        is DataState.Stale -> PlayersState.Data(
                            playerData.data,  // Show stale data as normal data
                            dataSource.selectedPlayerIndex.value,
                            dataSource.localPlayer.value?.playerId,
                            sendspinState,
                        )

                        is DataState.Error -> PlayersState.Error
                        is DataState.Loading -> PlayersState.Loading
                        is DataState.NoData -> PlayersState.Data(emptyList())
                    }
                }
            }
        }
    }

    private fun watchSelectedPlayerData(): Job = viewModelScope.launch {
        dataSource.selectedPlayerIndex.filterNotNull().collect { index ->
            val dataState = _playersState.value as? PlayersState.Data
            dataState?.let { state ->
                _playersState.update { state.copy(selectedPlayerIndex = index) }
            }
        }
    }

    fun selectPlayer(player: Player) = dataSource.selectPlayer(player)
    fun playerAction(playerId: String, action: PlayerAction) =
        dataSource.playerAction(playerId, action)

    fun playerAction(data: PlayerData, action: PlayerAction) = dataSource.playerAction(data, action)
    fun queueAction(action: QueueAction) = dataSource.queueAction(action)
    fun onPlayersSortChanged(newSort: List<String>) = dataSource.onPlayersSortChanged(newSort)
    fun openPlayerSettings(id: String) = settings.connectionInfo.value?.webUrl?.let { url ->
        onOpenExternalLink("$url/?code=${currentServerToken().orEmpty()}#/settings/editplayer/$id")
    }

    fun openPlayerDspSettings(id: String) = settings.connectionInfo.value?.webUrl?.let { url ->
        onOpenExternalLink("$url/?code=${currentServerToken().orEmpty()}#/settings/editplayer/$id/dsp")
    }

    private fun currentServerToken(): String? = when (val state = apiClient.sessionState.value) {
        is SessionState.Connected.Direct ->
            settings.getTokenForServer(
                settings.getDirectServerIdentifier(
                    state.connectionInfo.host,
                    state.connectionInfo.port,
                    state.connectionInfo.isTls,
                ),
            )

        is SessionState.Connected.WebRTC ->
            settings.getTokenForServer(settings.getWebRTCServerIdentifier(state.remoteId.rawId))

        else -> null
    }

    private fun onOpenExternalLink(url: String) = viewModelScope.launch { _links.emit(url) }

    /**
     * Persists the edited working list. Prefs for folders not currently present
     * on the server (e.g. temporarily item-less, so absent from the working list)
     * are carried over so their visibility/order isn't lost.
     */
    fun saveHomeRows(working: List<SettingsRepository.HomeRowPref>) {
        val presentIds = working.mapTo(mutableSetOf()) { it.id }
        val carriedOver = settings.homeRowsConfig.value.filterNot { it.id in presentIds }
        settings.setHomeRowsConfig(working + carriedOver)
    }

    data class State(
        val shortcuts: DataState<List<Shortcut>>,
        val recommendations: DataState<List<RecommendationRowState>>,
        val favorites: DataState<List<AppMediaItem>>,
        val randomFolders: DataState<List<RecommendationFolder>>,

        val homeRowsConfig: List<SettingsRepository.HomeRowPref> = emptyList(),
    )

    sealed class PlayersState {
        data object Loading : PlayersState()
        data object Disconnected : PlayersState()
        data object NoServer : PlayersState()
        data object NoAuth : PlayersState()
        data object Error : PlayersState()
        data class Data(
            val playerData: List<PlayerData>,
            val selectedPlayerIndex: Int? = null,
            val localPlayerId: String? = null,
            val sendspinState: SendspinState? = null,
        ) : PlayersState()
    }

    private companion object {
        private const val BUFFER_REAL_INTERVAL = 500L
        private const val RANDOM_FOLDER_COUNT = 10
    }
}

/**
 * One home-page recommendation row: the folder identity plus its items as an
 * independently loading [DataState], so each row can render a placeholder
 * while its contents are fetched.
 */
data class RecommendationRowState(
    val folder: RecommendationFolder,
    val items: DataState<List<AppMediaItem>>,
) {
    /** The row's items when resolved, or null while still loading (or on error). */
    val resolvedItems: List<AppMediaItem>? get() = (items as? DataState.Data)?.data
}
