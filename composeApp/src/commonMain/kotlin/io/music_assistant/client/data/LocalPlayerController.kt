package io.music_assistant.client.data

import co.touchlab.kermit.Logger
import io.music_assistant.client.api.ErrorMessageBus
import io.music_assistant.client.api.Request
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.data.model.client.ImageType
import io.music_assistant.client.data.model.client.Player
import io.music_assistant.client.data.model.client.PlayerData
import io.music_assistant.client.data.model.client.PlayerMedia
import io.music_assistant.client.data.model.client.PlayerType
import io.music_assistant.client.data.model.client.Queue
import io.music_assistant.client.data.model.client.QueueInfo
import io.music_assistant.client.data.model.client.QueueTrack
import io.music_assistant.client.data.model.client.RepeatMode
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.client.items.image
import io.music_assistant.client.player.MediaPlayerController
import io.music_assistant.client.player.sendspin.SendspinClient
import io.music_assistant.client.player.sendspin.SendspinClientFactory
import io.music_assistant.client.player.sendspin.SendspinError
import io.music_assistant.client.player.sendspin.SendspinState
import io.music_assistant.client.player.sendspin.WebRTCSendspinChannelExhausted
import io.music_assistant.client.player.sendspin.model.GoodbyeReason
import io.music_assistant.client.settings.SettingsRepository
import io.music_assistant.client.ui.compose.common.DataState
import io.music_assistant.client.ui.compose.common.action.PlayerAction
import io.music_assistant.client.utils.SessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.media_playback_stopped_connection_lost
import org.jetbrains.compose.resources.getString
import kotlin.coroutines.CoroutineContext

/**
 * Single owner of the local (Sendspin) player: its lifecycle (transport client
 * start/stop/monitor), its half-synthetic [PlayerData] state, optimistic UI
 * updates, the offline command queue, and reconciliation of the server events
 * the MA API still forwards for it.
 *
 * [MainDataSource] depends on this one-way: it merges [localPlayerData] into the
 * unified player list, mirrors [optimisticQueueChanges], collects
 * [needsServerRefresh], routes the local player's commands here, and forwards
 * matched server events to the `onServer*` reconcilers. This controller never
 * calls back into [MainDataSource]; the only "please refresh the server list"
 * signal goes out via [needsServerRefresh].
 *
 * The control plane stays on the MA server REST API: Sendspin is an audio
 * rendering endpoint (player@v1 role only), not a controller, so commands are
 * not migrated onto it.
 */
class LocalPlayerController(
    private val settings: SettingsRepository,
    private val apiClient: ServiceClient,
    private val mediaPlayerController: MediaPlayerController,
    private val sendspinClientFactory: SendspinClientFactory,
    private val playerRequestFactory: PlayerRequestFactory,
    private val positionTracker: PlayerPositionTracker,
    private val errorBus: ErrorMessageBus,
) : CoroutineScope {
    private val log = Logger.withTag("LocalPlayerCtrl")

    private val supervisorJob = SupervisorJob()
    override val coroutineContext: CoroutineContext = supervisorJob + Dispatchers.IO

    private val _localPlayerData = MutableStateFlow<PlayerData?>(null)

    // Backstop that clears a stuck resume spinner if the server never confirms play.
    private var pendingPlayTimeoutJob: Job? = null

    // Exposed view applies [withNowPlayingFallback] so the now-playing surfaces (AA /
    // notification / phone card, all of which read `player.currentMedia`) show the queued
    // track instead of "Unknown" before the first Play. Internal mutations stay on the raw
    // [_localPlayerData].
    val localPlayerData: StateFlow<PlayerData?> = _localPlayerData
        .map { it?.withNowPlayingFallback() }
        .stateIn(this, SharingStarted.Eagerly, null)

    /** Optimistic local-queue mutations; mirrored into `_queueInfos` by `MainDataSource`. */
    private val _optimisticQueueChanges = Channel<QueueInfo>(Channel.BUFFERED)
    val optimisticQueueChanges: Flow<QueueInfo> = _optimisticQueueChanges.receiveAsFlow()

    // --- Sendspin transport lifecycle ---

    private var sendspinClient: SendspinClient? = null
    private var sendspinMonitorJobs = mutableListOf<Job>()
    private var sendspinRetryCount = 0
    private val sendspinMutex = Mutex()

    private val _sendspinState = MutableStateFlow<SendspinState?>(null)
    val sendspinState: StateFlow<SendspinState?> = _sendspinState.asStateFlow()

    /**
     * Fires after Sendspin registers (state → Ready) so [MainDataSource] re-fetches
     * the server players/queues — replaces the former direct `updatePlayersAndQueues()`
     * callback and keeps the dependency one-way.
     */
    private val _needsServerRefresh = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val needsServerRefresh: SharedFlow<Unit> = _needsServerRefresh.asSharedFlow()

    private val commandQueueMutex = Mutex()
    private val commandQueue = mutableListOf<QueuedEntry>()

    private data class QueuedEntry(val action: PlayerAction, val request: Request)

    init {
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
    }

    // --- Command entry (canonical local-player command surface) ---

    /**
     * The one entry point for every local-player command surface (in-app controls,
     * Control Center / lock screen / Android Auto transport, playback-error auto-pause).
     * Applies the optimistic UI update immediately, then sends — or offline-queues — the
     * request. Routes uniformly through the MA REST API (no transport split).
     */
    fun handleLocalCommand(data: PlayerData, action: PlayerAction) {
        val resolved = playerRequestFactory.resolve(data, action)
        applyOptimisticUpdate(data, resolved)
        launch {
            val request = playerRequestFactory.buildRequest(data, resolved) ?: return@launch
            sendOrQueue(resolved, request)
        }
    }

    // --- Optimistic UI updates ---

    /** Backstop for a play request that never gets a positive server confirmation. */
    private fun armPendingPlayTimeout() {
        pendingPlayTimeoutJob?.cancel()
        pendingPlayTimeoutJob = launch {
            delay(PENDING_PLAY_TIMEOUT_MS)
            _localPlayerData.update { current ->
                if (current?.pendingPlay == true && current.player.isPlaying.not()) {
                    log.w { "Pending play timed out without server confirmation; clearing spinner" }
                    current.copy(pendingPlay = false)
                } else {
                    current
                }
            }
        }
    }

    private fun cancelPendingPlayTimeout() {
        pendingPlayTimeoutJob?.cancel()
        pendingPlayTimeoutJob = null
    }

    private fun applyOptimisticUpdate(data: PlayerData, action: PlayerAction) {
        when (action) {
            PlayerAction.TogglePlayPause -> {
                val wasPlaying = data.player.isPlaying
                if (wasPlaying) {
                    cancelPendingPlayTimeout()
                    mediaPlayerController.pauseSink()
                    _localPlayerData.update { current ->
                        current?.copy(
                            player = current.player.copy(isPlaying = false),
                            pendingPlay = false,
                        )
                    }
                } else {
                    if (!apiClient.isReadyForCommands.value) {
                        log.i { "Suppressing pending local play while command transport is not ready" }
                        return
                    }
                    mediaPlayerController.resumeSink()
                    _localPlayerData.update { current -> current?.copy(pendingPlay = true) }
                    armPendingPlayTimeout()
                }
            }

            PlayerAction.Play -> {
                if (!apiClient.isReadyForCommands.value) {
                    log.i { "Suppressing pending local play while command transport is not ready" }
                    return
                }
                mediaPlayerController.resumeSink()
                _localPlayerData.update { current -> current?.copy(pendingPlay = true) }
                armPendingPlayTimeout()
            }

            PlayerAction.Pause -> {
                cancelPendingPlayTimeout()
                mediaPlayerController.pauseSink()
                _localPlayerData.update { current ->
                    current?.copy(
                        player = current.player.copy(isPlaying = false),
                        pendingPlay = false,
                    )
                }
            }

            is PlayerAction.ToggleShuffle -> {
                updateOptimisticQueueInfo { it.copy(shuffleEnabled = !action.current) }
            }

            is PlayerAction.ToggleRepeatMode -> {
                val nextMode = when (action.current) {
                    RepeatMode.OFF -> RepeatMode.ALL
                    RepeatMode.ALL -> RepeatMode.ONE
                    RepeatMode.ONE -> RepeatMode.OFF
                }
                updateOptimisticQueueInfo { it.copy(repeatMode = nextMode) }
            }

            is PlayerAction.ToggleDontStopTheMusic -> {
                updateOptimisticQueueInfo { it.copy(autoPlayEnabled = !action.current) }
            }

            is PlayerAction.SeekTo -> {
                // Freeze until Sendspin confirms audio, not merely until the server echoes the seek.
                updateOptimisticQueueInfo { it.copy(elapsedTime = action.position.toDouble()) }
                data.queueInfo?.id?.let { queueId ->
                    positionTracker.setOptimisticSeek(
                        queueId = queueId,
                        elapsedSec = action.position.toDouble(),
                        durationSec = data.queueInfo.currentItem?.track?.duration,
                        speed = data.queueInfo.playbackSpeed,
                    )
                }
            }

            PlayerAction.Next,
            PlayerAction.Previous,
            -> {
                // Queue events for the next track can arrive before its audio; keep the
                // boundary frozen until Sendspin confirms the new stream.
                data.queueInfo?.id?.let { queueId ->
                    positionTracker.setOptimisticTrackChange(
                        queueId = queueId,
                        elapsedSec = 0.0,
                        durationSec = data.queueInfo.currentItem?.track?.duration,
                        speed = data.queueInfo.playbackSpeed,
                    )
                }
            }

            else -> {}
        }
    }

    // --- Command queue (online: send immediately, offline: queue with dedup) ---

    private suspend fun sendOrQueue(action: PlayerAction, request: Request) {
        // Fast-path queue when known-offline avoids burning the gate's 10s timeout
        // on actions the user already perceives as "do this when we're back."
        // The Result.isFailure fallback closes the TOCTOU window where the state
        // flips between the check and the send.
        if (!apiClient.isReadyForCommands.value) {
            enqueue(action, request)
            return
        }
        if (apiClient.sendRequest(request).isFailure) enqueue(action, request)
    }

    fun drainCommandQueue() {
        launch {
            val entries = commandQueueMutex.withLock {
                if (commandQueue.isEmpty()) return@launch
                log.i { "Draining ${commandQueue.size} queued commands" }
                commandQueue.toList().also { commandQueue.clear() }
            }
            entries.forEach { entry ->
                apiClient.sendRequest(entry.request)
                delay(100)
            }
        }
    }

    // --- Server event reconciliation ---

    fun onServerPlayerUpdate(player: Player) {
        if (player.isPlaying) cancelPendingPlayTimeout()
        _localPlayerData.update { current ->
            if (current == null) {
                if (!settings.sendspinEnabled.value) return@update null
                return@update PlayerData(
                    player = player,
                    queue = DataState.NoData(),
                    parentBind = null,
                    childrenBinds = emptyList(),
                    isLocal = true,
                )
            }
            // Mask transient server pauses during a frozen handoff; real local pauses
            // already flipped current.player.isPlaying before this reconciliation runs.
            val queueId = current.queueInfo?.id
            val maskHandoffPause = !player.isPlaying &&
                current.player.isPlaying &&
                queueId != null &&
                positionTracker.isFrozenUntilConfirmed(queueId)
            val resolvedPlayer = if (maskHandoffPause) player.copy(isPlaying = true) else player
            current.copy(
                player = resolvedPlayer,
                // Do not let stale not-playing echoes clear a pending resume.
                pendingPlay = if (player.isPlaying) false else current.pendingPlay,
            )
        }
    }

    fun onServerQueueUpdate(queueInfo: QueueInfo) {
        // Staleness gating happens upstream in [MainDataSource.gateOrSkip]
        // before we're called, so this just absorbs the admitted event.
        _localPlayerData.update { current ->
            current?.copy(
                queue = DataState.Data(
                    Queue(
                        info = queueInfo,
                        items = (current.queue as? DataState.Data)?.data?.items
                            ?: DataState.NoData(),
                    ),
                ),
            )
        }
    }

    fun onQueueItemsLoaded(queueInfo: QueueInfo, items: List<QueueTrack>) {
        _localPlayerData.update { current ->
            current?.let {
                // Preserve the authoritative `info` (shuffle/repeat/elapsed are
                // owned by the optimistic + server-queue-update path); an item
                // load must not write a stale snapshot back over it. Fall back to
                // the loaded `queueInfo` only when no info exists yet.
                val info = (it.queue as? DataState.Data)?.data?.info ?: queueInfo
                it.copy(
                    queue = DataState.Data(Queue(info = info, items = DataState.Data(items))),
                )
            }
        }
    }

    // --- Synthetic player ---

    fun onInitialPlayersReceived(hasLocalPlayer: Boolean) {
        if (!settings.sendspinEnabled.value) {
            _localPlayerData.update { null }
            return
        }
        if (!hasLocalPlayer && _localPlayerData.value == null) {
            log.i { "Injecting synthetic local player" }
            _localPlayerData.update {
                PlayerData(
                    player = Player(
                        id = settings.sendspinClientId.value,
                        name = settings.sendspinDeviceName.value,
                        provider = "builtin",
                        type = PlayerType.PLAYER,
                        shouldBeShown = true,
                        canSetVolume = false,
                        volumeLevel = null,
                        volumeControl = null,
                        volumeMuted = false,
                        canMute = false,
                        queueId = null,
                        isPlaying = false,
                        isAnnouncing = false,
                        canGroupWith = null,
                        groupMembers = null,
                        staticGroupMembers = null,
                        groupVolume = null,
                        groupVolumeMuted = false,
                        activeGroup = null,
                        syncedTo = null,
                        currentMedia = null,
                        canPower = false,
                        isPowered = true,
                    ),
                    queue = DataState.NoData(),
                    parentBind = null,
                    childrenBinds = emptyList(),
                    isLocal = true,
                )
            }
        }
    }

    // --- Lifecycle ---

    /**
     * Initialize the Sendspin client if enabled in settings.
     * Safe for background: this controller is a singleton held by the foreground service.
     */
    suspend fun start() = sendspinMutex.withLock {
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
            existing.stop(GoodbyeReason.Restart)
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
                // After reconnection, start() will be called again
                // from the session state handler with a fresh channel.
                return@withLock
            }
            log.w { "Cannot create Sendspin client: ${error.message}" }
            return@withLock
        }

        val client = createResult.getOrNull() ?: return@withLock

        // Set up remote command handler for Control Center/Lock Screen commands.
        // Routed through the canonical local-command entry.
        mediaPlayerController.onRemoteCommand = { command ->
            localPlayerData.value?.let { playerData ->
                log.i { "Remote command: $command" }
                when (command) {
                    "play" -> handleLocalCommand(playerData, PlayerAction.Play)
                    "pause" -> handleLocalCommand(playerData, PlayerAction.Pause)
                    "toggle_play_pause" -> handleLocalCommand(
                        playerData,
                        PlayerAction.TogglePlayPause,
                    )

                    "next" -> handleLocalCommand(playerData, PlayerAction.Next)
                    "previous" -> handleLocalCommand(playerData, PlayerAction.Previous)
                    else -> {
                        if (command.startsWith("seek:")) {
                            command.removePrefix("seek:").toDoubleOrNull()?.let { position ->
                                handleLocalCommand(playerData, PlayerAction.SeekTo(position.toLong()))
                            }
                        } else if (command.startsWith("seek_by:")) {
                            command.removePrefix("seek_by:").toLongOrNull()?.let { offset ->
                                handleLocalCommand(playerData, PlayerAction.SeekBy(offset))
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
            // and pause the MA server player when they occur.
            client.playbackStoppedDueToError.filterNotNull().collect { pauseLocalIfPlaying() }
        }

        sendspinMonitorJobs += launch {
            // Tear playback down only when all three hold at once: we're playing, the audio buffer
            // has run dry, and the transport is actually down. A dry buffer while the transport is
            // up is a normal transient — pause/resume or post-(re)connect ramp-up — and must NOT
            // stop playback. This is a pure reactive composition of current state; no heuristics
            // about how the buffer got empty. The pause is authoritative: the Error-retry loop
            // below may reconnect, but it won't auto-resume a paused player, so the two don't fight.
            combine(client.state, client.isStarved, localPlayerData) { state, starved, data ->
                starved &&
                    data?.player?.isPlaying == true &&
                    (state is SendspinState.Reconnecting || state is SendspinState.Error)
            }.distinctUntilChanged().collect { lostDuringPlayback ->
                if (lostDuringPlayback) {
                    log.w { "Buffer drained while transport is down — stopping local playback" }
                    pauseLocalIfPlaying()
                    client.stopStream()
                    errorBus.emit(getString(Res.string.media_playback_stopped_connection_lost))
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
                        _needsServerRefresh.emit(Unit)
                    }

                    is SendspinState.Error -> {
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
                                        start()
                                    } catch (_: Exception) {
                                        coroutineContext.ensureActive()
                                    }
                                }
                            }
                        }
                    }

                    is SendspinState.Idle -> Unit

                    is SendspinState.Synchronized -> {
                        localPlayerData.value?.queueInfo?.id?.let(positionTracker::confirmPlaying)
                    }

                    is SendspinState.Reconnecting -> Unit
                    else -> Unit
                }
            }
        }
    }

    /** Pause the local player on the MA server, but only if it's currently playing. */
    private fun pauseLocalIfPlaying() {
        localPlayerData.value?.let { playerData ->
            if (playerData.player.isPlaying) {
                handleLocalCommand(playerData, PlayerAction.Pause)
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
     * Stop the Sendspin client if running.
     * Destroys the shared audio pipeline so the AudioTrack is fully released.
     */
    suspend fun stop(reason: GoodbyeReason) = sendspinMutex.withLock {
        // Cancel monitor jobs FIRST to prevent old state transitions from leaking
        cancelSendspinMonitorJobs()
        sendspinRetryCount = 0
        sendspinClient?.let { client ->
            try {
                client.stop(reason)
                client.close()
            } catch (e: Exception) {
                log.e(e) { "Error stopping Sendspin client" }
            }
            sendspinClient = null
        }
        _sendspinState.value = null
        // Deliberately preserve local-player state and the offline command queue:
        // most callers (background, reconnect, persistent error) are transient and
        // rely on drainCommandQueue() replaying queued intent — e.g. a post-
        // interruption resume — once the transport returns. Genuine resets clear
        // them explicitly (clearAllData / Sendspin-disabled).
        //
        // The shared audio pipeline (buffer + consumer + AudioTrack) is decoupled from
        // transport churn: only a genuine reset destroys it. On a transient Restart we keep
        // it alive and draining so buffered audio survives the reconnect — the next start()
        // reuses it via the factory.
        if (reason != GoodbyeReason.Restart) {
            sendspinClientFactory.destroyPipeline()
        }
    }

    /** Full local-player reset: drop the optimistic UI state and any pending offline
     *  commands. Called only on a genuine session reset (logout, terminal auth failure,
     *  Sendspin disabled). NOT called on transient teardown — [stop] deliberately
     *  preserves both so [drainCommandQueue] can replay a queued resume once the
     *  transport returns. */
    fun clearState() {
        _localPlayerData.update { null }
        launch { commandQueueMutex.withLock { commandQueue.clear() } }
    }

    // --- Private helpers ---

    /**
     * The MA server leaves a stopped player's `current_media` null until playback loads
     * media into it, yet the queue's `currentItem` is known immediately. Synthesize a
     * display media from that queue item so the now-playing surfaces render the track
     * rather than "Unknown" before the first Play. Real `currentMedia` (once the server
     * sends it) always wins via the not-blank guard.
     */
    private fun PlayerData.withNowPlayingFallback(): PlayerData {
        if (player.currentMedia?.title?.isNotBlank() == true) return this
        val item = queueInfo?.currentItem ?: return this
        val track = item.track
        return copy(
            player = player.copy(
                currentMedia = PlayerMedia(
                    title = track.displayName,
                    artist = track.subtitle,
                    album = null,
                    imageUrl = track.image(ImageType.THUMB)?.url,
                    duration = track.duration,
                    queueId = queueInfo.id,
                    queueItemId = item.id,
                    mediaType = (track as? AppMediaItem)?.mediaType,
                    uri = track.uri,
                ),
            ),
        )
    }

    private fun updateOptimisticQueueInfo(transform: (QueueInfo) -> QueueInfo) {
        // Bump elapsedTimeLastUpdated above the last known server stamp so
        // stale replays drop while real confirmations (RTT >> epsilon) override.
        val newState = _localPlayerData.updateAndGet { current ->
            current?.let { pd ->
                val queueData = pd.queue as? DataState.Data ?: return@updateAndGet pd
                val existingStamp = queueData.data.info.elapsedTimeLastUpdated ?: 0.0
                val transformed = transform(queueData.data.info).copy(
                    elapsedTimeLastUpdated = existingStamp + OPTIMISTIC_BUMP_EPSILON_S,
                )
                pd.copy(queue = DataState.Data(queueData.data.copy(info = transformed)))
            }
        }
        (newState?.queue as? DataState.Data)?.data?.info?.let { _optimisticQueueChanges.trySend(it) }
    }

    private suspend fun enqueue(action: PlayerAction, request: Request) {
        commandQueueMutex.withLock {
            val entry = QueuedEntry(action, request)
            when (action) {
                PlayerAction.TogglePlayPause -> {
                    val idx = commandQueue.indexOfFirst { it.action is PlayerAction.TogglePlayPause }
                    if (idx >= 0) commandQueue.removeAt(idx) else commandQueue.add(entry)
                }

                PlayerAction.Play -> {
                    commandQueue.removeAll { it.action is PlayerAction.Play || it.action is PlayerAction.Pause }
                    commandQueue.add(entry)
                }

                PlayerAction.Pause -> {
                    commandQueue.removeAll { it.action is PlayerAction.Play || it.action is PlayerAction.Pause }
                    commandQueue.add(entry)
                }

                is PlayerAction.ToggleShuffle -> {
                    val idx = commandQueue.indexOfFirst { it.action is PlayerAction.ToggleShuffle }
                    if (idx >= 0) commandQueue.removeAt(idx) else commandQueue.add(entry)
                }

                is PlayerAction.ToggleRepeatMode -> {
                    commandQueue.removeAll { it.action is PlayerAction.ToggleRepeatMode }
                    commandQueue.add(entry)
                }

                is PlayerAction.ToggleDontStopTheMusic -> {
                    val idx = commandQueue.indexOfFirst { it.action is PlayerAction.ToggleDontStopTheMusic }
                    if (idx >= 0) commandQueue.removeAt(idx) else commandQueue.add(entry)
                }

                is PlayerAction.SeekTo -> {
                    Logger.e("SeekTo: ${action.position}")
                    commandQueue.removeAll { it.action is PlayerAction.SeekTo }
                    commandQueue.add(entry)
                }

                else -> commandQueue.add(entry)
            }
        }
    }

    private companion object {
        private const val MAX_SENDSPIN_RETRIES = 5

        /** Optimistic-bump offset; safely below any realistic server-confirmation RTT. */
        const val OPTIMISTIC_BUMP_EPSILON_S = 0.0001

        /** Backstop for play requests that neither confirm nor fail. */
        private const val PENDING_PLAY_TIMEOUT_MS = 10_000L
    }
}
