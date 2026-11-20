package io.music_assistant.client.data.share

import co.touchlab.kermit.Logger
import io.music_assistant.client.api.ErrorMessageBus
import io.music_assistant.client.data.model.client.QueueOption
import io.music_assistant.client.data.model.server.sendspinSourceUri
import io.music_assistant.client.data.play.PlayRequest
import io.music_assistant.sendspin.api.Endpoint
import io.music_assistant.sendspin.api.PlayerState
import io.music_assistant.sendspin.api.SourceDialect
import io.music_assistant.sendspin.api.SourceStatus
import io.music_assistant.sendspin.api.WireVersion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
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
import musicassistantclient.composeapp.generated.resources.phone_audio_end_connection_lost
import musicassistantclient.composeapp.generated.resources.phone_audio_end_refused
import musicassistantclient.composeapp.generated.resources.phone_audio_end_revoked
import musicassistantclient.composeapp.generated.resources.phone_audio_end_server_stopped
import musicassistantclient.composeapp.generated.resources.phone_audio_end_timeout
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString

sealed interface PhoneAudioShareState {
    data object Idle : PhoneAudioShareState

    /** The share starts: the server has not played the phone audio on [queueId] yet. */
    data class Starting(val queueId: String) : PhoneAudioShareState

    /** The server plays the phone audio. Which player has it comes from the server queues. */
    data object Active : PhoneAudioShareState
}

/** Why a share ended without the user. */
enum class PhoneAudioShareEnd(val message: StringResource) {
    CaptureRevoked(Res.string.phone_audio_end_revoked),
    ServerRefused(Res.string.phone_audio_end_refused),
    ServerStopped(Res.string.phone_audio_end_server_stopped),
    ConnectionLost(Res.string.phone_audio_end_connection_lost),
    Timeout(Res.string.phone_audio_end_timeout),
}

/**
 * Plays the phone audio on a player, and owns the life of a share.
 *
 * A share runs in the app [scope]. The local player offers the source role while the platform
 * capture runs ([PhoneAudioSource.config]). Only the server starts the stream, so the router
 * waits until the server accepts the source and then sends an ordinary play request for the
 * source uri through [play]. A share ends when the user stops it, when the system ends the
 * capture, when the server stops the stream, or when the audio link drops: a start command
 * does not survive a reconnect. The end of a share stops the player that played it.
 */
class PhoneAudioRouter(
    private val share: PhoneAudioShare,
    private val source: PhoneAudioSource,
    /** The state of the local player. */
    private val playerState: StateFlow<PlayerState>,
    endpoint: StateFlow<Endpoint?>,
    /** `PlayDispatcher.play`. */
    private val play: (PlayRequest) -> Unit,
    /** Sends a stop command to a player. */
    private val stopPlayer: (playerId: String) -> Unit,
    private val errorBus: ErrorMessageBus,
    private val scope: CoroutineScope,
    /** The text of an end cause; a test has no resources. */
    private val describe: suspend (StringResource) -> String = { getString(it) },
) {
    private val log = Logger.withTag("PhoneAudioRouter")
    private val _state = MutableStateFlow<PhoneAudioShareState>(PhoneAudioShareState.Idle)
    val state: StateFlow<PhoneAudioShareState> = _state
    private var job: Job? = null

    /** The queue that got the play request of this share; null before the request and after the end. */
    private var playedQueueId: String? = null

    /**
     * True when a share can start: the platform captures playback, the server lists Sendspin
     * sources, and the local player is connected on the wire that has the source role.
     */
    val isAvailable: StateFlow<Boolean> = combine(source.instanceId, playerState, endpoint) { instance, player, link ->
        share.isSupported && instance != null && player is PlayerState.Connected && link.hasSourceRole()
    }.stateIn(scope, SharingStarted.Eagerly, false)

    /** The uri of this phone as a live input, while the local player is connected. */
    val sourceUri: StateFlow<String?> = combine(source.instanceId, playerState, ::sourceUriOf)
        .stateIn(scope, SharingStarted.Eagerly, null)

    private fun sourceUriOf(instanceId: String?, player: PlayerState): String? =
        instanceId?.let { id -> (player as? PlayerState.Connected)?.let { sendspinSourceUri(id, it.playerId) } }

    private val sourceStatus = playerState.map { (it as? PlayerState.Connected)?.source ?: SourceStatus.Off }

    init {
        // The capture can end outside the app: the notification, the system UI, another capture.
        scope.launch {
            share.capture.filter { it == null }.collect {
                if (_state.value != PhoneAudioShareState.Idle) {
                    end(PhoneAudioShareEnd.CaptureRevoked.takeIf { share.endedBySystem })
                }
            }
        }
    }

    /**
     * Plays the phone audio on [queueId]. Call it after the platform capture started. A share in
     * progress moves to the new queue.
     */
    fun route(queueId: String) {
        job?.cancel()
        _state.value = PhoneAudioShareState.Starting(queueId)
        job = scope.launch {
            val uri = awaitAccepted() ?: return@launch
            play(PlayRequest(queueOrPlayerId = queueId, media = listOf(uri), option = QueueOption.REPLACE))
            playedQueueId = queueId
            withTimeoutOrNull(STREAM_WAIT_MS) { sourceStatus.first { it == SourceStatus.Streaming } }
                ?: return@launch end(PhoneAudioShareEnd.Timeout)
            _state.value = PhoneAudioShareState.Active
            sourceStatus.first { it != SourceStatus.Streaming }
            val connected = playerState.value is PlayerState.Connected
            end(if (connected) PhoneAudioShareEnd.ServerStopped else PhoneAudioShareEnd.ConnectionLost)
        }
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
            } ?: return endAndNull(PhoneAudioShareEnd.Timeout)
            if (status != SourceStatus.Refused) {
                // Not [sourceUri]: that flow gets the player state one dispatch later.
                return sourceUriOf(source.instanceId.value, playerState.value)
                    ?: endAndNull(PhoneAudioShareEnd.ConnectionLost)
            }
            log.i { "The server refused the source role as $dialect" }
        }
        source.dialect.value = SourceDialect.SPEC
        return endAndNull(PhoneAudioShareEnd.ServerRefused)
    }

    private fun endAndNull(cause: PhoneAudioShareEnd): String? {
        end(cause)
        return null
    }

    private fun end(cause: PhoneAudioShareEnd?) {
        if (_state.value == PhoneAudioShareState.Idle && share.capture.value == null) return
        log.i { "Share ended: ${cause ?: "stopped by the user"}" }
        _state.value = PhoneAudioShareState.Idle
        // The server waits a minute for a source that went away, and the player gives silence
        // for that time. So the player stops with the share, but not if the server stopped it.
        playedQueueId?.takeIf { cause != PhoneAudioShareEnd.ServerStopped }?.let(stopPlayer)
        playedQueueId = null
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

        /** The server gives a source 5 s to send audio after its start command. */
        const val STREAM_WAIT_MS = 10_000L
    }
}
