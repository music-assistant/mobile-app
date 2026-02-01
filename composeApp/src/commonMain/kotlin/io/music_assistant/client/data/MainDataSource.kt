package io.music_assistant.client.data

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.ui.graphics.Color
import co.touchlab.kermit.Logger
import io.ktor.http.Url
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
import io.music_assistant.client.player.sendspin.SendspinConfig
import io.music_assistant.client.player.sendspin.SendspinConnectionState
import io.music_assistant.client.settings.SettingsRepository
import io.music_assistant.client.ui.compose.common.DataState
import io.music_assistant.client.ui.compose.common.action.PlayerAction
import io.music_assistant.client.ui.compose.common.action.QueueAction
import io.music_assistant.client.ui.compose.common.providers.ProviderIconModel
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
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
import kotlin.coroutines.CoroutineContext

@OptIn(FlowPreview::class)
class MainDataSource(
    private val settings: SettingsRepository,
    val apiClient: ServiceClient,
    private val mediaPlayerController: MediaPlayerController,
) : CoroutineScope {

    private val log = Logger.withTag("MainDataSource")

    private var sendspinClient: SendspinClient? = null

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
            }

        }.stateIn(
            scope = this,
            started = SharingStarted.Eagerly,
            initialValue = DataState.Loading()
        )

    private val _playersData = MutableStateFlow<DataState<List<PlayerData>>>(DataState.Loading())
    val playersData = _playersData.asStateFlow()

    val localPlayer = playersData
        .mapNotNull { it as? DataState.Data<List<PlayerData>> }
        .map { it.data.firstOrNull { data -> data.player.id == settings.sendspinClientId.value } }
        .stateIn(this, SharingStarted.Eagerly, null)

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
                delay(500L) // Update position twice per second for smooth progress
            }
        }

        launch {
            combine(
                _players,
                _queueInfos
            ) { players, queues -> Pair(players, queues) }
                .debounce(50L) // Small debounce to batch rapid updates, but don't delay initial load
                .collect { p ->
                    _playersData.update { oldValues ->
                        when (val playersState = p.first) {
                            is DataState.Error -> DataState.Error()
                            is DataState.Loading -> DataState.Loading()
                            is DataState.NoData -> DataState.NoData()
                            is DataState.Data -> {
                                val groupedPlayersToHide = playersState.data
                                    .map { (it.groupChildren ?: emptyList()) - it.id }
                                    .flatten().toSet()
                                val filteredPlayers = playersState.data
                                    .filter { it.id !in groupedPlayersToHide }
                                DataState.Data(
                                    filteredPlayers.map { player ->
                                        val newData = PlayerData(
                                            player = player,
                                            queue = p.second.find { it.id == player.queueId }
                                                ?.let { queueInfo ->
                                                    DataState.Data(
                                                        Queue(
                                                            info = queueInfo,
                                                            items = DataState.NoData()
                                                        )
                                                    )
                                                } ?: DataState.NoData(),
                                            groupChildren = playersState.data
                                                .mapNotNull { it.asBindFor(player) }

                                        )
                                        (oldValues as? DataState.Data)?.data
                                            ?.firstOrNull { it.player.id == player.id }
                                            ?.updateFrom(newData) ?: newData

                                    }
                                )
                            }
                        }
                    }
                }
        }
        launch {
            apiClient.sessionState.collect {
                when (it) {
                    is SessionState.Connected -> {
                        watchJob = watchApiEvents()
                        if (it.dataConnectionState == DataConnectionState.Authenticated) {
                            _serverPlayers.update { DataState.Loading() }
                            updateProvidersManifests()
                            initSendspinIfEnabled()
                            updatePlayersAndQueues()
                        } else {
                            stopSendspin()
                            _serverPlayers.update { DataState.NoData() }
                            _queueInfos.update { emptyList() }
                            updateJob?.cancel()
                            updateJob = null
                            watchJob?.cancel()
                            watchJob = null
                        }
                    }

                    is SessionState.Reconnecting -> {
                        // Preserve state during reconnection - don't stop anything!
                        // Sendspin keeps playing, jobs keep running
                        // When reconnected, Connected branch will re-initialize if needed
                    }

                    SessionState.Connecting -> {
                        stopSendspin()
                        updateJob?.cancel()
                        updateJob = null
                        watchJob?.cancel()
                        watchJob = null
                        _serverPlayers.update { DataState.Loading() }
                    }

                    is SessionState.Disconnected -> {
                        stopSendspin()
                        _serverPlayers.update { DataState.NoData() }
                        _queueInfos.update { emptyList() }
                        updateJob?.cancel()
                        updateJob = null
                        watchJob?.cancel()
                        watchJob = null
                    }
                }
            }
        }
        launch {
            playersData.mapNotNull { (it as? DataState.Data)?.data }.collect { playersList ->
                // Auto-select first player if no player is selected
                if (playersList.isNotEmpty()
                    && playersList.none { data -> data.playerId == _selectedPlayerId.value }
                ) {
                    _selectedPlayerId.update { playersList.getOrNull(0)?.playerId }
                }
                // Don't call updatePlayersAndQueues() here - it creates a reactive loop!
                // Updates are triggered by sessionState changes and API events.
            }
        }
        launch {
            selectedPlayerIndex.filterNotNull().collect { index ->
                (playersData.value as? DataState.Data)?.data?.let { list ->
                    refreshPlayerQueueItems(list[index])
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
    }

    /**
     * Initialize Sendspin player if enabled in settings.
     * Safe for background: MainDataSource is singleton held by foreground service.
     */
    private suspend fun initSendspinIfEnabled() {
        if (!settings.sendspinEnabled.value) {
            log.i { "Sendspin disabled in settings, skipping initialization" }
            return
        }

        val serverHost = settings.connectionInfo.value?.webUrl?.let { url ->
            try {
                Url(url).host
            } catch (e: Exception) {
                log.e(e) { "Failed to parse server URL: $url" }
                null
            }
        }

        if (serverHost == null) {
            log.w { "No server host available, cannot initialize Sendspin" }
            return
        }

        // Stop existing client if any
        sendspinClient?.let { existing ->
            if (existing.connectionState.value is SendspinConnectionState.Connected) {
                // Already connected, don't reconnect
                return
            }
            existing.stop()
            existing.close()
        }

        // Build Sendspin config
        val config = SendspinConfig(
            clientId = settings.sendspinClientId.value,
            deviceName = settings.sendspinDeviceName.value,
            enabled = true,
            bufferCapacityMicros = 500_000, // 500ms
            codecPreference = settings.sendspinCodecPreference.value,
            serverHost = settings.sendspinHost.value.takeIf { it.isNotEmpty() } ?: serverHost,
            serverPort = settings.sendspinPort.value,
            serverPath = settings.sendspinPath.value,
            useTls = settings.sendspinUseTls.value
        )

        log.i { "Initializing Sendspin client: $serverHost:${config.serverPort}" }

        // Set up remote command handler for Control Center/Lock Screen commands
        // Commands go directly through MainDataSource via REST API
        mediaPlayerController.onRemoteCommand = { command ->
            localPlayer.value?.let { playerData ->
                log.i { "Remote command from Control Center: $command" }
                when (command) {
                    "play", "pause", "toggle_play_pause" -> playerAction(
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

        try {
            sendspinClient = SendspinClient(config, mediaPlayerController).also { client ->
                launch {
                    // Monitor for playback errors (e.g., Android Auto disconnect, audio output changed)
                    // and pause the MA server player when they occur
                    client.playbackStoppedDueToError.filterNotNull().collect { error ->
                        log.w(error) { "Sendspin playback stopped due to error - pausing MA server player" }
                        // Pause the local sendspin player on the MA server
                        localPlayer.value?.let { playerData ->
                            if (playerData.player.isPlaying) {
                                log.i { "Sending pause command to MA server for player ${playerData.player.name}" }
                                playerAction(playerData, PlayerAction.TogglePlayPause)
                            }
                        }
                    }
                }

                launch {
                    // Monitor connection state and refresh player list when Sendspin connects
                    // This ensures the local player appears immediately in the UI
                    client.connectionState.collect { state ->
                        if (state is SendspinConnectionState.Connected) {
                            log.i { "Sendspin connected - refreshing player list" }
                            delay(1000) // Give server a moment to register the player
                            updatePlayersAndQueues()
                        }
                    }
                }

                client.start()
            }

        } catch (e: Exception) {
            log.e(e) { "Failed to initialize Sendspin client" }
        }
    }

    /**
     * Stop Sendspin player if running.
     */
    private suspend fun stopSendspin() {
        sendspinClient?.let { client ->
            log.i { "Stopping Sendspin client" }
            try {
                client.stop()
                client.close()
            } catch (e: Exception) {
                log.e(e) { "Error stopping Sendspin client" }
            }
            sendspinClient = null
        }
    }


    fun selectPlayer(player: Player) {
        _selectedPlayerId.update { player.id }
    }

    fun playerAction(playerId: String, action: PlayerAction) {
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
        launch {
            when (action) {
                PlayerAction.TogglePlayPause -> {
                    apiClient.sendRequest(
                        Request.Player.simpleCommand(
                            playerId = data.playerId,
                            command = "play_pause"
                        )
                    )
                }

                PlayerAction.Next -> {
                    apiClient.sendRequest(
                        Request.Player.simpleCommand(playerId = data.playerId, command = "next")
                    )
                }

                PlayerAction.Previous -> {
                    apiClient.sendRequest(
                        Request.Player.simpleCommand(
                            playerId = data.playerId,
                            command = "previous"
                        )
                    )
                }

                is PlayerAction.SeekTo -> {
                    apiClient.sendRequest(
                        Request.Player.seek(
                            queueId = data.playerId,
                            position = action.position
                        )
                    )
                }

                is PlayerAction.ToggleRepeatMode -> apiClient.sendRequest(
                    Request.Queue.setRepeatMode(
                        queueId = data.queueInfo?.id ?: return@launch,
                        repeatMode = when (action.current) {
                            RepeatMode.OFF -> RepeatMode.ALL
                            RepeatMode.ALL -> RepeatMode.ONE
                            RepeatMode.ONE -> RepeatMode.OFF
                        }
                    )
                )

                is PlayerAction.ToggleShuffle -> apiClient.sendRequest(
                    Request.Queue.setShuffle(
                        queueId = data.queueInfo?.id ?: return@launch,
                        enabled = !action.current
                    )
                )

                PlayerAction.VolumeDown -> apiClient.sendRequest(
                    Request.Player.simpleCommand(
                        playerId = data.playerId,
                        command = "volume_down"
                    )
                )

                PlayerAction.VolumeUp -> apiClient.sendRequest(
                    Request.Player.simpleCommand(
                        playerId = data.playerId,
                        command = "volume_up"
                    )
                )


                is PlayerAction.VolumeSet -> apiClient.sendRequest(
                    Request.Player.setVolume(
                        playerId = data.playerId,
                        volumeLevel = action.level
                    )
                )

                PlayerAction.GroupVolumeDown -> apiClient.sendRequest(
                    Request.Player.simpleCommand(
                        playerId = data.playerId,
                        command = "group_volume_down"
                    )
                )

                PlayerAction.GroupVolumeUp -> apiClient.sendRequest(
                    Request.Player.simpleCommand(
                        playerId = data.playerId,
                        command = "group_volume_up"
                    )
                )

                is PlayerAction.GroupVolumeSet -> apiClient.sendRequest(
                    Request.Player.setGroupVolume(
                        playerId = data.playerId,
                        volumeLevel = action.level
                    )
                )

                PlayerAction.ToggleMute -> apiClient.sendRequest(
                    Request.Player.setMute(playerId = data.playerId, !data.player.volumeMuted)
                )

                is PlayerAction.GroupManage -> apiClient.sendRequest(
                    Request.Player.setGroupMembers(
                        playerId = data.playerId,
                        playersToAdd = action.toAdd,
                        playersToRemove = action.toRemove
                    )
                )
            }
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
                                        if (players.isEmpty()) {
                                            oldState
                                        } else DataState.Data(
                                            if (data.shouldBeShown) {
                                                players.map { if (it.id == data.id) data else it }
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
                                            is DataState.NoData -> currentState

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
                is DataState.NoData -> currentState

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
                    _serverPlayers.update {
                        DataState.Data(list.filter { it.shouldBeShown })
                    }
                }
        }
        launch {
            apiClient.sendRequest(Request.Queue.all())
                .resultAs<List<ServerQueue>>()?.map { it.toQueue() }?.let { list ->
                    _queueInfos.update { list }

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
                        is DataState.NoData -> currentState

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
                                        groupChildren = playerData.groupChildren
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

}