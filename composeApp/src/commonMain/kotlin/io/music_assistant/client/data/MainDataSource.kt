// Position update intervals and debounce values inline-documented at use site.
@file:Suppress("MagicNumber")

package io.music_assistant.client.data

import androidx.compose.ui.graphics.Color
import co.touchlab.kermit.Logger
import io.music_assistant.client.api.Request
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.data.MainDataSource.Companion.resolveSelectedPlayerId
import io.music_assistant.client.data.MainDataSource.NowPlayingSnapshot.Companion.ELAPSED_ANCHOR_EPSILON_S
import io.music_assistant.client.data.factory.MediaItemFactory
import io.music_assistant.client.data.factory.PlayerFactory
import io.music_assistant.client.data.factory.QueueFactory
import io.music_assistant.client.data.model.client.ImageType
import io.music_assistant.client.data.model.client.Player
import io.music_assistant.client.data.model.client.PlayerData
import io.music_assistant.client.data.model.client.Queue
import io.music_assistant.client.data.model.client.QueueInfo
import io.music_assistant.client.data.model.client.RepeatMode
import io.music_assistant.client.data.model.client.isBefore
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.client.items.Audiobook
import io.music_assistant.client.data.model.client.items.Track
import io.music_assistant.client.data.model.client.items.image
import io.music_assistant.client.data.model.server.DspConfig
import io.music_assistant.client.data.model.server.DspConfigPreset
import io.music_assistant.client.data.model.server.ProviderManifest
import io.music_assistant.client.data.model.server.ServerPlayer
import io.music_assistant.client.data.model.server.ServerQueue
import io.music_assistant.client.data.model.server.ServerQueueItem
import io.music_assistant.client.data.model.server.events.MediaItemAddedEvent
import io.music_assistant.client.data.model.server.events.MediaItemDeletedEvent
import io.music_assistant.client.data.model.server.events.MediaItemPlayedEvent
import io.music_assistant.client.data.model.server.events.MediaItemUpdatedEvent
import io.music_assistant.client.data.model.server.events.PlayerAddedEvent
import io.music_assistant.client.data.model.server.events.PlayerRemovedEvent
import io.music_assistant.client.data.model.server.events.PlayerUpdatedEvent
import io.music_assistant.client.data.model.server.events.QueueAddedEvent
import io.music_assistant.client.data.model.server.events.QueueItemsUpdatedEvent
import io.music_assistant.client.data.model.server.events.QueueTimeUpdatedEvent
import io.music_assistant.client.data.model.server.events.QueueUpdatedEvent
import io.music_assistant.client.player.MediaPlayerController
import io.music_assistant.client.player.sendspin.SendspinClient
import io.music_assistant.client.player.sendspin.SendspinClientFactory
import io.music_assistant.client.player.sendspin.SendspinError
import io.music_assistant.client.player.sendspin.SendspinState
import io.music_assistant.client.player.sendspin.WebRTCSendspinChannelExhausted
import io.music_assistant.client.settings.SettingsRepository
import io.music_assistant.client.ui.Timings
import io.music_assistant.client.ui.compose.common.DataState
import io.music_assistant.client.ui.compose.common.StaleReason
import io.music_assistant.client.ui.compose.common.action.PlayerAction
import io.music_assistant.client.ui.compose.common.action.QueueAction
import io.music_assistant.client.ui.compose.common.icons.BookshelfIcon
import io.music_assistant.client.ui.compose.common.providers.ProviderIconModel
import io.music_assistant.client.utils.AuthProcessState
import io.music_assistant.client.utils.DataConnectionState
import io.music_assistant.client.utils.SessionState
import io.music_assistant.client.utils.currentTimeMillis
import io.music_assistant.client.utils.resultAs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.CoroutineContext

@OptIn(FlowPreview::class)
class MainDataSource(
    private val settings: SettingsRepository,
    val apiClient: ServiceClient,
    private val mediaPlayerController: MediaPlayerController,
    private val sendspinClientFactory: SendspinClientFactory,
    private val localPlayerRepository: LocalPlayerRepository,
    private val mediaItemFactory: MediaItemFactory,
    private val playerFactory: PlayerFactory,
    private val queueFactory: QueueFactory,
) : CoroutineScope {
    private val log = Logger.withTag("MainDataSource")

    private var sendspinClient: SendspinClient? = null
    private var sendspinMonitorJobs = mutableListOf<Job>()
    private var sendspinRetryCount = 0
    private val sendspinMutex = Mutex()

    private val _sendspinState = MutableStateFlow<SendspinState?>(null)
    val sendspinState: StateFlow<SendspinState?> = _sendspinState.asStateFlow()

    private val supervisorJob = SupervisorJob()
    override val coroutineContext: CoroutineContext = supervisorJob + Dispatchers.IO

    private val _serverPlayers = MutableStateFlow<DataState<List<Player>>>(DataState.Loading())
    private val _queueInfos = MutableStateFlow<List<QueueInfo>>(emptyList())
    private val _providersIcons = MutableStateFlow<Map<String, ProviderIconModel>>(emptyMap())

    /**
     * Single source of truth for live elapsed-time per queue. Server events
     * write anchors here, play/pause transitions snapshot the interpolated
     * position. All consumers (in-app slider, MediaSession writes for AA +
     * notification, iOS NowPlaying, audiobook chapter logic) read from this
     * tracker — synchronously via [PlayerPositionTracker.effectiveSec] or as
     * a smoothly-ticking flow via [PlayerPositionTracker.observe].
     */
    val positionTracker: PlayerPositionTracker = PlayerPositionTracker()

    private val _players =
        combine(_serverPlayers, settings.playersSorting) { playersState, sortedIds ->
            when (playersState) {
                is DataState.Error,
                is DataState.Loading,
                is DataState.NoData,
                    -> playersState

                is DataState.Data -> {
                    val players = playersState.data
                    DataState.Data(
                        sortedIds?.let {
                            players.sortedBy { player ->
                                sortedIds.indexOf(player.id).takeIf { it >= 0 }
                                    ?: Int.MAX_VALUE
                            }
                        } ?: players.sortedBy { player -> player.name },
                    )
                }

                is DataState.Stale -> {
                    // Preserve stale state with sorted data
                    val players = playersState.data
                    DataState.Stale(
                        data = sortedIds?.let {
                            players.sortedBy { player ->
                                sortedIds.indexOf(player.id).takeIf { it >= 0 }
                                    ?: Int.MAX_VALUE
                            }
                        } ?: players.sortedBy { player -> player.name },
                        disconnectedAt = playersState.disconnectedAt,
                        reason = playersState.reason,
                    )
                }
            }
        }.stateIn(
            scope = this,
            started = SharingStarted.Eagerly,
            initialValue = DataState.Loading(),
        )

    private val _playersData = MutableStateFlow<DataState<List<PlayerData>>>(DataState.Loading())
    val playersData = _playersData.asStateFlow()

    val localPlayer = localPlayerRepository.localPlayerData

    val isAnythingPlaying =
        playersData
            .mapNotNull { it as? DataState.Data<List<PlayerData>> }
            .map { it.data.any { data -> data.player.isPlaying } }
            .stateIn(this, SharingStarted.Eagerly, false)
    val doesAnythingHavePlayableItem =
        playersData
            .mapNotNull { it as? DataState.Data<List<PlayerData>> }
            .map { it.data.any { data -> data.queueInfo?.currentItem != null } }
            .stateIn(this, SharingStarted.Eagerly, false)

    /**
     * Persisted user choice. Restored from settings on startup and written
     * only by [selectPlayer]; the persistence collector in [init] mirrors
     * non-null values into [SettingsRepository.lastSelectedPlayerId] for
     * the next app launch.
     */
    private val _userSelectedPlayerId =
        MutableStateFlow(settings.lastSelectedPlayerId.value)

    /**
     * Effective selection consumed by the rest of the data source and UI.
     * Derived from the current player list and the user choice via
     * [resolveSelectedPlayerId] — pure function, re-evaluated on every
     * input change. No state machine pushes a fallback into the upstream
     * flow; the resolver computes it on the fly.
     */
    private val _selectedPlayerId: StateFlow<String?> =
        combine(
            _playersData,
            _userSelectedPlayerId,
        ) { playersDataState, user ->
            val visibleIds = (playersDataState as? DataState.Data)
                ?.data?.map { it.playerId }
                .orEmpty()
            resolveSelectedPlayerId(
                visiblePlayerIds = visibleIds,
                userChoice = user,
            )
        }.stateIn(this, SharingStarted.Eagerly, settings.lastSelectedPlayerId.value)

    val selectedPlayerIndex = combine(_playersData, _selectedPlayerId) { listState, selectedId ->
        selectedId?.let { id ->
            (listState as? DataState.Data)?.data?.indexOfFirst { it.playerId == id }
                ?.takeIf { it >= 0 }
        }
    }.stateIn(this, SharingStarted.Eagerly, null)

    val selectedPlayer: PlayerData?
        get() = selectedPlayerIndex.value?.let { selectedIndex ->
            (_playersData.value as? DataState.Data)?.data?.getOrNull(selectedIndex)
        }

    fun providerIcon(provider: String): ProviderIconModel? =
        _providersIcons.value[provider.substringBefore("--")]

    private var watchJob: Job? = null
    private var updateJob: Job? = null

    init {
        // Mirror optimistic-bump stamps into `_queueInfos` so the gate sees them.
        launch {
            localPlayerRepository.optimisticQueueChanges.collect { queueInfo ->
                _queueInfos.update { value ->
                    if (value.any { it.id == queueInfo.id }) {
                        value.map { if (it.id == queueInfo.id) queueInfo else it }
                    } else {
                        value + queueInfo
                    }
                }
                queueInfo.elapsedTime?.let {
                    positionTracker.setAnchor(
                        queueId = queueInfo.id,
                        elapsedSec = it,
                        durationSec = queueInfo.currentItem?.track?.duration,
                    )
                }
            }
        }

        // Mirror play state into the tracker. setPlaying snapshots the
        // interpolated position on transitions so pause/resume don't fold
        // pause-duration into the next forward step. Cheap dedup inside.
        launch {
            playersData
                .mapNotNull { (it as? DataState.Data)?.data }
                .collect { list ->
                    list.forEach { pd ->
                        pd.queueInfo?.id?.let { queueId ->
                            positionTracker.setPlaying(queueId, pd.player.isPlaying)
                        }
                    }
                }
        }

        launch {
            combine(
                _players,
                _queueInfos,
                localPlayerRepository.localPlayerData,
            ) { players, queues, localData -> Triple(players, queues, localData) }
                .debounce(Timings.EVENT_DEBOUNCE) // Small debounce to batch rapid updates, but don't delay initial load
                .collect { (playersState, queues, localData) ->
                    _playersData.update { oldValues ->
                        when (playersState) {
                            is DataState.Error -> DataState.Error()
                            is DataState.Loading -> DataState.Loading()
                            is DataState.NoData -> DataState.NoData()
                            is DataState.Data -> DataState.Data(
                                buildPlayerDataList(
                                    playersState.data,
                                    queues,
                                    localData,
                                    oldValues,
                                ),
                            )

                            is DataState.Stale -> DataState.Stale(
                                data = buildPlayerDataList(
                                    playersState.data,
                                    queues,
                                    localData,
                                    oldValues,
                                ),
                                disconnectedAt = playersState.disconnectedAt,
                                reason = playersState.reason,
                            )
                        }
                    }
                }
        }
        launch {
            apiClient.sessionState.collect { sessionState ->
                log.i { "SessionState changed: ${sessionState::class.simpleName}" }

                when (sessionState) {
                    is SessionState.Connected -> {
                        // Start watching events (cancel old job if exists to avoid duplicates)
                        watchJob?.cancel()
                        watchJob = watchApiEvents()

                        if (sessionState.dataConnectionState == DataConnectionState.Authenticated) {
                            when (val currentState = _serverPlayers.value) {
                                is DataState.Stale -> {
                                    log.i { "Recovering from ${currentState.reason} stale state" }

                                    when (currentState.reason) {
                                        StaleReason.RECONNECTING -> {
                                            // Brief disconnection — data is still fresh. Transition
                                            // stale → Data without re-fetching to avoid a UI blink.
                                            // This branch only runs when dataConnectionState is
                                            // already Authenticated; if a reconnect requires re-auth,
                                            // the else branch below preserves Stale data until
                                            // AuthenticationManager finishes re-authorizing.
                                            log.i { "Seamless recovery - reusing cached data" }
                                            _serverPlayers.update {
                                                DataState.Data(currentState.data)
                                            }

                                            // selectedPlayerIndex doesn't re-emit on stale-recovery
                                            // (same value before/after), so refresh manually.
                                            refreshSelectedPlayerQueueItems()

                                            // Sendspin reinit: WebRTC sendspin auth is inherited
                                            // from the data channel itself, not JSON-RPC auth state,
                                            // so it must be re-driven independently of the main
                                            // connection's auth lifecycle.
                                            launch { initSendspinIfEnabled() }

                                            // Drain any commands queued while disconnected
                                            localPlayerRepository.drainCommandQueue()
                                        }

                                        StaleReason.PERSISTENT_ERROR -> {
                                            // Long disconnection - fetch fresh data
                                            log.i { "Recovery from persistent error - fetching fresh data" }
                                            _serverPlayers.update {
                                                DataState.Data(currentState.data)
                                            }
                                            updateProvidersManifests()
                                            initSendspinIfEnabled()
                                            updatePlayersAndQueues()
                                            localPlayerRepository.drainCommandQueue()
                                        }
                                    }
                                }

                                is DataState.Data -> {
                                    // Already have data (shouldn't happen, but handle gracefully)
                                    log.w { "Connected while already in Data state - refreshing anyway" }
                                    updateProvidersManifests()
                                    updatePlayersAndQueues()
                                    refreshSelectedPlayerQueueItems()
                                    // Safety net: reinit Sendspin if it's not already connected.
                                    // Factory detects channel freshness from the DataChannelWrapper
                                    // identity, so no manual reset needed here.
                                    launch { initSendspinIfEnabled() }
                                }

                                is DataState.Loading, is DataState.NoData, is DataState.Error -> {
                                    // Fresh connection or error recovery - show loading
                                    _serverPlayers.update { DataState.Loading() }
                                    updateProvidersManifests()
                                    initSendspinIfEnabled()
                                    updatePlayersAndQueues()
                                }
                            }
                        } else {
                            // Not authenticated yet
                            val connState = sessionState.dataConnectionState
                            val isTerminalAuthFailure =
                                connState is DataConnectionState.AwaitingAuth &&
                                        (
                                                connState.authProcessState is AuthProcessState.LoggedOut ||
                                                        connState.authProcessState is AuthProcessState.Failed
                                                )

                            if (isTerminalAuthFailure) {
                                // Auth permanently failed — stop everything
                                stopSendspin()
                                clearAllData()
                            } else {
                                // Transient: AwaitingServerInfo or auth in progress.
                                // Keep sendspin alive — it will be reinitialized when auth completes.
                                // Preserve stale data so reconnection recovery works.
                                if (_serverPlayers.value !is DataState.Stale) {
                                    clearAllData()
                                }
                            }
                            updateJob?.cancel()
                            updateJob = null
                            watchJob?.cancel()
                            watchJob = null
                        }
                    }

                    is SessionState.Reconnecting -> {
                        when (val currentState = _serverPlayers.value) {
                            is DataState.Data -> {
                                // Transition to Stale(RECONNECTING) - preserve data
                                log.i { "Data → Stale(RECONNECTING): preserving ${(currentState.data as? List<*>)?.size ?: 0} players" }
                                _serverPlayers.update {
                                    DataState.Stale(
                                        data = currentState.data,
                                        disconnectedAt = currentTimeMillis(),
                                        reason = StaleReason.RECONNECTING,
                                    )
                                }
                            }

                            is DataState.Stale -> {
                                // Already stale - update reason if needed, preserve original disconnectedAt
                                if (currentState.reason != StaleReason.RECONNECTING) {
                                    log.i { "Stale(${currentState.reason}) → Stale(RECONNECTING)" }
                                    _serverPlayers.update {
                                        DataState.Stale(
                                            data = currentState.data,
                                            disconnectedAt = currentState.disconnectedAt,  // KEEP ORIGINAL
                                            reason = StaleReason.RECONNECTING,
                                        )
                                    }
                                }
                                // else: already Stale(RECONNECTING), do nothing
                            }

                            is DataState.Loading, is DataState.NoData, is DataState.Error -> {
                                // No data to preserve - stay in current state
                                log.d { "Reconnecting with no data to preserve (state: ${currentState::class.simpleName})" }
                            }
                        }

                        // KEEP: Sendspin alive, jobs running, position tracking active
                        // watchJob will be idle (no events from disconnected WebSocket)
                        // updateJob keeps running for position calculations
                    }

                    SessionState.Connecting -> {
                        log.i { "Connecting - stopping Sendspin" }
                        stopSendspin()
                        updateJob?.cancel()
                        updateJob = null
                        watchJob?.cancel()
                        watchJob = null
                        // Preserve stale data (e.g. reconnecting from backgrounded state)
                        // so the player list stays visible instead of showing "Loading players"
                        when (val current = _serverPlayers.value) {
                            is DataState.Data -> _serverPlayers.update {
                                DataState.Stale(
                                    current.data,
                                    currentTimeMillis(),
                                    StaleReason.RECONNECTING,
                                )
                            }

                            is DataState.Stale -> {} // Already stale, keep as is
                            else -> _serverPlayers.update { DataState.Loading() }
                        }
                    }

                    is SessionState.Disconnected -> {
                        when (sessionState) {
                            SessionState.Disconnected.ByUser -> {
                                // Intentional logout - clear everything
                                log.i { "Disconnected by user - clearing all data" }
                                stopSendspin()
                                clearAllData()
                                updateJob?.cancel()
                                updateJob = null
                                watchJob?.cancel()
                                watchJob = null
                            }

                            is SessionState.Disconnected.Error -> {
                                // Persistent error after max reconnect attempts
                                when (val currentState = _serverPlayers.value) {
                                    is DataState.Data, is DataState.Stale -> {
                                        // Preserve data as Stale(PERSISTENT_ERROR)
                                        val data = when (currentState) {
                                            is DataState.Data -> currentState.data
                                            is DataState.Stale -> currentState.data
                                        }
                                        val originalDisconnectedAt =
                                            (currentState as? DataState.Stale)?.disconnectedAt
                                                ?: currentTimeMillis()

                                        val staleCount = (data as? List<*>)?.size ?: 0
                                        log.w { "Persistent connection error - preserving $staleCount players as stale" }
                                        _serverPlayers.update {
                                            DataState.Stale(
                                                data = data,
                                                disconnectedAt = originalDisconnectedAt,  // Preserve original
                                                reason = StaleReason.PERSISTENT_ERROR,
                                            )
                                        }

                                        // Stop Sendspin (can't stream without connection)
                                        stopSendspin()
                                    }

                                    is DataState.Loading, is DataState.NoData, is DataState.Error -> {
                                        // No data to preserve - transition to NoData
                                        log.w { "Persistent error with no data to preserve" }
                                        _serverPlayers.update { DataState.NoData() }
                                        _queueInfos.update { emptyList() }
                                    }
                                }

                                // Cancel jobs (no point running without connection)
                                updateJob?.cancel()
                                updateJob = null
                                watchJob?.cancel()
                                watchJob = null
                            }

                            SessionState.Disconnected.Backgrounded -> {
                                // App backgrounded — preserve data for instant foreground reconnect
                                when (val currentState = _serverPlayers.value) {
                                    is DataState.Data -> {
                                        log.i { "Backgrounded — preserving ${currentState.data.size} players as Stale(RECONNECTING)" }
                                        _serverPlayers.update {
                                            DataState.Stale(
                                                data = currentState.data,
                                                disconnectedAt = currentTimeMillis(),
                                                reason = StaleReason.RECONNECTING,
                                            )
                                        }
                                    }

                                    is DataState.Stale -> {
                                        log.i { "Backgrounded — already stale, keeping data" }
                                    }

                                    else -> {
                                        log.d { "Backgrounded with no data to preserve" }
                                    }
                                }

                                stopSendspin()
                                updateJob?.cancel()
                                updateJob = null
                                watchJob?.cancel()
                                watchJob = null
                            }

                            SessionState.Disconnected.Initial, SessionState.Disconnected.NoServerData -> {
                                // App startup or no server configured - clear all
                                log.i { "Disconnected (${sessionState::class.simpleName}) - clearing data" }
                                stopSendspin()
                                clearAllData()
                                updateJob?.cancel()
                                updateJob = null
                                watchJob?.cancel()
                                watchJob = null
                            }
                        }
                    }
                }
            }
        }
        launch {
            // Persist user-driven selection so it survives app restarts.
            // Only [selectPlayer] writes the upstream flow, so this captures
            // explicit picks. Nulls are filtered out because clearing the
            // persisted value isn't a product requirement and rarely the
            // user's intent.
            _userSelectedPlayerId
                .filterNotNull()
                .distinctUntilChanged()
                .collect { settings.setLastSelectedPlayerId(it) }
        }
        launch {
            selectedPlayerIndex.filterNotNull().collect { index ->
                // Only refresh queue if we have live data and are authenticated
                // Don't try to load during Stale state - will error with auth issues
                if (apiClient.isReadyForCommands.value) {
                    (playersData.value as? DataState.Data)?.data?.let { list ->
                        refreshPlayerQueueItems(list[index])
                    }
                } else {
                    log.d { "Skipping queue refresh - not authenticated (state: ${apiClient.sessionState.value::class.simpleName})" }
                }
            }
        }

        // Watch for Sendspin settings changes
        launch {
            settings.sendspinEnabled.collect { enabled ->
                if (apiClient.sessionState.value is SessionState.Connected) {
                    if (enabled) {
                        initSendspinIfEnabled()
                        // Inject synthetic player immediately so UI reflects the change
                        // before Sendspin fully connects and server confirms the player
                        localPlayerRepository.onInitialPlayersReceived(hasLocalPlayer = false)
                    } else {
                        stopSendspin()
                    }
                }
            }
        }

        // Reset clock sync on foreground unless playback held CPU awake through
        // the background — otherwise doze-paused CLOCK_MONOTONIC strands the offset.
        launch {
            apiClient.foregroundEvents.collect {
                val streaming = sendspinClient?.state?.value.let {
                    it is SendspinState.Synchronized || it is SendspinState.Buffering
                }
                if (streaming) return@collect
                sendspinClientFactory.currentClockSynchronizer()?.let {
                    log.i { "Foreground from idle — resetting clock sync" }
                    it.reset()
                }
            }
        }
        // Keep Now Playing (iOS Control Center / Lock Screen) in sync with the
        // local player. iOS interpolates the playback bar internally from the
        // `(elapsed, timestamp, rate)` triple on every `setNowPlayingInfo`
        // call; per Apple's guidance the right pattern is one anchor write
        // per server event, plus track/rate transitions, and let iOS take
        // it from there. So we drive this off `localPlayer` — which re-emits
        // on track change, play/pause, and server queue event — and dedupe
        // via [NowPlayingSnapshot.sameDictWriteWouldBe] so sub-second
        // position jitter doesn't cause a write per tick. Mid-pause
        // `elapsed_time = null` events flow through as `null` and the iOS
        // adapter's skip-on-nil semantics preserve the previous anchor.
        launch {
            localPlayer
                .map { pd ->
                    val track = pd?.queueInfo?.currentItem?.track
                    if (track == null) {
                        NowPlayingSnapshot.Cleared
                    } else {
                        NowPlayingSnapshot.Active(
                            title = track.displayName,
                            artist = track.subtitle,
                            album = track.parentName,
                            artworkUrl = track.image(ImageType.THUMB)?.url,
                            duration = track.duration,
                            // Read live position from the tracker rather than the stale
                            // anchor on `pd.queueInfo` (which is only updated by
                            // QueueAdded/UpdatedEvent, not by QueueTimeUpdatedEvent).
                            elapsedTime = pd.queueInfo.id.let {
                                positionTracker.effectiveSec(it)
                            } ?: pd.queueInfo.elapsedTime,
                            isPlaying = pd.player.isPlaying,
                        )
                    }
                }
                .distinctUntilChanged { a, b -> NowPlayingSnapshot.sameDictWriteWouldBe(a, b) }
                .collect { snapshot ->
                    when (snapshot) {
                        NowPlayingSnapshot.Cleared -> mediaPlayerController.clearNowPlaying()
                        is NowPlayingSnapshot.Active -> mediaPlayerController.updateNowPlaying(
                            title = snapshot.title,
                            artist = snapshot.artist,
                            album = snapshot.album,
                            artworkUrl = snapshot.artworkUrl,
                            duration = snapshot.duration,
                            elapsedTime = snapshot.elapsedTime,
                            playbackRate = if (snapshot.isPlaying) 1.0 else 0.0,
                        )
                    }
                }
        }
    }

    /**
     * Captures the fields a single emission would push to iOS's Now Playing
     * dict — [Active] when the local player has a track, [Cleared] when it
     * doesn't. Paired with [Companion.sameDictWriteWouldBe] as a
     * [distinctUntilChanged] key so the flow only emits once per visible
     * anchor change (new track, pause/play, real elapsed jump).
     */
    internal sealed interface NowPlayingSnapshot {
        data object Cleared : NowPlayingSnapshot
        data class Active(
            val title: String?,
            val artist: String?,
            val album: String?,
            val artworkUrl: String?,
            val duration: Double?,
            val elapsedTime: Double?,
            val isPlaying: Boolean,
        ) : NowPlayingSnapshot

        companion object {
            /**
             * Threshold (seconds) below which two `elapsed` values are treated
             * as the same anchor: iOS's own interpolator covers sub-second
             * drift from `(elapsed, timestamp, rate)`, so writing a fresh
             * value within this window is a no-op the user can't see.
             *
             * 2 s is comfortably wider than typical position-tracker jitter
             * (we tick at 500 ms with ±50 ms of dispatch noise) and tighter
             * than any user-visible seek.
             */
            internal const val ELAPSED_ANCHOR_EPSILON_S = 2.0

            /**
             * Returns `true` when [a] and [b] would produce indistinguishable
             * `MPNowPlayingInfoCenter` dict writes — i.e. emitting [b] after
             * [a] would not visibly change the lock screen / CarPlay bar.
             *
             * Most fields compare by value equality. `elapsedTime` is the
             * exception: small drifts (within [ELAPSED_ANCHOR_EPSILON_S]) are
             * treated as equal because iOS is already interpolating from the
             * last anchor. Crossing the threshold (e.g. a server-side seek,
             * a reconnect re-anchoring with a wildly different value) emits.
             */
            fun sameDictWriteWouldBe(a: NowPlayingSnapshot, b: NowPlayingSnapshot): Boolean {
                if (a !is Active || b !is Active) return a === b
                if (a.title != b.title) return false
                if (a.artist != b.artist) return false
                if (a.album != b.album) return false
                if (a.artworkUrl != b.artworkUrl) return false
                if (a.duration != b.duration) return false
                if (a.isPlaying != b.isPlaying) return false
                val ae = a.elapsedTime
                val be = b.elapsedTime
                return when {
                    ae == null && be == null -> true
                    ae == null || be == null -> false
                    else -> kotlin.math.abs(ae - be) < ELAPSED_ANCHOR_EPSILON_S
                }
            }
        }
    }

    /**
     * Build the merged player data list for [_playersData].
     * Local player uses repository state (single source of truth); others built from server data.
     */
    private fun buildPlayerDataList(
        allPlayers: List<Player>,
        queues: List<QueueInfo>,
        localData: PlayerData?,
        oldValues: DataState<List<PlayerData>>,
    ): List<PlayerData> {
        val localPlayerId = settings.sendspinClientId.value
        val playerDataList = allPlayers
            .map { player ->
                val isLocal = player.id == localPlayerId
                val parent =
                    (player.activeGroup ?: player.syncedTo)
                        ?.let { parentId -> allPlayers.firstOrNull { it.id == parentId } }
                        ?.asParentBind()
                val groupChildren =
                    // No children for local player or if player is part of group
                    if (isLocal || parent != null) {
                        emptyList()
                    } else {
                        allPlayers.mapNotNull { it.asChildBindFor(player) }
                    }
                if (isLocal && localData != null) {
                    // Repository is source of truth for the local player; surface the
                    // latest server-anchored `elapsedTime` from `_queueInfos` so the slider
                    // re-anchors on `QueueTimeUpdatedEvent` (which writes only to
                    // `_queueInfos`, not the repository). The slider does its own
                    // sub-second interpolation locally — see `rememberInterpolatedPosition`.
                    val trackedElapsed = queues.find {
                        it.id == player.queueId || it.id == localPlayerId
                    }?.elapsedTime
                    val withPosition = trackedElapsed?.let {
                        (localData.queue as? DataState.Data)?.let { qd ->
                            localData.copy(
                                queue = DataState.Data(
                                    qd.data.copy(info = qd.data.info.copy(elapsedTime = it)),
                                ),
                                parentBind = parent,
                            )
                        }
                    } ?: localData
                    // Preserve loaded queue items from previous state
                    (oldValues as? DataState.Data)?.data
                        ?.firstOrNull { it.player.id == player.id }
                        ?.updateFrom(withPosition) ?: withPosition
                } else {
                    val newData = PlayerData(
                        player = player,
                        queue = queues.find { it.id == player.queueId }
                            ?.let { queueInfo ->
                                DataState.Data(
                                    Queue(info = queueInfo, items = DataState.NoData()),
                                )
                            } ?: DataState.NoData(),
                        parentBind = parent,
                        childrenBinds = groupChildren,
                        isLocal = isLocal,
                    )
                    (oldValues as? DataState.Data)?.data
                        ?.firstOrNull { it.player.id == player.id }
                        ?.updateFrom(newData) ?: newData
                }
            }

        // Inject synthetic local player if not in server list
        return if (localData != null && playerDataList.none { it.playerId == localPlayerId }) {
            listOf(localData) + playerDataList
        } else {
            playerDataList
        }
    }

    /**
     * Clear all cached data.
     */
    private fun clearAllData() {
        log.i { "Clearing all cached data" }
        _serverPlayers.update { DataState.NoData() }
        _queueInfos.update { emptyList() }
        positionTracker.clear()
        localPlayerRepository.clearState()
        // Note: _providersIcons deliberately NOT cleared (static data)
    }

    /**
     * Initialize Sendspin player if enabled in settings.
     * Safe for background: MainDataSource is singleton held by foreground service.
     */
    private suspend fun initSendspinIfEnabled() = sendspinMutex.withLock {
        // Get prerequisites
        val authToken = when (val state = apiClient.sessionState.value) {
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

        // Stop existing client if any (but preserve if it's actively connected, connecting, or reconnecting)
        sendspinClient?.let { existing ->
            when (val state = existing.state.value) {
                is SendspinState.Ready,
                is SendspinState.Buffering,
                is SendspinState.Synchronized,
                is SendspinState.Connecting,
                is SendspinState.Authenticating,
                is SendspinState.Handshaking,
                    -> {
                    return@withLock
                }

                is SendspinState.Reconnecting -> {
                    return@withLock
                }

                is SendspinState.Error -> {
                    val errorMsg = when (val err = state.error) {
                        is SendspinError.Permanent -> err.userAction
                        is SendspinError.Transient -> err.cause.message
                        is SendspinError.Degraded -> err.reason
                    }
                    log.w { "Sendspin: REINIT — Error: $errorMsg" }
                }

                is SendspinState.Idle -> Unit
            }
            existing.stop()
            existing.close()
        }

        // Create client using factory
        val createResult = sendspinClientFactory.createIfEnabled(
            mainConnection = settings.connectionInfo.value,
            authToken = authToken,
        )

        createResult.onFailure { error ->
            if (error is WebRTCSendspinChannelExhausted) {
                log.i { "WebRTC sendspin channel exhausted — forcing reconnect for fresh channels" }
                apiClient.forceWebRTCReconnect()
                // After reconnection, initSendspinIfEnabled() will be called again
                // from the session state handler with a fresh channel.
                return@withLock
            }
            log.w { "Cannot create Sendspin client: ${error.message}" }
            return@withLock
        }

        val client = createResult.getOrNull() ?: return@withLock

        // Set up remote command handler for Control Center/Lock Screen commands
        // Go directly through MainDataSource via REST API
        mediaPlayerController.onRemoteCommand = { command ->
            localPlayer.value?.let { playerData ->
                log.i { "Remote command from Control Center: $command" }
                when (command) {
                    "play" -> playerAction(playerData, PlayerAction.Play)
                    "pause" -> playerAction(playerData, PlayerAction.Pause)
                    "toggle_play_pause" -> playerAction(
                        playerData,
                        PlayerAction.TogglePlayPause,
                    )

                    "next" -> playerAction(playerData, PlayerAction.Next)
                    "previous" -> playerAction(playerData, PlayerAction.Previous)
                    else -> {
                        if (command.startsWith("seek:")) {
                            command.removePrefix("seek:").toDoubleOrNull()?.let { position ->
                                playerAction(playerData, PlayerAction.SeekTo(position.toLong()))
                            }
                        } else {
                            log.w { "Unknown remote command: $command" }
                        }
                    }
                }
            } ?: log.w { "No local player available for remote command: $command" }
        }

        // Monitor client lifecycle
        sendspinClient = client
        monitorSendspinClient(client)

        // Client is already started by factory (detects WebRTC vs WebSocket automatically)
        log.i { "Sendspin client initialized and started" }
    }

    /**
     * Monitor Sendspin client for errors and state changes.
     */
    private fun monitorSendspinClient(client: SendspinClient) {
        // Cancel any previous monitoring jobs to prevent old clients from
        // leaking state transitions into _sendspinState or triggering pipeline disconnects
        cancelSendspinMonitorJobs()

        sendspinMonitorJobs += launch {
            // Monitor for playback errors (e.g., Android Auto disconnect, audio output changed)
            // and pause the MA server player when they occur
            client.playbackStoppedDueToError.filterNotNull().collect { _ ->
                // Pause the local sendspin player on the MA server
                localPlayer.value?.let { playerData ->
                    if (playerData.player.isPlaying) {
                        playerAction(playerData, PlayerAction.Pause)
                    }
                }
            }
        }

        sendspinMonitorJobs += launch {
            client.state.collect { state ->
                _sendspinState.value = state
                when (state) {
                    is SendspinState.Ready -> {
                        sendspinRetryCount = 0
                        delay(1000) // Give server a moment to register the player
                        updatePlayersAndQueues()
                    }

                    is SendspinState.Error -> {
                        sendspinClientFactory.getOrCreatePipeline().first.onNetworkDisconnected()

                        // Retry if error is not being auto-retried and main API is connected
                        val shouldRetry = when (state.error) {
                            is SendspinError.Permanent -> true
                            is SendspinError.Transient -> !state.error.willRetry
                            is SendspinError.Degraded -> false
                        }

                        if (shouldRetry && sendspinRetryCount < MAX_SENDSPIN_RETRIES) {
                            if (apiClient.isReadyForCommands.value && settings.sendspinEnabled.value) {
                                sendspinRetryCount++
                                val backoffMs = 5000L * sendspinRetryCount
                                delay(backoffMs)
                                // Re-check after delay (conditions may have changed)
                                val stillValid =
                                    apiClient.isReadyForCommands.value && settings.sendspinEnabled.value
                                if (stillValid) {
                                    try {
                                        initSendspinIfEnabled()
                                    } catch (_: Exception) {
                                        coroutineContext.ensureActive()
                                    }
                                }
                            }
                        }
                    }

                    is SendspinState.Idle -> {
                        sendspinClientFactory.getOrCreatePipeline().first.onNetworkDisconnected()
                    }

                    is SendspinState.Reconnecting -> Unit
                    else -> Unit
                }
            }
        }
    }

    private fun cancelSendspinMonitorJobs() {
        if (sendspinMonitorJobs.isNotEmpty()) {
            sendspinMonitorJobs.forEach { it.cancel() }
            sendspinMonitorJobs.clear()
        }
    }

    /**
     * Stop Sendspin player if running.
     * Destroys the shared audio pipeline so the AudioTrack is fully released.
     */
    private suspend fun stopSendspin() = sendspinMutex.withLock {
        // Cancel monitor jobs FIRST to prevent old state transitions from leaking
        cancelSendspinMonitorJobs()
        sendspinRetryCount = 0
        sendspinClient?.let { client ->
            try {
                client.stop()
                client.close()
            } catch (e: Exception) {
                log.e(e) { "Error stopping Sendspin client" }
            }
            sendspinClient = null
        }
        _sendspinState.value = null
        // Clear local player data immediately so the UI reflects the change
        localPlayerRepository.clearState()
        // Fully release the shared audio pipeline (AudioTrack, decoder, etc.)
        // A fresh pipeline will be created on the next initSendspinIfEnabled()
        sendspinClientFactory.destroyPipeline()
    }

    suspend fun getDspConfig(playerId: String): DspConfig? =
        apiClient.sendRequest(Request.Dsp.getPlayerConfig(playerId))
            .getOrNull()?.resultAs<DspConfig>()

    suspend fun saveDspConfig(playerId: String, config: DspConfig): DspConfig? {
        return apiClient.sendRequest(Request.Dsp.savePlayerConfig(playerId, config))
            .getOrNull()?.resultAs<DspConfig>()
    }

    suspend fun getDspPresets(): List<DspConfigPreset> =
        apiClient.sendRequest(Request.Dsp.getPresets())
            .getOrNull()?.resultAs<List<DspConfigPreset>>() ?: emptyList()

    fun selectPlayer(player: Player) {
        // User-driven selection (UI player picker). Writes through
        // [_userSelectedPlayerId] so the choice persists; the persistence
        // launch in [init] mirrors it into [SettingsRepository] for the next
        // app launch.
        _userSelectedPlayerId.update { player.id }
    }

    /** `null` if this event is older than what `_queueInfos` already holds for the same id. */
    private fun QueueInfo.takeIfNotStale(label: String): QueueInfo? {
        val existing = _queueInfos.value.find { it.id == id } ?: return this
        if (isBefore(existing)) {
            log.d {
                "Dropping stale $label for $id: $elapsedTimeLastUpdated < ${existing.elapsedTimeLastUpdated}"
            }
            return null
        }
        return this
    }

    fun playerAction(playerId: String, action: PlayerAction) {
        // Delegate to data-based overload for local player (handles optimistic + routing)
        if (playerId == settings.sendspinClientId.value) {
            localPlayerRepository.localPlayerData.value?.let { localData ->
                playerAction(localData, action)
                return
            }
        }
        launch {
            when (action) {
                PlayerAction.TogglePlayPause -> {
                    apiClient.sendRequest(
                        Request.Player.simpleCommand(
                            playerId = playerId,
                            command = "play_pause",
                        ),
                    )
                }

                PlayerAction.Play -> {
                    apiClient.sendRequest(
                        Request.Player.simpleCommand(
                            playerId = playerId,
                            command = "play",
                        ),
                    )
                }

                PlayerAction.Pause -> {
                    apiClient.sendRequest(
                        Request.Player.simpleCommand(
                            playerId = playerId,
                            command = "pause",
                        ),
                    )
                }

                PlayerAction.Next -> {
                    apiClient.sendRequest(
                        Request.Player.simpleCommand(playerId = playerId, command = "next"),
                    )
                }

                PlayerAction.Previous -> {
                    apiClient.sendRequest(
                        Request.Player.simpleCommand(
                            playerId = playerId,
                            command = "previous",
                        ),
                    )
                }

                is PlayerAction.SeekTo -> {
                    Logger.e("SeekTo: ${action.position}")
                    apiClient.sendRequest(
                        Request.Player.seek(
                            queueId = playerId,
                            position = action.position,
                        ),
                    )
                }

                PlayerAction.VolumeDown -> apiClient.sendRequest(
                    Request.Player.simpleCommand(
                        playerId = playerId,
                        command = "volume_down",
                    ),
                )

                PlayerAction.VolumeUp -> apiClient.sendRequest(
                    Request.Player.simpleCommand(
                        playerId = playerId,
                        command = "volume_up",
                    ),
                )

                is PlayerAction.ToggleMute -> apiClient.sendRequest(
                    Request.Player.setMute(playerId = playerId, !action.isMutedNow),
                )

                is PlayerAction.VolumeSet -> apiClient.sendRequest(
                    Request.Player.setVolume(
                        playerId = playerId,
                        volumeLevel = action.level,
                    ),
                )

                PlayerAction.GroupVolumeDown -> apiClient.sendRequest(
                    Request.Player.simpleCommand(
                        playerId = playerId,
                        command = "group_volume_down",
                    ),
                )

                PlayerAction.GroupVolumeUp -> apiClient.sendRequest(
                    Request.Player.simpleCommand(
                        playerId = playerId,
                        command = "group_volume_up",
                    ),
                )

                is PlayerAction.GroupToggleMute -> apiClient.sendRequest(
                    Request.Player.setGroupMute(playerId = playerId, !action.isMutedNow),
                )

                is PlayerAction.GroupVolumeSet -> apiClient.sendRequest(
                    Request.Player.setGroupVolume(
                        playerId = playerId,
                        volumeLevel = action.level,
                    ),
                )

                is PlayerAction.GroupManage -> apiClient.sendRequest(
                    Request.Player.setGroupMembers(
                        playerId = playerId,
                        playersToAdd = action.toAdd,
                        playersToRemove = action.toRemove,
                    ),
                )

                else -> Unit
            }
        }
    }

    fun playerAction(data: PlayerData, action: PlayerAction) {
        // Apply optimistic update for local player (immediate, before async command)
        if (data.isLocal) {
            localPlayerRepository.applyOptimisticUpdate(data, action)
        }
        launch {
            val request = buildPlayerRequest(data, action) ?: return@launch
            if (data.isLocal) {
                localPlayerRepository.sendOrQueue(action, request)
            } else {
                val result = apiClient.sendRequest(request)
                if (result.isFailure) {
                    log.e(
                        result.exceptionOrNull(),
                    ) { "Failed to send player action request for ${data.player.name}: $action" }
                }
            }
        }
    }

    private fun buildPlayerRequest(data: PlayerData, action: PlayerAction): Request? {
        return when (action) {
            PlayerAction.TogglePlayPause ->
                Request.Player.simpleCommand(playerId = data.playerId, command = "play_pause")

            PlayerAction.Play ->
                Request.Player.simpleCommand(playerId = data.playerId, command = "play")

            PlayerAction.Pause ->
                Request.Player.simpleCommand(playerId = data.playerId, command = "pause")

            PlayerAction.Next -> {
                val currentPos = data.queueInfo?.id
                    ?.let(positionTracker::effectiveSec)
                    ?: data.queueInfo?.elapsedTime ?: 0.0
                (data.queueInfo?.currentItem?.track as? Audiobook)
                    ?.chapters?.firstOrNull { it.start > currentPos }?.start
                    ?.let { Request.Player.seek(queueId = data.playerId, position = it.toLong()) }
                    ?: Request.Player.simpleCommand(playerId = data.playerId, command = "next")
            }

            PlayerAction.Previous -> {
                val currentPos = data.queueInfo?.id
                    ?.let(positionTracker::effectiveSec)
                    ?: data.queueInfo?.elapsedTime ?: 0.0
                (data.queueInfo?.currentItem?.track as? Audiobook)
                    ?.chapters?.takeIf { it.isNotEmpty() }
                    ?.let { chapters ->
                        val currentChapterStart =
                            chapters.lastOrNull { it.start <= currentPos }?.start ?: 0.0
                        val prevStart =
                            if (currentPos - currentChapterStart > 5) {
                                currentChapterStart
                            } else {
                                chapters.lastOrNull { it.start < currentChapterStart }?.start
                                    ?: 0.0
                            }
                        Request.Player.seek(queueId = data.playerId, position = prevStart.toLong())
                    }
                    ?: Request.Player.simpleCommand(playerId = data.playerId, command = "previous")
            }

            is PlayerAction.SeekTo -> {
                Logger.e("SeekTo: ${action.position}")
                Request.Player.seek(queueId = data.playerId, position = action.position)
            }

            is PlayerAction.ToggleRepeatMode -> {
                val queueId = data.queueInfo?.id ?: return null
                Request.Queue.setRepeatMode(
                    queueId = queueId,
                    repeatMode = when (action.current) {
                        RepeatMode.OFF -> RepeatMode.ALL
                        RepeatMode.ALL -> RepeatMode.ONE
                        RepeatMode.ONE -> RepeatMode.OFF
                    },
                )
            }

            is PlayerAction.ToggleShuffle -> {
                val queueId = data.queueInfo?.id ?: return null
                Request.Queue.setShuffle(queueId = queueId, enabled = !action.current)
            }

            is PlayerAction.ToggleDontStopTheMusic -> {
                val queueId = data.queueInfo?.id ?: return null
                Request.Queue.setDontStopTheMusic(queueId = queueId, enabled = !action.current)
            }

            PlayerAction.VolumeDown ->
                Request.Player.simpleCommand(playerId = data.playerId, command = "volume_down")

            PlayerAction.VolumeUp ->
                Request.Player.simpleCommand(playerId = data.playerId, command = "volume_up")

            is PlayerAction.VolumeSet ->
                Request.Player.setVolume(playerId = data.playerId, volumeLevel = action.level)

            is PlayerAction.ToggleMute ->
                Request.Player.setMute(playerId = data.playerId, !action.isMutedNow)

            PlayerAction.GroupVolumeDown ->
                Request.Player.simpleCommand(
                    playerId = data.playerId,
                    command = "group_volume_down",
                )

            PlayerAction.GroupVolumeUp ->
                Request.Player.simpleCommand(playerId = data.playerId, command = "group_volume_up")

            is PlayerAction.GroupVolumeSet ->
                Request.Player.setGroupVolume(playerId = data.playerId, volumeLevel = action.level)

            is PlayerAction.GroupToggleMute ->
                Request.Player.setGroupMute(playerId = data.playerId, !action.isMutedNow)

            is PlayerAction.GroupManage ->
                Request.Player.setGroupMembers(
                    playerId = data.playerId,
                    playersToAdd = action.toAdd,
                    playersToRemove = action.toRemove,
                )
        }
    }

    fun queueAction(action: QueueAction) {
        launch {
            when (action) {
                is QueueAction.PlayQueueItem -> {
                    apiClient.sendRequest(
                        Request.Queue.playIndex(
                            queueId = action.queueId,
                            queueItemId = action.queueItemId,
                        ),
                    )
                }

                is QueueAction.ClearQueue -> {
                    apiClient.sendRequest(
                        Request.Queue.clear(
                            queueId = action.queueId,
                        ),
                    )
                }

                is QueueAction.RemoveItems -> {
                    action.items.forEach {
                        apiClient.sendRequest(
                            Request.Queue.removeItem(
                                queueId = action.queueId,
                                queueItemId = it,
                            ),
                        )
                    }
                }

                is QueueAction.MoveItem -> {
                    (action.to - action.from)
                        .takeIf { it != 0 }
                        ?.let {
                            apiClient.sendRequest(
                                Request.Queue.moveItem(
                                    queueId = action.queueId,
                                    queueItemId = action.queueItemId,
                                    positionShift = action.to - action.from,
                                ),
                            )
                        }
                }

                is QueueAction.Transfer -> {
                    apiClient.sendRequest(
                        Request.Queue.transfer(
                            sourceId = action.sourceId,
                            targetId = action.targetId,
                            autoplay = action.autoplay,
                        ),
                    )
                }
            }
        }
    }

    fun onPlayersSortChanged(newSort: List<String>) = settings.updatePlayersSorting(newSort)

    private fun watchApiEvents() =
        launch {
            apiClient.events
                .collect { event ->
                    when (event) {
                        is PlayerAddedEvent -> {
                            val newPlayer = playerFactory.create(event.data)
                            Logger.e("Player added: $newPlayer")
                            if (newPlayer.shouldBeShown) {
                                _serverPlayers.update { oldState ->
                                    when (oldState) {
                                        is DataState.Data -> {
                                            val players = oldState.data
                                            DataState.Data(
                                                if (players.none { it.id == newPlayer.id }) {
                                                    players + newPlayer
                                                } else {
                                                    // Player already exists, just update it
                                                    players.map { if (it.id == newPlayer.id) newPlayer else it }
                                                },
                                            )
                                        }

                                        else -> oldState
                                    }
                                }
                            }
                        }

                        is PlayerRemovedEvent -> {
                            val playerId =
                                event.objectId ?: event.data.takeIf { it.isNotEmpty() }
                            if (playerId != null) {
                                Logger.e("Player removed: $playerId")
                                _serverPlayers.update { oldState ->
                                    when (oldState) {
                                        is DataState.Data -> {
                                            DataState.Data(
                                                oldState.data.filter { it.id != playerId },
                                            )
                                        }

                                        else -> oldState
                                    }
                                }
                            }
                        }

                        is PlayerUpdatedEvent -> {
                            val data = playerFactory.create(event.data)
                            Logger.i("Player updated: $data")
                            // Forward to local player repository if this is the local player
                            if (data.id == settings.sendspinClientId.value) {
                                localPlayerRepository.onServerPlayerUpdate(data)
                            }
                            _serverPlayers.update { oldState ->
                                when (oldState) {
                                    is DataState.Data -> {
                                        val players = oldState.data
                                        DataState.Data(
                                            if (data.shouldBeShown) {
                                                if (players.any { it.id == data.id }) {
                                                    players.map { if (it.id == data.id) data else it }
                                                } else {
                                                    players + data // Player just became visible
                                                }
                                            } else {
                                                players.filter { it.id != data.id }
                                            },
                                        )
                                    }

                                    else -> oldState
                                }
                            }
                        }

                        is QueueAddedEvent -> {
                            // Server announces a queue (typically when a new
                            // player connects and MA registers its queue).
                            val data = queueFactory.create(event.data).takeIfNotStale("QueueAdded") ?: return@collect
                            Logger.i("Queue added $data")

                            val localPlayerId = settings.sendspinClientId.value
                            if (data.id == localPlayerId ||
                                (_serverPlayers.value as? DataState.Data)?.data
                                    ?.find { it.id == localPlayerId }?.queueId == data.id
                            ) {
                                localPlayerRepository.onServerQueueUpdate(data)
                            }

                            // Upsert: replace if present, append if new.
                            _queueInfos.update { value ->
                                if (value.any { it.id == data.id }) {
                                    value.map { if (it.id == data.id) data else it }
                                } else {
                                    value + data
                                }
                            }
                            data.elapsedTime?.let { elapsed ->
                                val player = (_serverPlayers.value as? DataState.Data)
                                    ?.data?.find { it.queueId == data.id }
                                positionTracker.setAnchor(
                                    queueId = data.id,
                                    elapsedSec = elapsed,
                                    isPlaying = player?.isPlaying,
                                    durationSec = data.currentItem?.track?.duration,
                                )
                            }
                        }

                        is QueueUpdatedEvent -> {
                            val data =
                                queueFactory.create(event.data).takeIfNotStale("QueueUpdated") ?: return@collect
                            Logger.i("Queue updated $data")

                            // Forward to local player repository if this is the local player's queue
                            val localPlayerId = settings.sendspinClientId.value
                            if (data.id == localPlayerId ||
                                (_serverPlayers.value as? DataState.Data)?.data
                                    ?.find { it.id == localPlayerId }?.queueId == data.id
                            ) {
                                localPlayerRepository.onServerQueueUpdate(data)
                            }

                            _queueInfos.update { value ->
                                value.map {
                                    if (it.id == data.id) data else it
                                }
                            }
                            data.elapsedTime?.let { elapsed ->
                                val player = (_serverPlayers.value as? DataState.Data)
                                    ?.data?.find { it.queueId == data.id }
                                positionTracker.setAnchor(
                                    queueId = data.id,
                                    elapsedSec = elapsed,
                                    isPlaying = player?.isPlaying,
                                    durationSec = data.currentItem?.track?.duration,
                                )
                            }
                        }

                        is QueueItemsUpdatedEvent -> {
                            val data =
                                queueFactory.create(event.data).takeIfNotStale("QueueItemsUpdated") ?: return@collect
                            _queueInfos.update { value ->
                                value.map {
                                    if (it.id == data.id) data else it
                                }
                            }
                            data.elapsedTime?.let { elapsed ->
                                val player = (_serverPlayers.value as? DataState.Data)
                                    ?.data?.find { it.queueId == data.id }
                                positionTracker.setAnchor(
                                    queueId = data.id,
                                    elapsedSec = elapsed,
                                    isPlaying = player?.isPlaying,
                                    durationSec = data.currentItem?.track?.duration,
                                )
                            }
                            (playersData.value as? DataState.Data)?.data?.firstOrNull {
                                it.queueId == data.id
                            }?.let { refreshPlayerQueueItems(it, data) }
                        }

                        is QueueTimeUpdatedEvent -> {
                            // Not staleness-gated: payload has no server-side
                            // `last_updated` to compare against. Relies on
                            // in-order WebSocket delivery instead.
                            _queueInfos.update { value ->
                                value.map {
                                    if (it.id == event.objectId) it.copy(elapsedTime = event.data) else it
                                }
                            }
                            event.objectId?.let { queueId ->
                                positionTracker.setAnchor(
                                    queueId = queueId,
                                    elapsedSec = event.data,
                                )
                            }
                        }

                        is MediaItemPlayedEvent -> {
                            // Position-tracking removed; the slider interpolates locally
                            // from `(queueInfo.elapsedTime, isPlaying, duration)`. Server
                            // anchors flow via `QueueTimeUpdatedEvent` above.
                        }

                        is MediaItemUpdatedEvent -> {
                            (mediaItemFactory.create(event.data) as? Track)
                                ?.let { updateMediaTrackInfo(it) }
                        }

                        is MediaItemAddedEvent -> {
                            (mediaItemFactory.create(event.data) as? Track)
                                ?.let { updateMediaTrackInfo(it) }
                        }

                        is MediaItemDeletedEvent -> {
                            (mediaItemFactory.create(event.data) as? Track)
                                ?.let { deletedTrack ->
                                    _playersData.update { currentState ->
                                        when (currentState) {
                                            is DataState.Error,
                                            is DataState.Loading,
                                            is DataState.NoData,
                                            is DataState.Stale,
                                                -> currentState

                                            is DataState.Data -> DataState.Data(
                                                currentState.data.map { playerData ->
                                                    playerData.queueItems?.let { items ->
                                                        val updatedItems = items.filter {
                                                            (it.track as? AppMediaItem)
                                                                ?.hasAnyMappingFrom(deletedTrack) != true
                                                        }
                                                        playerData.copy(
                                                            queue = (playerData.queue as? DataState.Data)?.let { queueData ->
                                                                DataState.Data(
                                                                    queueData.data.copy(
                                                                        items = DataState.Data(
                                                                            updatedItems,
                                                                        ),
                                                                    ),
                                                                )
                                                            } ?: playerData.queue,
                                                        )
                                                    } ?: playerData
                                                },
                                            )
                                        }
                                    }
                                }
                        }

                        else -> log.i { "Unhandled event: $event" }
                    }
                }
        }

    private fun updateMediaTrackInfo(newTrack: Track) {
        _playersData.update { currentState ->
            when (currentState) {
                is DataState.Error,
                is DataState.Loading,
                is DataState.NoData,
                is DataState.Stale,
                    -> currentState

                is DataState.Data -> DataState.Data(
                    currentState.data.map { playerData ->
                        playerData.queueItems?.let { items ->
                            val updatedItems = items.map { queueTrack ->
                                if ((queueTrack.track as? AppMediaItem)?.hasAnyMappingFrom(newTrack) == true) {
                                    queueTrack.copy(
                                        track = newTrack,
                                    )
                                } else {
                                    queueTrack
                                }
                            }
                            playerData.copy(
                                queue = (playerData.queue as? DataState.Data)?.let { queueData ->
                                    DataState.Data(
                                        queueData.data.copy(
                                            info = if ((queueData.data.info.currentItem?.track as? AppMediaItem)
                                                    ?.hasAnyMappingFrom(newTrack) == true
                                            ) {
                                                queueData.data.info.copy(
                                                    currentItem = queueData.data.info.currentItem.copy(
                                                        track = newTrack
                                                            .takeIf {
                                                                it.hasAnyMappingFrom(
                                                                    queueData.data.info.currentItem.track as AppMediaItem,
                                                                )
                                                            }
                                                            ?: queueData.data.info.currentItem.track,
                                                    ),
                                                )
                                            } else {
                                                queueData.data.info
                                            },
                                            items = DataState.Data(updatedItems),
                                        ),
                                    )
                                } ?: playerData.queue,
                            )
                        } ?: playerData
                    },
                )
            }
        }
    }

    private fun updatePlayersAndQueues() {
        log.i { "Updating players and queues" }
        launch {
            apiClient.sendRequest(Request.Player.all())
                .resultAs<List<ServerPlayer>>()?.let { playerFactory.createList(it) }
                ?.let { list ->
                    val visiblePlayers = list.filter { it.shouldBeShown }
                    _serverPlayers.update {
                        DataState.Data(visiblePlayers)
                    }
                    // Forward to repository: real player if found, synthetic if not
                    val localPlayerId = settings.sendspinClientId.value
                    val localServerPlayer = visiblePlayers.find { it.id == localPlayerId }
                    localPlayerRepository.onInitialPlayersReceived(
                        hasLocalPlayer = localServerPlayer != null,
                    )
                    localServerPlayer?.let {
                        localPlayerRepository.onServerPlayerUpdate(it)
                    }
                }
        }
        launch {
            apiClient.sendRequest(Request.Queue.all())
                .resultAs<List<ServerQueue>>()?.let { queueFactory.createList(it) }?.let { list ->
                    _queueInfos.update { list }
                    list.forEach { queueInfo ->
                        queueInfo.elapsedTime?.let { elapsed ->
                            val player = (_serverPlayers.value as? DataState.Data)
                                ?.data?.find { it.queueId == queueInfo.id }
                            positionTracker.setAnchor(
                                queueId = queueInfo.id,
                                elapsedSec = elapsed,
                                isPlaying = player?.isPlaying,
                                durationSec = queueInfo.currentItem?.track?.duration,
                            )
                        }
                    }

                    // Forward local player's queue to repository
                    val localPlayerId = settings.sendspinClientId.value
                    val localQueueId = (_serverPlayers.value as? DataState.Data)?.data
                        ?.find { it.id == localPlayerId }?.queueId
                    list.find { it.id == localPlayerId || it.id == localQueueId }
                        ?.let { localPlayerRepository.onServerQueueUpdate(it) }
                }
        }
        launch {
            // Wait for the players+queues combine to land, then fetch items
            // for every player. Single-shot per call — runtime queue changes
            // are covered by QueueItemsUpdatedEvent.
            _playersData.first { it is DataState.Data }
            refreshAllPlayersQueueItems()
        }
    }

    private fun updateProvidersManifests() {
        launch {
            apiClient.sendRequest(Request.Library.providersManifests())
                .resultAs<List<ProviderManifest>>()?.filter { it.type == "music" }
                ?.let { manifests ->
                    val map = buildMap {
                        put(
                            "library",
                            ProviderIconModel.Mdi(BookshelfIcon, Color.White),
                        )
                        manifests.forEach { manifest ->
                            ProviderIconModel.from(manifest.icon, manifest.iconSvgDark)?.let {
                                put(manifest.domain, it)
                            }
                        }
                    }
                    _providersIcons.update { map }
                }
        }
    }

    /**
     * Fan out item fetches across every player whose queue metadata is loaded.
     * Without this, items only get fetched for the currently selected player
     * (via the [selectedPlayerIndex] collector), so notifications and pager
     * neighbours show "not loaded" until visited.
     */
    private fun refreshAllPlayersQueueItems() {
        val players = when (val pd = _playersData.value) {
            is DataState.Data -> pd.data
            is DataState.Stale -> pd.data
            else -> return
        }
        players.forEach { pd ->
            if (pd.queueInfo != null) refreshPlayerQueueItems(pd)
        }
    }

    /**
     * Refresh queue items for the currently selected player.
     * Used after reconnection when selectedPlayerIndex doesn't re-emit
     * (same index value before and after reconnect).
     */
    private fun refreshSelectedPlayerQueueItems() {
        val players = when (val pd = _playersData.value) {
            is DataState.Data -> pd.data
            is DataState.Stale -> pd.data
            else -> return
        }
        _selectedPlayerId.value?.let { selectedId ->
            players.firstOrNull { it.playerId == selectedId }
                ?.let { refreshPlayerQueueItems(it) }
        }
    }

    private fun refreshPlayerQueueItems(
        fullData: PlayerData,
        forcedQueueData: QueueInfo? = null,
    ) {
        launch {
            (forcedQueueData ?: fullData.queueInfo)?.let { queueInfo ->
                val queueTracks = apiClient.sendRequest(Request.Queue.items(queueInfo.id))
                    .resultAs<List<ServerQueueItem>>()?.let { queueFactory.createTrackList(it) }

                // Forward to local player repository so external consumers (Android Auto, CarPlay) see items
                if (fullData.isLocal && queueTracks != null) {
                    localPlayerRepository.onQueueItemsLoaded(queueInfo, queueTracks)
                }

                _playersData.update { currentState ->
                    when (currentState) {
                        is DataState.Error,
                        is DataState.Loading,
                        is DataState.NoData,
                        is DataState.Stale,
                            -> currentState

                        is DataState.Data -> DataState.Data(
                            currentState.data.map { playerData ->
                                if (playerData.player.id == fullData.player.id) {
                                    PlayerData(
                                        player = playerData.player,
                                        queue = DataState.Data(
                                            Queue(
                                                info = queueInfo,
                                                items = queueTracks?.let { list ->
                                                    DataState.Data(
                                                        list,
                                                    )
                                                }
                                                    ?: DataState.Error(),
                                            ),
                                        ),
                                        parentBind = playerData.parentBind,
                                        childrenBinds = playerData.childrenBinds,
                                        isLocal = playerData.player.id == settings.sendspinClientId.value,
                                    )
                                } else {
                                    playerData
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    /**
     * Called when the app task is removed (user closed the app from recents).
     * Stops Sendspin if nothing is actively playing — playing state is intentionally
     * kept alive for background audio and is not affected by this call.
     */
    fun onAppClosed() {
        if (!isAnythingPlaying.value) {
            log.i { "App closed with no active playback — stopping Sendspin" }
            launch { stopSendspin() }
        }
    }

    fun close() {
        supervisorJob.cancel()
    }

    internal companion object {
        private const val MAX_SENDSPIN_RETRIES = 5

        /**
         * Resolves the effective selected player from the current state.
         * Pure function; re-evaluates on every input change.
         *
         * Invariant: the result is always either `null` or a member of
         * [visiblePlayerIds]. Returning a value not in the list would let a
         * downstream consumer attempt to act on an unreachable player; the
         * persisted choice is held in `SettingsRepository.lastSelectedPlayerId`
         * across loading gaps, not here.
         *
         * Resolution order:
         *  1. [userChoice] if it appears in [visiblePlayerIds] — persisted
         *     explicit pick.
         *  2. First visible player — fallback when no user choice or when
         *     the user's choice is offline.
         *  3. `null` when [visiblePlayerIds] is empty.
         */
        internal fun resolveSelectedPlayerId(
            visiblePlayerIds: List<String>,
            userChoice: String?,
        ): String? {
            if (userChoice != null && userChoice in visiblePlayerIds) return userChoice
            return visiblePlayerIds.firstOrNull()
        }
    }
}
