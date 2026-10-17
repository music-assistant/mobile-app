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
import io.music_assistant.client.data.model.client.nextMovesQueue
import io.music_assistant.client.data.model.client.presentationChapter
import io.music_assistant.client.data.model.client.toAbsoluteSeekSeconds
import io.music_assistant.client.settings.SettingsRepository
import io.music_assistant.client.ui.compose.common.DataState
import io.music_assistant.client.ui.compose.common.action.PlayerAction
import io.music_assistant.client.utils.mapState
import io.music_assistant.sendspin.api.AudioCodec
import io.music_assistant.sendspin.api.Endpoint
import io.music_assistant.sendspin.api.FailureCause
import io.music_assistant.sendspin.api.LocalPlayerConfig
import io.music_assistant.sendspin.api.PlayerEvent
import io.music_assistant.sendspin.api.PlayerState
import io.music_assistant.sendspin.api.StopCause
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
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.sendspin_failed_incompatible_protocol
import musicassistantclient.composeapp.generated.resources.sendspin_failed_server_rejected
import musicassistantclient.composeapp.generated.resources.sendspin_failed_setup
import musicassistantclient.composeapp.generated.resources.sendspin_failed_unauthorized
import musicassistantclient.composeapp.generated.resources.sendspin_failed_unpaired
import org.jetbrains.compose.resources.getString
import kotlin.coroutines.CoroutineContext
import io.music_assistant.sendspin.api.SendspinPlayer as SendspinPlayerApi

/**
 * App side of the local (Sendspin) player. Owns the [SendspinPlayer] through
 * its config flow ([LocalPlayerEndpoints] derives the endpoint from the MA
 * session), and keeps everything that is about the MA player model: the
 * half-synthetic [PlayerData], optimistic UI updates, the offline command
 * queue, and reconciliation of the server events the MA API forwards.
 *
 * [MainDataSource] depends on this one-way. The control plane stays on the MA
 * REST API: Sendspin is an audio endpoint (player@v1), not a controller.
 *
 * Registered in `SharedModule` with [LocalPlayerEndpoints] and the
 * [LocalPlayerFactory], which holds the platform ports (sink, decoders, key
 * store). [approvePairing] is the app's half of silent pairing: the module
 * has no MA API client, so it calls back here to spend the pairing token on
 * `sendspin/pair_web_player`, which pairs the player without a person.
 */
class LocalPlayerAdapter(
    private val settings: SettingsRepository,
    private val apiClient: ServiceClient,
    private val playerRequestFactory: PlayerRequestFactory,
    private val positionTracker: PlayerPositionTracker,
    private val userPreferences: UserPreferences,
    private val errorBus: ErrorMessageBus,
    private val endpoints: LocalPlayerEndpoints,
    playerFactory: LocalPlayerFactory,
) : CoroutineScope {
    private val log = Logger.withTag("LocalPlayerAdapter")
    private val supervisorJob = SupervisorJob()
    override val coroutineContext: CoroutineContext = supervisorJob + Dispatchers.IO

    private val _localPlayerData = MutableStateFlow<PlayerData?>(null)

    /** The now-playing surfaces read `player.currentMedia`; fall back to the queued track before the first Play. */
    val localPlayerData: StateFlow<PlayerData?> = _localPlayerData.mapState { it?.withNowPlayingFallback() }

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
        localPlayerConfig(
            enabled = values[0] as Boolean,
            endpoint = values[1] as Endpoint?,
            deviceName = values[2] as String,
            codec = values[3] as AudioCodec,
            bufferCapacityMb = values[4] as Int,
            staticDelayMs = values[5] as Int,
        )
    }.stateIn(this, SharingStarted.Eagerly, null)

    val player: SendspinPlayerApi = playerFactory.create(config, ::approvePairing, this)

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

    private val commands = LocalCommandQueue(
        isReady = apiClient.isReadyForCommands,
        send = apiClient::sendRequest,
        scope = this,
        requestRecovery = apiClient::requestCommandRecovery,
    )
    private var pendingPlayTimeoutJob: Job? = null
    private var pausedByInterruption = false

    /** Mirror of the hold this adapter set on [player]; it masks the server's stale "playing". */
    private val held = MutableStateFlow(false)
    private val outputHeld: Boolean get() = held.value

    private fun hold(held: Boolean) {
        this.held.value = held
        player.hold(held)
    }

    /**
     * True while the local player plays, or while a local pause is not yet confirmed by the
     * server. The hold is that pause: the server still streams until the queued pause lands,
     * so the MA session must keep reconnecting instead of being torn down in the background.
     * A stop forced by an empty buffer during an outage is such a pause, like a user's.
     * Both sources are read live: `combine` can pair a new value of one with a stale value of
     * the other, and a local pause (hold first, then not playing) would read as a short false.
     */
    val wantsSession: StateFlow<Boolean> = combine(_localPlayerData, held) { _, _ ->
        _localPlayerData.value?.let { it.player.isPlaying || held.value } == true
    }.stateIn(this, SharingStarted.Eagerly, false)

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
        launch {
            // Failed is terminal. Say why once, so the player is not offered as
            // a working one that answers every command with a server error.
            // The state is a StateFlow, so it emits only on change: one message
            // per entry into Failed, and another if the player fails again
            // after a restart.
            player.state.filterIsInstance<PlayerState.Failed>()
                .collect { errorBus.emit(getString(it.cause.message)) }
        }
    }

    private val FailureCause.message
        get() = when (this) {
            FailureCause.Unauthorized -> Res.string.sendspin_failed_unauthorized
            FailureCause.Unpaired -> Res.string.sendspin_failed_unpaired
            FailureCause.ServerRejected -> Res.string.sendspin_failed_server_rejected
            FailureCause.SetupFailed -> Res.string.sendspin_failed_setup
            FailureCause.IncompatibleProtocol -> Res.string.sendspin_failed_incompatible_protocol
        }

    private suspend fun approvePairing(pairingToken: String) {
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
                // A local pause like a user's: held, queued, and answered with a pause if the
                // server starts a stream first. The player never resumes on its own.
                StopCause.Starved -> {
                    log.w { "Playback stopped: buffer ran dry while disconnected" }
                    pauseLocalIfPlaying()
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

            PlayerEvent.ServerRefreshNeeded -> _needsServerRefresh.emit(Unit)

            // The local pause never reached the server (lost, or the session was reset):
            // send it again. Built from the event's id, because the player data may be gone.
            is PlayerEvent.StreamStartedWhileHeld -> {
                log.i { "Server started a stream while held: pausing ${event.playerId}" }
                optimisticPause()
                launch {
                    // Not a user pause: a Play that waits for this reconnect still wins.
                    commands.sendOrQueue(
                        PlayerAction.Pause,
                        Request.Player.simpleCommand(playerId = event.playerId, command = "pause"),
                        userIntent = false,
                    )
                }
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
        val resolved = playerRequestFactory.resolve(data, resolveLocalToggle(data, action))
        if (resolved.movesPlayback) {
            sendOrDrop(data, resolved)
            return
        }
        applyOptimisticUpdate(data, resolved)
        launch {
            val request = playerRequestFactory.buildRequest(data, resolved) ?: return@launch
            commands.sendOrQueue(resolved, request)
        }
    }

    /**
     * Play, Next, Previous and seeks go to a reachable server or nowhere. While the session is
     * not ready, the optimistic update (it lifts the hold) waits for the send, so a dropped
     * command changes nothing. A Play that lifts a hold pauses first: the server may still play
     * the timeline that the hold discarded, and a plain play does nothing there. The pause and
     * play give a fresh stream at once.
     */
    private fun sendOrDrop(data: PlayerData, action: PlayerAction) {
        val request = playerRequestFactory.buildRequest(data, action) ?: return
        val resync = playerRequestFactory.buildRequest(data, PlayerAction.Pause)
            .takeIf { action == PlayerAction.Play && outputHeld }
        val appliedNow = apiClient.isReadyForCommands.value
        if (appliedNow) applyOptimisticUpdate(data, action)
        launch {
            commands.sendOrDrop(listOfNotNull(resync, request)) {
                if (!appliedNow) applyOptimisticUpdate(data, action)
            }
        }
    }

    /**
     * A play request from another surface (library, car, Siri, queue item) targets the local
     * player: lift the hold before the server's stream arrives, or it would be paused again.
     */
    fun onPlaybackRequested() = hold(false)

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

            is PlayerAction.ToggleDontStopTheMusic -> updateOptimisticQueueInfo {
                it.copy(
                autoPlayEnabled = !action.current,
            )
            }
            is PlayerAction.ToggleCrossfade -> updateOptimisticQueueInfo { it.copy(crossfadeEnabled = !action.current) }

            is PlayerAction.SeekTo -> {
                hold(false) // the server may start playback from the new position
                // Freeze until Sendspin confirms audio, not merely until the server echoes the seek.
                updateOptimisticQueueInfo { it.copy(elapsedTime = action.position.toDouble()) }
                data.queueInfo?.id?.let { queueId ->
                    val freezeId = positionTracker.setOptimisticSeek(
                        queueId = queueId,
                        elapsedSec = action.position.toDouble(),
                        durationSec = data.queueInfo.currentItem?.track?.duration,
                        speed = data.queueInfo.playbackSpeed,
                    )
                    releaseIfUnconfirmed(queueId, freezeId)
                }
            }

            // The server ignores a next on the last item, so nothing would release a 0:00 freeze.
            PlayerAction.Next -> if (data.queueInfo?.nextMovesQueue != false) optimisticTrackChange(data)
            PlayerAction.Previous -> optimisticTrackChange(data)

            else -> Unit
        }
    }

    private fun optimisticTrackChange(data: PlayerData) {
        hold(false) // the server starts the next track
        data.queueInfo?.id?.let { queueId ->
            val freezeId = positionTracker.setOptimisticTrackChange(
                queueId = queueId,
                elapsedSec = 0.0,
                durationSec = data.queueInfo.currentItem?.track?.duration,
                speed = data.queueInfo.playbackSpeed,
            )
            releaseIfUnconfirmed(queueId, freezeId)
        }
    }

    /** A server that ignored or rejected the command never reports the frozen position. */
    private fun releaseIfUnconfirmed(queueId: String, freezeId: Long) {
        launch {
            delay(PlayerPositionTracker.SERVER_CONFIRM_WINDOW)
            positionTracker.releaseUnconfirmed(queueId, freezeId)
        }
    }

    /** The spinner is the only feedback an offline Play gives; [armPendingPlayTimeout] bounds it. */
    private fun optimisticPlay() {
        hold(false)
        _localPlayerData.update { current -> current?.copy(pendingPlay = true) }
        armPendingPlayTimeout()
    }

    /**
     * Silences the output before the request leaves: the server may be
     * unreachable or slow, and the buffer can hold minutes of audio.
     */
    private fun optimisticPause() {
        hold(true)
        cancelPendingPlayTimeout()
        _localPlayerData.update { current ->
            current?.copy(player = current.player.copy(isPlaying = false), pendingPlay = false)
        }
    }

    // --- Server event reconciliation ---

    fun onServerPlayerUpdate(player: Player) {
        if (player.isPlaying) {
            cancelPendingPlayTimeout()
        } else {
            // The server caught up with the local pause: its next stream/start is a real play.
            hold(false)
        }
        _localPlayerData.update { current ->
            if (current == null) {
                if (!settings.sendspinEnabled.value) return@update null
                return@update PlayerData(
                    player = player.copy(isPlaying = player.isPlaying && !outputHeld),
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
                player = when {
                    // Until the server sees the local pause, its "playing" is stale.
                    outputHeld -> player.copy(isPlaying = false)
                    maskHandoffPause -> player.copy(isPlaying = true)
                    else -> player
                },
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

    /**
     * Full reset: drop the optimistic UI state and pending offline commands (logout, disable).
     * The hold stays: a pause lost here is sent again on the next stream/start while held.
     */
    fun clearState() {
        _localPlayerData.update { null }
        launch { commands.clear() }
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

    /** Remaps chapter-relative system-scrubber SeekTo payloads to absolute seconds. */
    private fun remapChapterRelativeSeek(data: PlayerData, action: PlayerAction): PlayerAction {
        if (action !is PlayerAction.SeekTo || !userPreferences.isChapterProgressEnabled) return action
        val elapsedSec = data.queueInfo?.id?.let(positionTracker::effectiveSec)
        val chapter = data.presentationChapter(elapsedSec) ?: return action
        return PlayerAction.SeekTo(chapter.toAbsoluteSeekSeconds(action.position.toDouble()))
    }

    internal companion object {
        const val PAIR_WEB_PLAYER_COMMAND = "sendspin/pair_web_player"

        /**
         * Settings to module config. The persisted static delay is a latency
         * compensation (positive = play earlier); the module's [LocalPlayerConfig.userDelayMs]
         * is a lag (positive = play later), so the sign flips here and nowhere else.
         */
        fun localPlayerConfig(
            enabled: Boolean,
            endpoint: Endpoint?,
            deviceName: String,
            codec: AudioCodec,
            bufferCapacityMb: Int,
            staticDelayMs: Int,
        ): LocalPlayerConfig? {
            if (!enabled || endpoint == null) return null
            return LocalPlayerConfig(
                endpoint = endpoint,
                deviceName = deviceName,
                codecPreference = listOf(codec),
                bufferCapacityBytes = bufferCapacityMb * SettingsRepository.BYTES_PER_MB,
                userDelayMs = -staticDelayMs,
            )
        }

        /** Optimistic-bump offset; safely below any realistic server-confirmation RTT. */
        const val OPTIMISTIC_BUMP_EPSILON_S = 0.0001

        /** Backstop for play requests that neither confirm nor fail. */
        const val PENDING_PLAY_TIMEOUT_MS = 10_000L
    }
}

/**
 * Resolves a local play/pause toggle to the explicit action the user asked for,
 * against the state they see. A queued `play_pause` would replay against the
 * server's state instead, and [LocalCommandQueue] dedups toggles in their own
 * class, so a toggle and a later Pause would both survive an outage. Local only:
 * remote players keep the atomic server-side command.
 */
internal fun resolveLocalToggle(data: PlayerData, action: PlayerAction): PlayerAction =
    if (action == PlayerAction.TogglePlayPause) {
        if (data.player.isPlaying || data.pendingPlay) PlayerAction.Pause else PlayerAction.Play
    } else {
        action
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
    command.startsWith(
        "seek:",
    ) -> command.removePrefix("seek:").toDoubleOrNull()?.let { PlayerAction.SeekTo(it.toLong()) }
    command.startsWith("seek_by:") -> command.removePrefix("seek_by:").toLongOrNull()?.let { PlayerAction.SeekBy(it) }
    else -> null
}
