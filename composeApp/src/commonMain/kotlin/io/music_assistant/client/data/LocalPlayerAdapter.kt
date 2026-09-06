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
import io.music_assistant.client.data.model.client.presentationChapter
import io.music_assistant.client.data.model.client.toAbsoluteSeekSeconds
import io.music_assistant.client.settings.SettingsRepository
import io.music_assistant.client.ui.compose.common.DataState
import io.music_assistant.client.ui.compose.common.action.PlayerAction
import io.music_assistant.client.utils.NetworkMonitor
import io.music_assistant.client.utils.audioDispatcher
import io.music_assistant.client.utils.createPlatformHttpClient
import io.music_assistant.sendspin.SendspinPlayer
import io.music_assistant.sendspin.api.AudioSink
import io.music_assistant.sendspin.api.SendspinPlayer as SendspinPlayerApi
import io.music_assistant.sendspin.api.DecoderFactory
import io.music_assistant.sendspin.api.LocalPlayerConfig
import io.music_assistant.sendspin.api.PlayerEvent
import io.music_assistant.sendspin.api.PlayerState
import io.music_assistant.sendspin.api.SendspinDeps
import io.music_assistant.sendspin.api.StopCause
import io.music_assistant.sendspin.identity.SendspinKeyStore
import io.music_assistant.sendspin.noise.crypto.CryptographyKotlinNoiseCrypto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
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
 * App side of the local (Sendspin) player. Owns the [SendspinPlayer] through
 * its config flow ([LocalPlayerEndpoints] derives the endpoint from the MA
 * session), and keeps everything that is about the MA player model: the
 * half-synthetic [PlayerData], optimistic UI updates, the offline command
 * queue, and reconciliation of the server events the MA API forwards.
 *
 * [MainDataSource] depends on this one-way. The control plane stays on the MA
 * REST API: Sendspin is an audio endpoint (player@v1), not a controller.
 */
class LocalPlayerAdapter(
    private val settings: SettingsRepository,
    private val apiClient: ServiceClient,
    private val playerRequestFactory: PlayerRequestFactory,
    private val positionTracker: PlayerPositionTracker,
    private val userPreferences: UserPreferences,
    private val errorBus: ErrorMessageBus,
    private val endpoints: LocalPlayerEndpoints,
    sink: AudioSink,
    decoders: DecoderFactory,
    keyStore: SendspinKeyStore,
    networkMonitor: NetworkMonitor,
) : CoroutineScope {
    private val log = Logger.withTag("LocalPlayerAdapter")
    private val supervisorJob = SupervisorJob()
    override val coroutineContext: CoroutineContext = supervisorJob + Dispatchers.IO

    private val _localPlayerData = MutableStateFlow<PlayerData?>(null)

    /** The now-playing surfaces read `player.currentMedia`; fall back to the queued track before the first Play. */
    val localPlayerData: StateFlow<PlayerData?> = _localPlayerData
        .map { it?.withNowPlayingFallback() }
        .stateIn(this, SharingStarted.Eagerly, null)

    private val _optimisticQueueChanges = Channel<QueueInfo>(Channel.BUFFERED)
    val optimisticQueueChanges: Flow<QueueInfo> = _optimisticQueueChanges.receiveAsFlow()

    /** The server registered (or re-registered) the player: refetch players and queues. */
    private val _needsServerRefresh = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val needsServerRefresh: SharedFlow<Unit> = _needsServerRefresh.asSharedFlow()

    private val config: StateFlow<LocalPlayerConfig?> = combine(
        settings.sendspinEnabled,
        endpoints.endpoint,
        settings.sendspinDeviceName,
        settings.sendspinCodecPreference,
        settings.sendspinBufferCapacityMb,
        settings.sendspinStaticDelayMs,
    ) { values ->
        val enabled = values[0] as Boolean
        val endpoint = values[1] as io.music_assistant.sendspin.api.Endpoint?
        if (!enabled || endpoint == null) return@combine null
        LocalPlayerConfig(
            endpoint = endpoint,
            deviceName = values[2] as String,
            codecPreference = listOf(values[3] as io.music_assistant.sendspin.wire.AudioCodec),
            bufferCapacityBytes = (values[4] as Int) * SettingsRepository.BYTES_PER_MB,
            userDelayMs = values[5] as Int,
        )
    }.stateIn(this, SharingStarted.Eagerly, null)

    val player: SendspinPlayerApi = SendspinPlayer(
        config = config,
        deps = SendspinDeps(
            sink = sink,
            decoders = decoders,
            keyStore = keyStore,
            crypto = CryptographyKotlinNoiseCrypto(),
            httpClient = createPlatformHttpClient(),
            online = networkMonitor.isAvailable,
            pairWebPlayer = ::pairWebPlayer,
            audioDispatcher = audioDispatcher,
        ),
        scope = this,
    )

    /** Connection and audio state for the UI; null while disabled. */
    val playerState: StateFlow<PlayerState?> = player.state
        .map { it.takeUnless { state -> state == PlayerState.Disabled } }
        .stateIn(this, SharingStarted.Eagerly, null)

    /** Seconds of audio buffered ahead of the playhead, for the slider's buffered band. */
    val bufferedSeconds: StateFlow<Double> = player.state
        .map { state ->
            when (state) {
                is PlayerState.Connected -> state.audio.bufferedMs / 1_000.0
                is PlayerState.Reconnecting -> state.audio.bufferedMs / 1_000.0
                else -> 0.0
            }
        }
        .stateIn(this, SharingStarted.Eagerly, 0.0)

    private val commandQueueMutex = Mutex()
    private val commandQueue = mutableListOf<QueuedEntry>()
    private var pendingPlayTimeoutJob: Job? = null
    private var pausedByInterruption = false

    private data class QueuedEntry(val action: PlayerAction, val request: Request)

    init {
        launch { player.events.collect(::onPlayerEvent) }
        launch {
            // The server addresses the local player by the module's identity; keep the
            // persisted mirror in sync so other screens address the right player.
            player.state.collect { state ->
                if (state is PlayerState.Connected && state.playerId != settings.sendspinEffectivePlayerId.value) {
                    settings.setSendspinEffectivePlayerId(state.playerId)
                    _localPlayerData.update { it?.copy(player = it.player.copy(id = state.playerId)) }
                }
            }
        }
    }

    private suspend fun pairWebPlayer(pairingToken: String) {
        val request = Request(
            command = PAIR_WEB_PLAYER_COMMAND,
            args = kotlinx.serialization.json.buildJsonObject {
                put("pairing_token", kotlinx.serialization.json.JsonPrimitive(pairingToken))
            },
        )
        val answer = apiClient.sendRequest(request).getOrThrow()
        check(!answer.json.containsKey("error_code")) { "pairing rejected: ${answer.json["error_code"]}" }
    }

    private suspend fun onPlayerEvent(event: PlayerEvent) {
        when (event) {
            is PlayerEvent.PlaybackStarted -> {
                pausedByInterruption = false
                localPlayerData.value?.queueInfo?.id?.let(positionTracker::confirmPlaying)
                confirmLocalPlaying()
            }

            is PlayerEvent.PlaybackStopped -> when (event.cause) {
                StopCause.Starved -> {
                    log.w { "Playback stopped: buffer ran dry while disconnected" }
                    pauseLocalIfPlaying()
                    errorBus.emit(getString(Res.string.media_playback_stopped_connection_lost))
                }

                StopCause.FocusLost -> {
                    pausedByInterruption = localPlayerData.value?.player?.isPlaying == true
                    pauseLocalIfPlaying()
                }

                StopCause.SinkFailed -> pauseLocalIfPlaying()
                StopCause.ServerEnded, StopCause.Cleared, StopCause.Disabled -> Unit
            }

            PlayerEvent.FocusRegained -> if (pausedByInterruption) {
                pausedByInterruption = false
                localPlayerData.value?.let { handleLocalCommand(it, PlayerAction.Play) }
            }

            PlayerEvent.ServerRefreshNeeded -> {
                _needsServerRefresh.emit(Unit)
                drainCommandQueue()
            }

            is PlayerEvent.Warning -> log.w { "Local player warning: ${event.code}" }
        }
    }

    // --- Command entry (canonical local-player command surface) ---

    /**
     * The one entry point for every local-player command surface: in-app controls,
     * Control Center / lock screen / Android Auto transport, and interruption handling.
     * Applies the optimistic UI update, then sends or offline-queues the request.
     */
    fun handleLocalCommand(data: PlayerData, action: PlayerAction) {
        val resolved = playerRequestFactory.resolve(data, action)
        applyOptimisticUpdate(data, resolved)
        launch {
            val request = playerRequestFactory.buildRequest(data, resolved) ?: return@launch
            sendOrQueue(resolved, request)
        }
    }

    /** Platform remote command (Control Center, lock screen, CarPlay). */
    fun onRemoteCommand(command: String) {
        val playerData = localPlayerData.value ?: run {
            log.w { "No local player for remote command: $command" }
            return
        }
        remoteCommandToPlayerAction(command, playerData.queueInfo)
            ?.let { handleLocalCommand(playerData, remapChapterRelativeSeek(playerData, it)) }
            ?: log.w { "Unknown remote command: $command" }
    }

    // --- Optimistic UI updates ---

    private fun armPendingPlayTimeout() {
        pendingPlayTimeoutJob?.cancel()
        pendingPlayTimeoutJob = launch {
            delay(PENDING_PLAY_TIMEOUT_MS)
            _localPlayerData.update { current ->
                if (current?.pendingPlay == true && !current.player.isPlaying) {
                    log.w { "Pending play timed out without confirmation; clearing spinner" }
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
            PlayerAction.TogglePlayPause -> if (data.player.isPlaying) optimisticPause() else optimisticPlay()
            PlayerAction.Play -> optimisticPlay()
            PlayerAction.Pause -> optimisticPause()

            is PlayerAction.ToggleShuffle -> updateOptimisticQueueInfo { it.copy(shuffleEnabled = !action.current) }

            is PlayerAction.ToggleRepeatMode -> {
                val nextMode = when (action.current) {
                    RepeatMode.OFF -> RepeatMode.ALL
                    RepeatMode.ALL -> RepeatMode.ONE
                    RepeatMode.ONE -> RepeatMode.OFF
                }
                updateOptimisticQueueInfo { it.copy(repeatMode = nextMode) }
            }

            is PlayerAction.ToggleDontStopTheMusic -> updateOptimisticQueueInfo { it.copy(autoPlayEnabled = !action.current) }
            is PlayerAction.ToggleCrossfade -> updateOptimisticQueueInfo { it.copy(crossfadeEnabled = !action.current) }

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

            PlayerAction.Next, PlayerAction.Previous -> data.queueInfo?.id?.let { queueId ->
                positionTracker.setOptimisticTrackChange(
                    queueId = queueId,
                    elapsedSec = 0.0,
                    durationSec = data.queueInfo.currentItem?.track?.duration,
                    speed = data.queueInfo.playbackSpeed,
                )
            }

            else -> Unit
        }
    }

    private fun optimisticPlay() {
        if (!apiClient.isReadyForCommands.value) {
            log.i { "Suppressing pending local play while the command transport is not ready" }
            return
        }
        _localPlayerData.update { current -> current?.copy(pendingPlay = true) }
        armPendingPlayTimeout()
    }

    private fun optimisticPause() {
        cancelPendingPlayTimeout()
        _localPlayerData.update { current ->
            current?.copy(player = current.player.copy(isPlaying = false), pendingPlay = false)
        }
    }

    // --- Command queue (online: send immediately, offline: queue with dedup) ---

    private suspend fun sendOrQueue(action: PlayerAction, request: Request) {
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
            current.copy(
                player = if (maskHandoffPause) player.copy(isPlaying = true) else player,
                pendingPlay = if (player.isPlaying) false else current.pendingPlay,
            )
        }
    }

    fun onServerQueueUpdate(queueInfo: QueueInfo) {
        _localPlayerData.update { current ->
            current?.copy(
                queue = DataState.Data(
                    Queue(
                        info = queueInfo,
                        items = (current.queue as? DataState.Data)?.data?.items ?: DataState.NoData(),
                    ),
                ),
            )
        }
    }

    fun onQueueItemsLoaded(queueInfo: QueueInfo, items: List<QueueTrack>) {
        _localPlayerData.update { current ->
            current?.let {
                val info = (it.queue as? DataState.Data)?.data?.info ?: queueInfo
                it.copy(queue = DataState.Data(Queue(info = info, items = DataState.Data(items))))
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
                        id = settings.sendspinEffectivePlayerId.value,
                        name = settings.sendspinDeviceName.value,
                        provider = "builtin",
                        type = PlayerType.PLAYER,
                        isListed = true,
                        isAvailable = true,
                        needsSetup = false,
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

    /** Full reset: drop the optimistic UI state and pending offline commands (logout, disable). */
    fun clearState() {
        _localPlayerData.update { null }
        launch { commandQueueMutex.withLock { commandQueue.clear() } }
    }

    // --- Private helpers ---

    /** Once Sendspin confirms audio is flowing, reflect it; the false direction stays with pause. */
    private fun confirmLocalPlaying() {
        cancelPendingPlayTimeout()
        _localPlayerData.update { current ->
            if (current == null || current.player.isPlaying) return@update current
            current.copy(player = current.player.copy(isPlaying = true), pendingPlay = false)
        }
    }

    private fun pauseLocalIfPlaying() {
        localPlayerData.value?.takeIf { it.player.isPlaying }?.let { handleLocalCommand(it, PlayerAction.Pause) }
    }

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
            fun toggle(match: (PlayerAction) -> Boolean) {
                val idx = commandQueue.indexOfFirst { match(it.action) }
                if (idx >= 0) commandQueue.removeAt(idx) else commandQueue.add(entry)
            }
            when (action) {
                PlayerAction.TogglePlayPause -> toggle { it is PlayerAction.TogglePlayPause }
                PlayerAction.Play, PlayerAction.Pause -> {
                    commandQueue.removeAll { it.action is PlayerAction.Play || it.action is PlayerAction.Pause }
                    commandQueue.add(entry)
                }

                is PlayerAction.ToggleShuffle -> toggle { it is PlayerAction.ToggleShuffle }
                is PlayerAction.ToggleRepeatMode -> {
                    commandQueue.removeAll { it.action is PlayerAction.ToggleRepeatMode }
                    commandQueue.add(entry)
                }

                is PlayerAction.ToggleDontStopTheMusic -> toggle { it is PlayerAction.ToggleDontStopTheMusic }
                is PlayerAction.ToggleCrossfade -> toggle { it is PlayerAction.ToggleCrossfade }
                is PlayerAction.SeekTo -> {
                    commandQueue.removeAll { it.action is PlayerAction.SeekTo }
                    commandQueue.add(entry)
                }

                else -> commandQueue.add(entry)
            }
        }
    }

    /** Remaps chapter-relative system-scrubber SeekTo payloads to absolute seconds. */
    private fun remapChapterRelativeSeek(data: PlayerData, action: PlayerAction): PlayerAction {
        if (action !is PlayerAction.SeekTo || !userPreferences.isChapterProgressEnabled) return action
        val elapsedSec = data.queueInfo?.id?.let(positionTracker::effectiveSec)
        val chapter = data.presentationChapter(elapsedSec) ?: return action
        return PlayerAction.SeekTo(chapter.toAbsoluteSeekSeconds(action.position.toDouble()))
    }

    private companion object {
        const val PAIR_WEB_PLAYER_COMMAND = "sendspin/pair_web_player"

        /** Optimistic-bump offset; safely below any realistic server-confirmation RTT. */
        const val OPTIMISTIC_BUMP_EPSILON_S = 0.0001

        /** Backstop for play requests that neither confirm nor fail. */
        const val PENDING_PLAY_TIMEOUT_MS = 10_000L
    }
}

/**
 * Maps a platform remote-command string (Control Center / lock screen / CarPlay)
 * to the [PlayerAction] to dispatch. Toggle commands read their current state
 * from [queueInfo], defaulting to off when no queue exists.
 */
internal fun remoteCommandToPlayerAction(command: String, queueInfo: QueueInfo?): PlayerAction? = when {
    command == "play" -> PlayerAction.Play
    command == "pause" -> PlayerAction.Pause
    command == "toggle_play_pause" -> PlayerAction.TogglePlayPause
    command == "next" -> PlayerAction.Next
    command == "previous" -> PlayerAction.Previous
    command == "toggle_shuffle" -> PlayerAction.ToggleShuffle(current = queueInfo?.shuffleEnabled == true)
    command == "toggle_repeat" -> PlayerAction.ToggleRepeatMode(current = queueInfo?.repeatMode ?: RepeatMode.OFF)
    command.startsWith("seek:") -> command.removePrefix("seek:").toDoubleOrNull()?.let { PlayerAction.SeekTo(it.toLong()) }
    command.startsWith("seek_by:") -> command.removePrefix("seek_by:").toLongOrNull()?.let { PlayerAction.SeekBy(it) }
    else -> null
}
