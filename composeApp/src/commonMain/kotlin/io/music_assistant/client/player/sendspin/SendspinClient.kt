package io.music_assistant.client.player.sendspin

import co.touchlab.kermit.Logger
import com.sendspin.protocol.ClientState
import io.music_assistant.client.player.sendspin.audio.MediaPlayerAudioPlayer
import io.music_assistant.client.player.sendspin.model.GoodbyeReason
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import com.sendspin.protocol.SendSpinClient as LibraryClient

/**
 * Thin app-side adapter over the canonical [LibraryClient]. Preserves the exact seam that
 * [io.music_assistant.client.data.LocalPlayerController] binds to (`state`, `isStarved`,
 * `playbackStoppedDueToError`, `stopStream`, `stop`, `close`) while translating the library's
 * [ClientState] into the app's richer [SendspinState] hierarchy.
 *
 * Reconnection and buffering live entirely inside the library client; this adapter only maps state
 * and surfaces the sink errors published by [MediaPlayerAudioPlayer].
 */
class SendspinClient(
    private val libraryClient: LibraryClient,
    private val audioPlayer: MediaPlayerAudioPlayer,
) : CoroutineScope {
    private val logger = Logger.withTag("SendspinClient")
    private val supervisorJob = SupervisorJob()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Default + supervisorJob

    // Components
    private var transport: SendspinTransport? = null
    private var messageDispatcher: MessageDispatcher? = null
    private var stateReporter: StateReporter? = null

    // Unified state
    private val _state = MutableStateFlow<SendspinState>(SendspinState.Idle)
    val state: StateFlow<SendspinState> = _state.asStateFlow()

    private val _playbackStoppedDueToError = MutableStateFlow<Throwable?>(null)
    val playbackStoppedDueToError: StateFlow<Throwable?> = _playbackStoppedDueToError.asStateFlow()

    /** Buffer-starvation, straight from the consumer. Composed with transport state by the owner. */
    val isStarved: StateFlow<Boolean> get() = audioPlayer.isStarved

    /**
     * Fires each time audio actually starts flowing to the sink (stream/start, seek, track change).
     * The owner uses this — not the one-shot connection-level [SendspinState.Synchronized] — to
     * release its optimistic position freeze and confirm the local player's playing state.
     */
    val audioRendered: Flow<Unit> get() = audioPlayer.audioRendered

    // Tracks whether we've completed at least one handshake, so that a subsequent CONNECTING/
    // HANDSHAKING/DISCONNECTED reads as Reconnecting rather than a fresh connect. The owner ignores
    // Reconnecting's payload fields, so approximate values are fine.
    private var hasConnected = false
    private var wasStreaming = false
    private var stopped = false

    init {
        launch {
            combine(libraryClient.state, libraryClient.connectionExhausted) { s, exhausted -> s to exhausted }
                .collect { (s, exhausted) -> _state.value = mapState(s, exhausted) }
        }
        launch {
            // One-shot signal: emit the sink error, then reset (matches the prior 100 ms blip so the
            // owner's filterNotNull collector fires exactly once per error).
            audioPlayer.streamError.collect { error ->
                _playbackStoppedDueToError.value = error
                delay(ERROR_BLIP_MS)
                _playbackStoppedDueToError.value = null
            }
        }
    }

    private fun mapState(clientState: ClientState, exhausted: Boolean): SendspinState =
        when (clientState) {
            ClientState.IDLE -> SendspinState.Idle
            ClientState.CONNECTING ->
                if (hasConnected && !stopped) SendspinState.Reconnecting(wasStreaming, 0) else SendspinState.Connecting
            ClientState.HANDSHAKING ->
                if (hasConnected && !stopped) SendspinState.Reconnecting(wasStreaming, 0) else SendspinState.Handshaking
            ClientState.CLOCK_SYNCING -> {
                hasConnected = true
                wasStreaming = false
                SendspinState.Ready(libraryClient.serverId.value, libraryClient.serverName.value)
            }
            ClientState.STREAMING -> {
                hasConnected = true
                wasStreaming = true
                SendspinState.Synchronized
            }
            ClientState.ERROR -> SendspinState.Error(
                if (exhausted) {
                    SendspinError.Permanent(
                        cause = SendspinConnectionException("Sendspin connection failed (retries exhausted)"),
                        userAction = "Check network connection and server availability",
                    )
                } else {
                    // The library is auto-retrying — willRetry=true tells the owner not to double-retry.
                    SendspinError.Transient(
                        cause = SendspinConnectionException("Sendspin transport error"),
                        willRetry = true,
                    )
                },
            )
            ClientState.DISCONNECTED ->
                if (hasConnected && !stopped) SendspinState.Reconnecting(wasStreaming, 0) else SendspinState.Idle
        }

    /** Release the audio sink, leaving the client/transport intact. */
    suspend fun stopStream() {
        audioPlayer.stop()
    }

    /**
     * Send goodbye and disconnect (no reconnect). A [GoodbyeReason.Restart] is a warm disconnect:
     * the buffer + player keep draining so audio survives the reinit, and the factory reuses this
     * client on the next start. Other reasons stop audio.
     */
    suspend fun stop(reason: GoodbyeReason) {
        stopped = true
        logger.i { "Stopping Sendspin client (${reason.wire})" }
        libraryClient.disconnect(reason.wire, stopAudio = reason != GoodbyeReason.Restart)
    }

    /**
     * Detach this adapter's state collectors. The library client's lifecycle is owned by
     * [SendspinClientFactory] (reused on Restart, torn down via `destroyPipeline`), so we do NOT
     * close it here.
     */
    fun close() {
        logger.i { "Detaching Sendspin client adapter" }
        supervisorJob.cancel()
    }

    private companion object {
        const val ERROR_BLIP_MS = 100L
    }
}

/** Carrier for a Sendspin connection failure surfaced through [SendspinError]. */
class SendspinConnectionException(message: String) : Exception(message)
