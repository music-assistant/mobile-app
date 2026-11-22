package io.music_assistant.client.data.share

import co.touchlab.kermit.Logger
import io.music_assistant.client.api.ErrorMessageBus
import io.music_assistant.client.data.model.client.QueueOption
import io.music_assistant.client.data.model.server.sendspinSourceUri
import io.music_assistant.client.data.play.PlayRequest
import io.music_assistant.sendspin.api.Endpoint
import io.music_assistant.sendspin.api.PlayerState
import io.music_assistant.sendspin.api.SourceDialect
import io.music_assistant.sendspin.api.SourceSignal
import io.music_assistant.sendspin.api.SourceStatus
import io.music_assistant.sendspin.api.WireVersion
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.device_audio_end_connection_lost
import musicassistantclient.composeapp.generated.resources.device_audio_end_refused
import musicassistantclient.composeapp.generated.resources.device_audio_end_revoked
import musicassistantclient.composeapp.generated.resources.device_audio_end_server_stopped
import musicassistantclient.composeapp.generated.resources.device_audio_end_silence
import musicassistantclient.composeapp.generated.resources.device_audio_end_timeout
import musicassistantclient.composeapp.generated.resources.device_audio_end_unpaired
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString

sealed interface DeviceAudioShareState {
    data object Idle : DeviceAudioShareState

    /** The share starts: the server has not played the device audio on [queueId] yet. */
    data class Starting(val queueId: String) : DeviceAudioShareState

    /** The server plays the device audio. Which player has it comes from the server queues. */
    data object Active : DeviceAudioShareState
}

/** Why a share ended without the user. */
enum class DeviceAudioShareEnd(val message: StringResource) {
    CaptureRevoked(Res.string.device_audio_end_revoked),
    ServerRefused(Res.string.device_audio_end_refused),
    ServerStopped(Res.string.device_audio_end_server_stopped),

    /** The server stopped the stream after the source reported no signal: its line-sense autostop. */
    Silence(Res.string.device_audio_end_silence),
    ConnectionLost(Res.string.device_audio_end_connection_lost),
    Timeout(Res.string.device_audio_end_timeout),

    /**
     * The system did not accept the capture consent, so no capture started. It reuses the
     * [Timeout] text on purpose: "did not start, try again" is what the user should do, and
     * the cause (a stale consent token) means nothing to them.
     */
    CaptureFailed(Res.string.device_audio_end_timeout),

    /** The local player is not paired with the server, which grants the source only to a paired one. */
    Unpaired(Res.string.device_audio_end_unpaired),
}

/**
 * Plays the device audio on a player, and owns the life of a share.
 *
 * A share runs in the app [scope]. The local player offers the source role while the platform
 * capture runs ([DeviceAudioSource.config]). Only the server starts the stream, so the router
 * waits until the server accepts the source and then sends an ordinary play request for the
 * source uri through [play]. A share ends when the user stops it, when the system ends the
 * capture, when the server stops the stream (also after a minute of silence, with the
 * `line_sense` of [SourceDialect.RC1]), or when the audio link drops: a start command
 * does not survive a reconnect. The end of a share stops the player that played it.
 */
class DeviceAudioRouter(
    private val share: DeviceAudioShare,
    private val source: DeviceAudioSource,
    /** The state of the local player. */
    private val playerState: StateFlow<PlayerState>,
    endpoint: StateFlow<Endpoint?>,
    /**
     * Whether the server finds the source by its uri: null if it cannot tell. The server lists a
     * client as a source some time after it grants the role, and rejects a play request before.
     */
    private val isListed: suspend (uri: String) -> Boolean?,
    /** `PlayDispatcher.play`. */
    private val play: (PlayRequest) -> Unit,
    /**
     * Takes the device audio off a player: its own queue becomes its source again. With [stop]
     * the player also stops first; without it the server stopped the player already.
     */
    private val releasePlayer: (playerId: String, stop: Boolean) -> Unit,
    private val errorBus: ErrorMessageBus,
    private val scope: CoroutineScope,
    /** The text of an end cause; a test has no resources. */
    private val describe: suspend (StringResource) -> String = { getString(it) },
) {
    private val log = Logger.withTag("DeviceAudioRouter")
    private val _state = MutableStateFlow<DeviceAudioShareState>(DeviceAudioShareState.Idle)
    val state: StateFlow<DeviceAudioShareState> = _state
    private var job: Job? = null

    /**
     * Guards [_state], [job] and [playedQueueId]. A share can end from the user, the capture
     * collector and the share job at once, on several threads; only the first end acts.
     */
    private val lock = SynchronizedObject()

    /** The queue that got the play request of this share; null before the request and after the end. */
    private var playedQueueId: String? = null

    /**
     * True when a share can start: the platform captures playback, the server lists Sendspin
     * sources, and the local player is connected on the wire that has the source role.
     */
    val isAvailable: StateFlow<Boolean> = combine(source.instanceId, playerState, endpoint) { instance, player, link ->
        share.isSupported && instance != null && player is PlayerState.Connected && link.hasSourceRole()
    }.stateIn(scope, SharingStarted.Eagerly, false)

    /** The uri of this device as a live input, while the local player is connected. */
    val sourceUri: StateFlow<String?> = combine(source.instanceId, playerState, ::sourceUriOf)
        .stateIn(scope, SharingStarted.Eagerly, null)

    private fun sourceUriOf(instanceId: String?, player: PlayerState): String? =
        instanceId?.let { id -> (player as? PlayerState.Connected)?.let { sendspinSourceUri(id, it.playerId) } }

    private val sourceStatus = playerState.map { (it as? PlayerState.Connected)?.source ?: SourceStatus.Off }

    init {
        // The capture can end outside the app: the notification, the system UI, another capture.
        scope.launch {
            share.capture.filter { it == null }.collect {
                if (_state.value != DeviceAudioShareState.Idle) end(captureEndCause())
            }
        }
        // A rejected consent leaves the capture null all along: nothing else tells.
        scope.launch {
            share.startFailures.collect {
                if (_state.value != DeviceAudioShareState.Idle) end(DeviceAudioShareEnd.CaptureFailed)
            }
        }
    }

    /** The cause once the capture is gone: none for a stop by the user. */
    private fun captureEndCause(): DeviceAudioShareEnd? = DeviceAudioShareEnd.CaptureRevoked.takeIf { share.endedBySystem }

    /**
     * Plays the device audio on [queueId]. Call it after the platform capture started. A share in
     * progress moves to the new queue.
     */
    fun route(queueId: String) {
        synchronized(lock) { start(queueId) }
    }

    private fun start(queueId: String) {
        job?.cancel()
        _state.value = DeviceAudioShareState.Starting(queueId)
        job = scope.launch {
            val uri = awaitAccepted() ?: return@launch
            if (!awaitListed(uri)) return@launch end(DeviceAudioShareEnd.Timeout)
            log.i { "The server accepted the source. Playing it on $queueId" }
            // An end in between cancels this job: then no request goes out that nothing releases.
            synchronized(lock) {
                ensureActive()
                play(PlayRequest(queueOrPlayerId = queueId, media = listOf(uri), option = QueueOption.REPLACE))
                playedQueueId = queueId
            }
            withTimeoutOrNull(STREAM_WAIT_MS) { sourceStatus.first { it == SourceStatus.Streaming } }
                ?: return@launch end(DeviceAudioShareEnd.Timeout)
            synchronized(lock) {
                ensureActive()
                _state.value = DeviceAudioShareState.Active
            }
            sourceStatus.first { it != SourceStatus.Streaming }
            end(streamEndCause())
        }
    }

    /**
     * Why the stream stopped. A capture that ended stops the stream too, and its cause wins: the
     * capture clears before the stream reports the end.
     */
    private fun streamEndCause(): DeviceAudioShareEnd? = when {
        share.capture.value == null -> captureEndCause()
        else -> serverEndCause(playerState.value)
    }

    /** The user ends the share. */
    fun stop() = end(cause = null)

    /**
     * Waits until the server accepts the source and returns its uri. A server that refuses the
     * dialect in effect gets the other one, which restarts the audio link one time. Ends the
     * share and returns null when both fail.
     */
    private suspend fun awaitAccepted(): String? {
        val first = source.dialect.value
        for (dialect in listOf(first) + SourceDialect.entries.filter { it != first }) {
            source.dialect.value = dialect
            // A refusal from before the restart is still the current value: skip it.
            val status = withTimeoutOrNull(ACCEPT_WAIT_MS) {
                sourceStatus.dropWhile { it == SourceStatus.Refused }
                    .first { it != SourceStatus.Off }
            } ?: return endAndNull(DeviceAudioShareEnd.Timeout)
            if (status == SourceStatus.Unpaired) return endAndNull(DeviceAudioShareEnd.Unpaired)
            if (status != SourceStatus.Refused) {
                // Not [sourceUri]: that flow gets the player state one dispatch later.
                return sourceUriOf(source.instanceId.value, playerState.value)
                    ?: endAndNull(DeviceAudioShareEnd.ConnectionLost)
            }
            log.i { "The server refused the source role as $dialect" }
        }
        source.dialect.value = SourceDialect.SPEC
        return endAndNull(DeviceAudioShareEnd.ServerRefused)
    }

    /** False if the server does not list the source in time. */
    private suspend fun awaitListed(uri: String): Boolean {
        var checks = 0
        return withTimeoutOrNull(LISTED_WAIT_MS) {
            while (isListed(uri) == false) {
                checks++
                delay(LISTED_POLL_MS)
            }
            if (checks > 0) log.i { "The server listed the source after $checks checks" }
            true
        } ?: false.also { log.w { "The server does not list $uri" } }
    }

    /** The signal keeps its value after the stream ends, so it tells a silence stop apart. */
    private fun serverEndCause(player: PlayerState): DeviceAudioShareEnd = when {
        player !is PlayerState.Connected -> DeviceAudioShareEnd.ConnectionLost
        player.sourceSignal == SourceSignal.Absent -> DeviceAudioShareEnd.Silence
        else -> DeviceAudioShareEnd.ServerStopped
    }

    private fun endAndNull(cause: DeviceAudioShareEnd): String? {
        end(cause)
        return null
    }

    /**
     * Ends the share once. The lock is reentrant, and [Job.cancel] does not wait for the job, so
     * the share job can end itself here.
     */
    private fun end(cause: DeviceAudioShareEnd?) {
        synchronized(lock) { endLocked(cause) }
    }

    private fun endLocked(cause: DeviceAudioShareEnd?) {
        if (_state.value == DeviceAudioShareState.Idle && share.capture.value == null) return
        log.i { "Share ended: ${cause ?: "stopped by the user"}" }
        _state.value = DeviceAudioShareState.Idle
        // The server waits a minute for a source that went away, and the player gives silence
        // for that time. So the player stops with the share. The device also stays the source of
        // the player after a stop, and the player then shows no queue: give the queue back.
        val played = playedQueueId
        playedQueueId = null
        val serverEnded = cause == DeviceAudioShareEnd.ServerStopped || cause == DeviceAudioShareEnd.Silence
        played?.let { releasePlayer(it, !serverEnded) }
        share.stop()
        cause?.let { scope.launch { errorBus.emit(describe(it.message)) } }
        job?.cancel()
        job = null
    }

    private fun Endpoint?.hasSourceRole(): Boolean = when (this) {
        is Endpoint.WebSocket -> wire == WireVersion.SPEC_1_0
        is Endpoint.WebRtc -> wire == WireVersion.SPEC_1_0
        is Endpoint.Unsupported, null -> false
    }

    private companion object {
        /** A restart of the audio link and the clock sync fit in this with a wide margin. */
        const val ACCEPT_WAIT_MS = 15_000L

        /** The server lists a source a moment after its first state report. */
        const val LISTED_WAIT_MS = 5_000L
        const val LISTED_POLL_MS = 250L

        /** The server gives a source 5 s to send audio after its start command. */
        const val STREAM_WAIT_MS = 10_000L
    }
}
