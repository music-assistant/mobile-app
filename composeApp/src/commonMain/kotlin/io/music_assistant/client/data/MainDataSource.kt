package io.music_assistant.client.data

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.ui.graphics.Color
import co.touchlab.kermit.Logger
import io.music_assistant.client.api.Request
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.data.model.client.AppMediaItem
import io.music_assistant.client.data.model.client.AppMediaItem.Companion.toAppMediaItem
import io.music_assistant.client.data.model.client.Player
import io.music_assistant.client.data.model.client.Player.Companion.toPlayer
import io.music_assistant.client.data.model.client.PlayerData
import io.music_assistant.client.data.model.client.Queue
import io.music_assistant.client.data.model.client.QueueInfo
import io.music_assistant.client.data.model.client.QueueInfo.Companion.toQueue
import io.music_assistant.client.data.model.client.QueueTrack.Companion.toQueueTrack
import io.music_assistant.client.data.model.server.ProviderManifest
import io.music_assistant.client.data.model.server.RepeatMode
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
import io.music_assistant.client.ui.compose.common.DataState
import io.music_assistant.client.ui.compose.common.StaleReason
import io.music_assistant.client.ui.compose.common.action.PlayerAction
import io.music_assistant.client.ui.compose.common.action.QueueAction
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
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
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

    // Position tracking for smooth local playback position calculation
    private data class PositionTracker(
        val queueId: String,
        val basePosition: Double,  // Last known server position in seconds
        val baseTimestamp: Long,   // System time when basePosition was captured
        val isPlaying: Boolean,
        val duration: Double?      // Track duration for clamping
    ) {
        fun calculateCurrentPosition(): Double {
            if (!isPlaying) return basePosition
            val elapsedSinceBase = (currentTimeMillis() - baseTimestamp) / 1000.0
            val calculated = basePosition + elapsedSinceBase
            return duration?.let { calculated.coerceAtMost(it) } ?: calculated
        }
    }

    private val _positionTrackers = MutableStateFlow<Map<String, PositionTracker>>(emptyMap())

    private val _players =
        combine(_serverPlayers, settings.playersSorting) { playersState, sortedIds ->
            when (playersState) {
                is DataState.Error,
                is DataState.Loading,
                is DataState.NoData -> playersState

                is DataState.Data -> {
                    val players = playersState.data
                    DataState.Data(
                        sortedIds?.let {
                            players.sortedBy { player ->
                                sortedIds.indexOf(player.id).takeIf { it >= 0 }
                                    ?: Int.MAX_VALUE
                            }
                        } ?: players.sortedBy { player -> player.name }
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
                        reason = playersState.reason
                    )
                }
            }

        }.stateIn(
            scope = this,
            started = SharingStarted.Eagerly,
            initialValue = DataState.Loading()
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

    private val _selectedPlayerId = MutableStateFlow<String?>(null)
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
        // Position calculation loop - runs independently to provide smooth position updates
        launch {
            while (isActive) {
                // Only update positions if we have live or recovering data
                val shouldUpdatePositions = when (val playersState = _serverPlayers.value) {
                    is DataState.Data -> true
                    is DataState.Stale -> playersState.reason == StaleReason.RECONNECTING
                    else -> false
                }

                if (shouldUpdatePositions) {
                    // Update QueueInfo with latest calculated positions
                    _queueInfos.update { queues ->
                        queues.map { queue ->
                            val tracker = _positionTrackers.value[queue.id]
                            if (tracker != null) {
                                val calculatedPos = tracker.calculateCurrentPosition()
                                queue.copy(elapsedTime = calculatedPos)
                            } else {
                                queue
                            }
                        }
                    }
                }
                delay(500L) // Update position twice per second for smooth progress
            }
        }

        launch {
            combine(
                _players,
                _queueInfos,
                localPlayerRepository.localPlayerData
            ) { players, queues, localData -> Triple(players, queues, localData) }
                .debounce(50L) // Small debounce to batch rapid updates, but don't delay initial load
                .collect { (playersState, queues, localData) ->
                    _playersData.update { oldValues ->
                        when (playersState) {
                            is DataState.Error -> DataState.Error()
                            is DataState.Loading -> DataState.Loading()
                            is DataState.NoData -> DataState.NoData()
                            is DataState.Data -> DataState.Data(
                                buildPlayerDataList(
                                    playersState.data, queues, localData, oldValues
                                )
                            )

                            is DataState.Stale -> DataState.Stale(
                                data = buildPlayerDataList(
                                    playersState.data, queues, localData, oldValues
                                ),
                                disconnectedAt = playersState.disconnectedAt,
                                reason = playersState.reason
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
                                            // Brief disconnection - data is still fresh!
                                            // Transition stale data back to Data without fetching
                                            // This prevents the "blink" from reloading the UI
                                            log.i { "Seamless recovery - reusing cached data" }
                                            _serverPlayers.update {
                                                DataState.Data(currentState.data)
                                            }

                                            // CRITICAL: Re-authenticate the server session
                                            // New WebSocket connection needs auth command sent
                                            launch {
                                                // Get token for current server
                                                val serverIdentifier = when (val state =
                                                    apiClient.sessionState.value) {
                                                    is SessionState.Connected.Direct -> {
                                                        state.connectionInfo.let { connInfo ->
                                                            settings.getDirectServerIdentifier(
                                                                connInfo.host,
                                                                connInfo.port,
                                                                connInfo.isTls
                                                            )
                                                        }
                                                    }

                                                    is SessionState.Connected.WebRTC -> {
                                                        settings.getWebRTCServerIdentifier(state.remoteId.rawId)
                                                    }

                                                    else -> null
                                                }

                                                val token = serverIdentifier?.let {
                                                    settings.getTokenForServer(it)
                                                }

                                                if (token != null) {
                                                    log.i { "Re-authenticating after reconnection with saved token for server: $serverIdentifier" }
                                                    apiClient.authorize(token, isAutoLogin = true)
                                                } else {
                                                    log.w { "No saved token to re-authenticate with for server: $serverIdentifier" }
                                                }
                                            }

                                            // Reinit Sendspin — safe because initSendspinIfEnabled()
                                            // returns early if already Connected/Reconnecting.
                                            // Needed because:
                                            //  - WebRTC: new data channels were created on reconnect;
                                            //    old SendspinClient holds a dead channel (Idle state).
                                            //  - WebSocket: reconnection may have given up;
                                            //    server removes the player when the socket closes.
                                            sendspinClientFactory.onFreshWebRTCConnection()
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
                                    // Safety net: reinit Sendspin if it's not already connected
                                    sendspinClientFactory.onFreshWebRTCConnection()
                                    launch { initSendspinIfEnabled() }
                                }

                                is DataState.Loading, is DataState.NoData, is DataState.Error -> {
                                    // Fresh connection or error recovery - show loading
                                    _serverPlayers.update { DataState.Loading() }
                                    updateProvidersManifests()
                                    sendspinClientFactory.onFreshWebRTCConnection()
                                    initSendspinIfEnabled()
                                    updatePlayersAndQueues()
                                }
                            }
                        } else {
                            // Not authenticated yet
                            val connState = sessionState.dataConnectionState
                            val isTerminalAuthFailure =
                                connState is DataConnectionState.AwaitingAuth &&
                                        (connState.authProcessState is AuthProcessState.LoggedOut ||
                                                connState.authProcessState is AuthProcessState.Failed)

                            if (isTerminalAuthFailure) {
                                // Auth permanently failed — stop everything
                                log.w { "[SS-DIAG] Terminal auth failure (${connState}) — stopping sendspin" }
                                stopSendspin()
                                clearAllData()
                            } else {
                                // Transient: AwaitingServerInfo or auth in progress.
                                // Keep sendspin alive — it will be reinitialized when auth completes.
                                // Preserve stale data so reconnection recovery works.
                                log.w { "[SS-DIAG] Transient non-auth state ($connState) — keeping sendspin alive" }
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
                                        reason = StaleReason.RECONNECTING
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
                                            reason = StaleReason.RECONNECTING
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
                        // Fresh connection attempt - show loading
                        log.i { "Connecting - stopping Sendspin and showing loading state" }
                        stopSendspin()
                        updateJob?.cancel()
                        updateJob = null
                        watchJob?.cancel()
                        watchJob = null
                        _serverPlayers.update { DataState.Loading() }
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

                                        log.w { "Persistent connection error - preserving ${(data as? List<*>)?.size ?: 0} players as stale" }
                                        _serverPlayers.update {
                                            DataState.Stale(
                                                data = data,
                                                disconnectedAt = originalDisconnectedAt,  // Preserve original
                                                reason = StaleReason.PERSISTENT_ERROR
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
                                                reason = StaleReason.RECONNECTING
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
            var wasLocalPlayerInList = false
            playersData.mapNotNull { (it as? DataState.Data)?.data }.collect { playersList ->
                // Auto-select first player if no player is selected
                if (playersList.isNotEmpty()
                    && playersList.none { data -> data.playerId == _selectedPlayerId.value }
                ) {
                    _selectedPlayerId.update { playersList.getOrNull(0)?.playerId }
                }
                // When local player first appears at first position, select it
                val localId = settings.sendspinClientId.value
                if (playersList.firstOrNull()?.playerId == localId && !wasLocalPlayerInList) {
                    _selectedPlayerId.update { localId }
                }
                wasLocalPlayerInList = playersList.any { it.playerId == localId }
                // Don't call updatePlayersAndQueues() here - it creates a reactive loop!
                // Updates are triggered by sessionState changes and API events.
            }
        }
        launch {
            selectedPlayerIndex.filterNotNull().collect { index ->
                // Only refresh queue if we have live data and are authenticated
                // Don't try to load during Stale state - will error with auth issues
                val sessionState = apiClient.sessionState.value
                val isAuthenticated =
                    (sessionState as? SessionState.Connected)?.dataConnectionState == DataConnectionState.Authenticated

                if (isAuthenticated) {
                    (playersData.value as? DataState.Data)?.data?.let { list ->
                        refreshPlayerQueueItems(list[index])
                    }
                } else {
                    log.d { "Skipping queue refresh - not authenticated (state: ${sessionState::class.simpleName})" }
                }
            }
        }

        // Watch for Sendspin settings changes
        launch {
            settings.sendspinEnabled.collect { enabled ->
                if (apiClient.sessionState.value is SessionState.Connected) {
                    if (enabled) {
                        initSendspinIfEnabled()
                    } else {
                        stopSendspin()
                    }
                }
            }
        }
        // Keep Now Playing (iOS Control Center / Lock Screen) in sync with local player state.
        // Runs every ~500 ms driven by the position calculation loop above.
        launch {
            localPlayer.collect { playerData ->
                val track = playerData?.queueInfo?.currentItem?.track
                val serverUrl =
                    (apiClient.sessionState.value as? SessionState.Connected)?.serverInfo?.baseUrl
                if (track != null) {
                    mediaPlayerController.updateNowPlaying(
                        title = track.name,
                        artist = track.subtitle,
                        album = track.parentName,
                        artworkUrl = track.imageInfo?.url(serverUrl),
                        duration = track.duration ?: 0.0,
                        elapsedTime = playerData.queueInfo.elapsedTime ?: 0.0,
                        playbackRate = if (playerData.player.isPlaying) 1.0 else 0.0
                    )
                } else {
                    mediaPlayerController.clearNowPlaying()
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
        oldValues: DataState<List<PlayerData>>
    ): List<PlayerData> {
        val localPlayerId = settings.sendspinClientId.value
        val groupedPlayersToHide = allPlayers
            .map { (it.groupChildren ?: emptyList()) - it.id }
            .flatten()
            .filter { it != localPlayerId }
            .toSet()
        val filteredPlayers = allPlayers.filter { it.id !in groupedPlayersToHide }

        val playerDataList = filteredPlayers.map { player ->
            if (player.id == localPlayerId && localData != null) {
                // Repository is source of truth. Overlay interpolated position from tracker
                // (repository has raw server position; queues has smooth 500ms interpolation).
                val trackedElapsed = queues.find {
                    it.id == player.queueId || it.id == localPlayerId
                }?.elapsedTime
                val withPosition = trackedElapsed?.let {
                    (localData.queue as? DataState.Data)?.let { qd ->
                        localData.copy(
                            queue = DataState.Data(
                                qd.data.copy(info = qd.data.info.copy(elapsedTime = it))
                            )
                        )
                    }
                } ?: localData
                val enriched = withPosition.copy(
                    groupChildren = allPlayers.mapNotNull { it.asBindFor(player) }
                )
                // Preserve loaded queue items from previous state
                (oldValues as? DataState.Data)?.data
                    ?.firstOrNull { it.player.id == player.id }
                    ?.updateFrom(enriched) ?: enriched
            } else {
                val newData = PlayerData(
                    player = player,
                    queue = queues.find { it.id == player.queueId }
                        ?.let { queueInfo ->
                            DataState.Data(
                                Queue(info = queueInfo, items = DataState.NoData())
                            )
                        } ?: DataState.NoData(),
                    groupChildren = allPlayers.mapNotNull { it.asBindFor(player) },
                    isLocal = player.id == localPlayerId
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
        _positionTrackers.update { emptyMap() }
        localPlayerRepository.clearState()
        // Note: _providersIcons deliberately NOT cleared (static data)
    }

    /**
     * Initialize Sendspin player if enabled in settings.
     * Safe for background: MainDataSource is singleton held by foreground service.
     */
    private suspend fun initSendspinIfEnabled() = sendspinMutex.withLock {
        log.w { "[SS-DIAG] initSendspin: acquired mutex" }

        // Get prerequisites
        val mainConnectionInfo = settings.connectionInfo.value ?: run {
            log.w { "No main connection info available, cannot initialize Sendspin" }
            return@withLock
        }

        val authToken = when (val state = apiClient.sessionState.value) {
            is SessionState.Connected.Direct ->
                settings.getTokenForServer(
                    settings.getDirectServerIdentifier(
                        state.connectionInfo.host,
                        state.connectionInfo.port,
                        state.connectionInfo.isTls
                    )
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
                is SendspinState.Handshaking -> {
                    log.w { "[SS-DIAG] initSendspin: SKIP — ${state::class.simpleName} (in progress)" }
                    return@withLock
                }

                is SendspinState.Reconnecting -> {
                    log.w { "[SS-DIAG] initSendspin: SKIP — Reconnecting (wasStreaming=${state.wasStreaming}, attempt=${state.attempt})" }
                    return@withLock
                }

                is SendspinState.Error -> {
                    val errorMsg = when (val err = state.error) {
                        is SendspinError.Permanent -> err.userAction
                        is SendspinError.Transient -> err.cause.message
                        is SendspinError.Degraded -> err.reason
                    }
                    log.w { "[SS-DIAG] initSendspin: REINIT — Error: $errorMsg" }
                }

                is SendspinState.Idle -> {
                    log.w { "[SS-DIAG] initSendspin: REINIT — was Idle" }
                }
            }
            log.w { "[SS-DIAG] initSendspin: stopping old client before reinit" }
            existing.stop()
            existing.close()
        } ?: log.w { "[SS-DIAG] initSendspin: no existing client — fresh init" }

        // Create client using factory
        val createResult = sendspinClientFactory.createIfEnabled(
            mainConnection = mainConnectionInfo,
            authToken = authToken
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
        // Commands go directly through MainDataSource via REST API
        mediaPlayerController.onRemoteCommand = { command ->
            localPlayer.value?.let { playerData ->
                log.i { "Remote command from Control Center: $command" }
                when (command) {
                    "play" -> playerAction(playerData, PlayerAction.Play)
                    "pause" -> playerAction(playerData, PlayerAction.Pause)
                    "toggle_play_pause" -> playerAction(
                        playerData,
                        PlayerAction.TogglePlayPause
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
            client.playbackStoppedDueToError.filterNotNull().collect { error ->
                log.w(error) { "[SS-DIAG] playbackStoppedDueToError fired: ${error.message}" }
                // Pause the local sendspin player on the MA server
                localPlayer.value?.let { playerData ->
                    log.w { "[SS-DIAG] localPlayer isPlaying=${playerData.player.isPlaying}, name=${playerData.player.name}" }
                    if (playerData.player.isPlaying) {
                        log.w { "[SS-DIAG] >>> SENDING PAUSE to server for ${playerData.player.name}" }
                        playerAction(playerData, PlayerAction.Pause)
                    }
                } ?: log.w { "[SS-DIAG] playbackStoppedDueToError but localPlayer is null" }
            }
        }

        sendspinMonitorJobs += launch {
            client.state.collect { state ->
                log.w { "[SS-DIAG] state transition -> $state (prev=${_sendspinState.value})" }
                _sendspinState.value = state
                when (state) {
                    is SendspinState.Ready -> {
                        sendspinRetryCount = 0
                        log.w { "[SS-DIAG] Ready — refreshing player list in 1s" }
                        delay(1000) // Give server a moment to register the player
                        updatePlayersAndQueues()
                    }

                    is SendspinState.Error -> {
                        log.w { "[SS-DIAG] Error state: ${state.error} — signalling pipeline disconnect" }
                        sendspinClientFactory.getOrCreatePipeline().first.onNetworkDisconnected()

                        // Retry if error is not being auto-retried and main API is connected
                        val shouldRetry = when (state.error) {
                            is SendspinError.Permanent -> true
                            is SendspinError.Transient -> !state.error.willRetry
                            is SendspinError.Degraded -> false
                        }

                        if (shouldRetry && sendspinRetryCount < MAX_SENDSPIN_RETRIES) {
                            val isAuthenticated =
                                (apiClient.sessionState.value as? SessionState.Connected)
                                    ?.dataConnectionState == DataConnectionState.Authenticated
                            if (isAuthenticated && settings.sendspinEnabled.value) {
                                sendspinRetryCount++
                                val backoffMs = 5000L * sendspinRetryCount
                                log.w { "[SS-DIAG] retry $sendspinRetryCount/$MAX_SENDSPIN_RETRIES in ${backoffMs}ms" }
                                delay(backoffMs)
                                // Re-check after delay (conditions may have changed)
                                val stillValid =
                                    (apiClient.sessionState.value as? SessionState.Connected)
                                        ?.dataConnectionState == DataConnectionState.Authenticated
                                            && settings.sendspinEnabled.value
                                if (stillValid) {
                                    try {
                                        initSendspinIfEnabled()
                                    } catch (e: Exception) {
                                        coroutineContext.ensureActive()
                                        log.e(e) { "[SS-DIAG] retry $sendspinRetryCount failed" }
                                    }
                                }
                            }
                        }
                    }

                    is SendspinState.Idle -> {
                        log.w { "[SS-DIAG] Idle state — signalling pipeline disconnect" }
                        sendspinClientFactory.getOrCreatePipeline().first.onNetworkDisconnected()
                    }

                    is SendspinState.Reconnecting -> {
                        log.w { "[SS-DIAG] Reconnecting: wasStreaming=${state.wasStreaming}, attempt=${state.attempt}" }
                    }

                    else -> {
                        log.w { "[SS-DIAG] state=$state (no special handling)" }
                    }
                }
            }
        }
    }
    private fun cancelSendspinMonitorJobs() {
        if (sendspinMonitorJobs.isNotEmpty()) {
            log.w { "[SS-DIAG] cancelling ${sendspinMonitorJobs.size} old monitor jobs" }
            sendspinMonitorJobs.forEach { it.cancel() }
            sendspinMonitorJobs.clear()
        }
    }

    /**
     * Stop Sendspin player if running.
     * Destroys the shared audio pipeline so the AudioTrack is fully released.
     */
    private suspend fun stopSendspin() = sendspinMutex.withLock {
        val currentState = sendspinClient?.state?.value
        log.w { "[SS-DIAG] stopSendspin() acquired mutex — client=${sendspinClient != null}, clientState=$currentState" }
        // Cancel monitor jobs FIRST to prevent old state transitions from leaking
        cancelSendspinMonitorJobs()
        sendspinRetryCount = 0
        sendspinClient?.let { client ->
            log.w { "[SS-DIAG] stopping client (state=${client.state.value})" }
            try {
                client.stop()
                client.close()
            } catch (e: Exception) {
                log.e(e) { "Error stopping Sendspin client" }
            }
            sendspinClient = null
        }
        _sendspinState.value = null
        // Fully release the shared audio pipeline (AudioTrack, decoder, etc.)
        // A fresh pipeline will be created on the next initSendspinIfEnabled()
        sendspinClientFactory.destroyPipeline()
    }


    fun selectPlayer(player: Player) {
        _selectedPlayerId.update { player.id }
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
                            command = "play_pause"
                        )
                    )
                }

                PlayerAction.Play -> {
                    apiClient.sendRequest(
                        Request.Player.simpleCommand(
                            playerId = playerId,
                            command = "play"
                        )
                    )
                }

                PlayerAction.Pause -> {
                    apiClient.sendRequest(
                        Request.Player.simpleCommand(
                            playerId = playerId,
                            command = "pause"
                        )
                    )
                }

                PlayerAction.Next -> {
                    apiClient.sendRequest(
                        Request.Player.simpleCommand(playerId = playerId, command = "next")
                    )
                }

                PlayerAction.Previous -> {
                    apiClient.sendRequest(
                        Request.Player.simpleCommand(
                            playerId = playerId,
                            command = "previous"
                        )
                    )
                }

                is PlayerAction.SeekTo -> {
                    apiClient.sendRequest(
                        Request.Player.seek(
                            queueId = playerId,
                            position = action.position
                        )
                    )
                }

                PlayerAction.VolumeDown -> apiClient.sendRequest(
                    Request.Player.simpleCommand(
                        playerId = playerId,
                        command = "volume_down"
                    )
                )

                PlayerAction.VolumeUp -> apiClient.sendRequest(
                    Request.Player.simpleCommand(
                        playerId = playerId,
                        command = "volume_up"
                    )
                )

                is PlayerAction.ToggleMute -> apiClient.sendRequest(
                    Request.Player.setMute(playerId = playerId, !action.isMutedNow)
                )


                is PlayerAction.VolumeSet -> apiClient.sendRequest(
                    Request.Player.setVolume(
                        playerId = playerId,
                        volumeLevel = action.level
                    )
                )

                PlayerAction.GroupVolumeDown -> apiClient.sendRequest(
                    Request.Player.simpleCommand(
                        playerId = playerId,
                        command = "group_volume_down"
                    )
                )

                PlayerAction.GroupVolumeUp -> apiClient.sendRequest(
                    Request.Player.simpleCommand(
                        playerId = playerId,
                        command = "group_volume_up"
                    )
                )

                is PlayerAction.GroupVolumeSet -> apiClient.sendRequest(
                    Request.Player.setGroupVolume(
                        playerId = playerId,
                        volumeLevel = action.level
                    )
                )

                is PlayerAction.GroupManage -> apiClient.sendRequest(
                    Request.Player.setGroupMembers(
                        playerId = playerId,
                        playersToAdd = action.toAdd,
                        playersToRemove = action.toRemove
                    )
                )


                else -> Unit
            }
        }
    }

    fun playerAction(data: PlayerData, action: PlayerAction) {
        // Apply optimistic update for local player (immediate, before async command)
        if (data.isLocal) {
            localPlayerRepository.applyOptimisticUpdate(data, action)
            // Optimistic seek: update position tracker immediately
            if (action is PlayerAction.SeekTo) {
                data.queueInfo?.id?.let { queueId ->
                    _positionTrackers.update { trackers ->
                        trackers + (queueId to PositionTracker(
                            queueId = queueId,
                            basePosition = action.position.toDouble(),
                            baseTimestamp = currentTimeMillis(),
                            isPlaying = data.player.isPlaying,
                            duration = data.queueInfo.currentItem?.track?.duration
                        ))
                    }
                }
            }
        }
        launch {
            val request = buildPlayerRequest(data, action) ?: return@launch
            if (data.isLocal) {
                localPlayerRepository.sendOrQueue(action, request)
            } else {
                apiClient.sendRequest(request)
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
                val currentPos = data.queueInfo?.elapsedTime ?: 0.0
                (data.queueInfo?.currentItem?.track as? AppMediaItem.Audiobook)
                    ?.chapters?.firstOrNull { it.start > currentPos }?.start
                    ?.let { Request.Player.seek(queueId = data.playerId, position = it.toLong()) }
                    ?: Request.Player.simpleCommand(playerId = data.playerId, command = "next")
            }

            PlayerAction.Previous -> {
                val currentPos = data.queueInfo?.elapsedTime ?: 0.0
                (data.queueInfo?.currentItem?.track as? AppMediaItem.Audiobook)
                    ?.chapters?.takeIf { it.isNotEmpty() }
                    ?.let { chapters ->
                        val currentChapterStart =
                            chapters.lastOrNull { it.start <= currentPos }?.start ?: 0.0
                        val prevStart =
                            if (currentPos - currentChapterStart > 5) currentChapterStart
                            else chapters.lastOrNull { it.start < currentChapterStart }?.start
                                ?: 0.0
                        Request.Player.seek(queueId = data.playerId, position = prevStart.toLong())
                    }
                    ?: Request.Player.simpleCommand(playerId = data.playerId, command = "previous")
            }

            is PlayerAction.SeekTo ->
                Request.Player.seek(queueId = data.playerId, position = action.position)

            is PlayerAction.ToggleRepeatMode -> {
                val queueId = data.queueInfo?.id ?: return null
                Request.Queue.setRepeatMode(
                    queueId = queueId,
                    repeatMode = when (action.current) {
                        RepeatMode.OFF -> RepeatMode.ALL
                        RepeatMode.ALL -> RepeatMode.ONE
                        RepeatMode.ONE -> RepeatMode.OFF
                    }
                )
            }

            is PlayerAction.ToggleShuffle -> {
                val queueId = data.queueInfo?.id ?: return null
                Request.Queue.setShuffle(queueId = queueId, enabled = !action.current)
            }

            PlayerAction.VolumeDown ->
                Request.Player.simpleCommand(playerId = data.playerId, command = "volume_down")

            PlayerAction.VolumeUp ->
                Request.Player.simpleCommand(playerId = data.playerId, command = "volume_up")

            is PlayerAction.VolumeSet ->
                Request.Player.setVolume(playerId = data.playerId, volumeLevel = action.level)

            PlayerAction.GroupVolumeDown ->
                Request.Player.simpleCommand(
                    playerId = data.playerId,
                    command = "group_volume_down"
                )

            PlayerAction.GroupVolumeUp ->
                Request.Player.simpleCommand(playerId = data.playerId, command = "group_volume_up")

            is PlayerAction.GroupVolumeSet ->
                Request.Player.setGroupVolume(playerId = data.playerId, volumeLevel = action.level)

            is PlayerAction.ToggleMute ->
                Request.Player.setMute(playerId = data.playerId, !action.isMutedNow)

            is PlayerAction.GroupManage ->
                Request.Player.setGroupMembers(
                    playerId = data.playerId,
                    playersToAdd = action.toAdd,
                    playersToRemove = action.toRemove
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
                            queueItemId = action.queueItemId
                        )
                    )
                }

                is QueueAction.ClearQueue -> {
                    apiClient.sendRequest(
                        Request.Queue.clear(
                            queueId = action.queueId,
                        )
                    )
                }

                is QueueAction.RemoveItems -> {
                    action.items.forEach {
                        apiClient.sendRequest(
                            Request.Queue.removeItem(
                                queueId = action.queueId,
                                queueItemId = it
                            )
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
                                    positionShift = action.to - action.from
                                )
                            )
                        }
                }

                is QueueAction.Transfer -> {
                    apiClient.sendRequest(
                        Request.Queue.transfer(
                            sourceId = action.sourceId,
                            targetId = action.targetId,
                            autoplay = action.autoplay
                        )
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
                            val newPlayer = event.player()
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
                                                }
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
                                                oldState.data.filter { it.id != playerId }
                                            )
                                        }

                                        else -> oldState
                                    }
                                }
                            }
                        }

                        is PlayerUpdatedEvent -> {
                            val data = event.player()
                            Logger.e("Player updated: $data")
                            // Forward to local player repository if this is the local player
                            if (data.id == settings.sendspinClientId.value) {
                                localPlayerRepository.onServerPlayerUpdate(data)
                            }
                            _serverPlayers.update { oldState ->
                                when (oldState) {
                                    is DataState.Data -> {
                                        // Update position tracker with new playing state
                                        data.queueId?.let { queueId ->
                                            _positionTrackers.update { trackers ->
                                                trackers[queueId]?.let { tracker ->
                                                    trackers + (queueId to tracker.copy(
                                                        isPlaying = data.isPlaying
                                                    ))
                                                } ?: trackers
                                            }
                                        }
                                        // State update
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
                                            })
                                    }

                                    else -> oldState
                                }
                            }
                        }

                        is QueueUpdatedEvent -> {
                            val data = event.queue()
                            Logger.e("Queue updated $data")

                            // Forward to local player repository if this is the local player's queue
                            val localPlayerId = settings.sendspinClientId.value
                            if (data.id == localPlayerId ||
                                (_serverPlayers.value as? DataState.Data)?.data
                                    ?.find { it.id == localPlayerId }?.queueId == data.id
                            ) {
                                localPlayerRepository.onServerQueueUpdate(data)
                            }

                            // Update position tracker if elapsedTime is present
                            data.elapsedTime?.let { elapsed ->
                                val player =
                                    (_serverPlayers.value as? DataState.Data)?.data?.find { it.queueId == data.id }
                                _positionTrackers.update { trackers ->
                                    trackers + (data.id to PositionTracker(
                                        queueId = data.id,
                                        basePosition = elapsed,
                                        baseTimestamp = currentTimeMillis(),
                                        isPlaying = player?.isPlaying ?: false,
                                        duration = data.currentItem?.track?.duration
                                    ))
                                }
                            }

                            _queueInfos.update { value ->
                                value.map {
                                    if (it.id == data.id) data else it
                                }
                            }
                        }

                        is QueueItemsUpdatedEvent -> {
                            val data = event.queue()
                            _queueInfos.update { value ->
                                value.map {
                                    if (it.id == data.id) data else it
                                }
                            }
                            (playersData.value as? DataState.Data)?.data?.firstOrNull {
                                it.queueId == data.id
                            }?.let { refreshPlayerQueueItems(it, data) }
                        }

                        is QueueTimeUpdatedEvent -> {
                            val oldQueue = _queueInfos.value.find { it.id == event.objectId }
                            // Update position tracker
                            event.objectId?.let { queueId ->
                                val player =
                                    (_serverPlayers.value as? DataState.Data)?.data?.find { it.queueId == queueId }
                                _positionTrackers.update { trackers ->
                                    trackers + (queueId to PositionTracker(
                                        queueId = queueId,
                                        basePosition = event.data,
                                        baseTimestamp = currentTimeMillis(),
                                        isPlaying = player?.isPlaying ?: false,
                                        duration = oldQueue?.currentItem?.track?.duration
                                    ))
                                }
                            }

                            _queueInfos.update { value ->
                                value.map {
                                    if (it.id == event.objectId) it.copy(elapsedTime = event.data) else it
                                }
                            }
                        }

                        is MediaItemPlayedEvent -> {
                            _queueInfos.value.find { queue ->
                                queue.currentItem?.track?.uri == event.data.uri
                            }?.id?.let {
                                _positionTrackers.update { trackers ->
                                    trackers + (it to PositionTracker(
                                        queueId = it,
                                        basePosition = event.data.secondsPlayed,
                                        baseTimestamp = currentTimeMillis(),
                                        isPlaying = event.data.isPlaying,
                                        duration = event.data.duration
                                    ))
                                }
                            }
                        }

                        is MediaItemUpdatedEvent -> {
                            (event.data.toAppMediaItem() as? AppMediaItem.Track)
                                ?.let { updateMediaTrackInfo(it) }
                        }

                        is MediaItemAddedEvent -> {
                            (event.data.toAppMediaItem() as? AppMediaItem.Track)
                                ?.let { updateMediaTrackInfo(it) }
                        }

                        is MediaItemDeletedEvent -> {
                            (event.data.toAppMediaItem() as? AppMediaItem.Track)
                                ?.let { deletedTrack ->
                                    _playersData.update { currentState ->
                                        when (currentState) {
                                            is DataState.Error,
                                            is DataState.Loading,
                                            is DataState.NoData,
                                            is DataState.Stale -> currentState

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
                                                                            updatedItems
                                                                        )
                                                                    )
                                                                )
                                                            } ?: playerData.queue,
                                                        )
                                                    } ?: playerData
                                                })
                                        }
                                    }
                                }
                        }

                        else -> log.i { "Unhandled event: $event" }
                    }
                }
        }

    private fun updateMediaTrackInfo(newTrack: AppMediaItem.Track) {
        _playersData.update { currentState ->
            when (currentState) {
                is DataState.Error,
                is DataState.Loading,
                is DataState.NoData,
                is DataState.Stale -> currentState

                is DataState.Data -> DataState.Data(
                    currentState.data.map { playerData ->
                        playerData.queueItems?.let { items ->
                            val updatedItems = items.map { queueTrack ->
                                if ((queueTrack.track as? AppMediaItem)?.hasAnyMappingFrom(newTrack) == true) {
                                    queueTrack.copy(
                                        track = newTrack
                                    )
                                } else queueTrack
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
                                                                    queueData.data.info.currentItem.track as AppMediaItem
                                                                )
                                                            }
                                                            ?: queueData.data.info.currentItem.track
                                                    )
                                                )
                                            } else queueData.data.info,
                                            items = DataState.Data(updatedItems),
                                        )
                                    )
                                } ?: playerData.queue,
                            )
                        } ?: playerData
                    })
            }
        }
    }

    private fun updatePlayersAndQueues() {
        log.i { "Updating players and queues" }
        launch {
            apiClient.sendRequest(Request.Player.all())
                .resultAs<List<ServerPlayer>>()?.map { it.toPlayer() }
                ?.let { list ->
                    val visiblePlayers = list.filter { it.shouldBeShown }
                    _serverPlayers.update {
                        DataState.Data(visiblePlayers)
                    }
                    // Forward to repository: real player if found, synthetic if not
                    val localPlayerId = settings.sendspinClientId.value
                    val localServerPlayer = visiblePlayers.find { it.id == localPlayerId }
                    localPlayerRepository.onInitialPlayersReceived(
                        hasLocalPlayer = localServerPlayer != null
                    )
                    localServerPlayer?.let {
                        localPlayerRepository.onServerPlayerUpdate(it)
                    }
                }
        }
        launch {
            apiClient.sendRequest(Request.Queue.all())
                .resultAs<List<ServerQueue>>()?.map { it.toQueue() }?.let { list ->
                    _queueInfos.update { list }

                    // Forward local player's queue to repository
                    val localPlayerId = settings.sendspinClientId.value
                    val localQueueId = (_serverPlayers.value as? DataState.Data)?.data
                        ?.find { it.id == localPlayerId }?.queueId
                    list.find { it.id == localPlayerId || it.id == localQueueId }
                        ?.let { localPlayerRepository.onServerQueueUpdate(it) }

                    // Initialize position trackers from initial queue data
                    list.forEach { queue ->
                        queue.elapsedTime?.let { elapsed ->
                            val player =
                                (_serverPlayers.value as? DataState.Data)?.data?.find { it.queueId == queue.id }
                            _positionTrackers.update { trackers ->
                                trackers + (queue.id to PositionTracker(
                                    queueId = queue.id,
                                    basePosition = elapsed,
                                    baseTimestamp = currentTimeMillis(),
                                    isPlaying = player?.isPlaying ?: false,
                                    duration = queue.currentItem?.track?.duration
                                ))
                            }
                        }
                    }
                }
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
                            ProviderIconModel.Mdi(Icons.Default.LibraryMusic, Color.White)
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

    private fun refreshPlayerQueueItems(
        fullData: PlayerData,
        forcedQueueData: QueueInfo? = null
    ) {
        launch {
            (forcedQueueData ?: fullData.queueInfo)?.let { queueInfo ->
                val queueTracks = apiClient.sendRequest(Request.Queue.items(queueInfo.id))
                    .resultAs<List<ServerQueueItem>>()?.mapNotNull { it.toQueueTrack() }
                _playersData.update { currentState ->
                    when (currentState) {
                        is DataState.Error,
                        is DataState.Loading,
                        is DataState.NoData,
                        is DataState.Stale -> currentState

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
                                                        list
                                                    )
                                                }
                                                    ?: DataState.Error()
                                            )
                                        ),
                                        groupChildren = playerData.groupChildren,
                                        isLocal = playerData.player.id == settings.sendspinClientId.value
                                    )

                                } else playerData
                            }
                        )
                    }

                }
            }

        }
    }

    fun close() {
        supervisorJob.cancel()
    }

    fun refreshPlayersAndQueues() = updatePlayersAndQueues()

    private companion object {
        const val MAX_SENDSPIN_RETRIES = 5
    }
}