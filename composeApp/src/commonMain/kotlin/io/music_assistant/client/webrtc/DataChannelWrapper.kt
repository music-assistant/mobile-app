package io.music_assistant.client.webrtc

import co.touchlab.kermit.Logger
import io.ktor.client.webrtc.DataChannelEvent
import io.ktor.client.webrtc.WebRtc
import io.ktor.client.webrtc.WebRtcDataChannel
import io.ktor.utils.io.ExperimentalKtorApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * WebRTC data channel wrapper backed by `io.ktor:ktor-client-webrtc`.
 *
 * Internals:
 *  - Outgoing messages flow through an unbounded Channel + drain coroutine because
 *    Ktor's `send` is suspending while our public API is not.
 *  - Incoming messages are pulled via `dataChannel.receive()` in a launched loop and
 *    discriminated as [WebRtc.DataChannel.Message.Text] / [WebRtc.DataChannel.Message.Binary]
 *    — no first-byte sniffing.
 *  - State propagation is event-driven via the parent connection's [DataChannelEvent]
 *    flow, filtered by channel identity. A poll-based version of this caused a ~50 ms
 *    detection lag that confused the auth handshake on first connect; do not reintroduce.
 */
@OptIn(ExperimentalKtorApi::class)
class DataChannelWrapper(
    private val dataChannel: WebRtcDataChannel,
    private val connectionEvents: SharedFlow<DataChannelEvent>,
) {
    private val logger = Logger.withTag("DataChannelWrapper")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // close() is called exactly once per channel from WebRTCConnectionManager's
    // single-threaded cleanup path; an atomic guard would be defensive overkill.
    // Worst-case races (send() after close()) surface as a logged Ktor exception.
    private var closed = false

    val label: String
        get() = dataChannel.label

    private val _state = MutableStateFlow(dataChannel.state.toCommon())
    val state: StateFlow<DataChannelState> = _state.asStateFlow()

    // Shared `ma-api` channel carries both control-plane RPCs and http-proxy responses.
    // Buffer sized to absorb image-burst responses without dropping control frames.
    private val _textMessages = MutableSharedFlow<String>(extraBufferCapacity = 200)
    val messages: Flow<String> = _textMessages.asSharedFlow()

    // Binary messages (audio chunks on `sendspin`) arrive at ~50–100/sec. Large buffer
    // prevents backpressure stalling the receive coroutine during consumer lag.
    private val _binaryMessages = MutableSharedFlow<ByteArray>(extraBufferCapacity = 2000)
    val binaryMessages: Flow<ByteArray> = _binaryMessages.asSharedFlow()

    private sealed interface Outgoing {
        data class Text(val data: String) : Outgoing
        data class Binary(val data: ByteArray) : Outgoing {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (other == null || this::class != other::class) return false
                other as Binary
                if (!data.contentEquals(other.data)) return false
                return true
            }

            override fun hashCode(): Int {
                return data.contentHashCode()
            }
        }
    }

    private val outgoing = Channel<Outgoing>(capacity = Channel.UNLIMITED)

    init {
        // Drain outgoing messages — exits naturally when `outgoing` is closed by close().
        scope.launch {
            for (msg in outgoing) {
                runCatchingNonCancellation("send failed on channel $label") {
                    when (msg) {
                        is Outgoing.Text -> dataChannel.send(msg.data)
                        is Outgoing.Binary -> dataChannel.send(msg.data)
                    }
                }
            }
        }

        // Receive loop — exits when `dataChannel.receive()` throws (channel closed) or
        // when scope is cancelled.
        scope.launch {
            runCatchingNonCancellation("receive loop failed on channel $label") {
                while (true) {
                    when (val msg = dataChannel.receive()) {
                        is WebRtc.DataChannel.Message.Text ->
                            if (!_textMessages.tryEmit(msg.data)) {
                                logger.w { "Text message buffer full on $label, dropping" }
                            }
                        is WebRtc.DataChannel.Message.Binary ->
                            if (!_binaryMessages.tryEmit(msg.data)) {
                                logger.w { "Binary message buffer full on $label, dropping chunk" }
                            }
                    }
                }
            }
        }

        // State propagation — filtered by channel identity so siblings on the same
        // peer connection don't bleed into our state.
        scope.launch {
            runCatchingNonCancellation("state event collector failed on channel $label") {
                connectionEvents.collect { event ->
                    if (event.channel !== dataChannel) return@collect
                    val mapped = when (event) {
                        is DataChannelEvent.Open -> DataChannelState.Open
                        is DataChannelEvent.Closing -> DataChannelState.Closing
                        is DataChannelEvent.Closed -> DataChannelState.Closed
                        is DataChannelEvent.Error -> {
                            logger.e { "Data channel $label error: ${event.reason}" }
                            DataChannelState.Closed
                        }
                        is DataChannelEvent.BufferedAmountLow -> return@collect
                    }
                    _state.update { mapped }
                }
            }
        }
    }

    private inline fun runCatchingNonCancellation(failureMessage: String, block: () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            logger.e(e) { failureMessage }
        }
    }

    fun send(message: String) {
        if (closed) {
            logger.w { "send() on closed channel $label" }
            return
        }
        if (outgoing.trySend(Outgoing.Text(message)).isFailure) {
            logger.e { "outgoing channel rejected text frame on $label" }
        }
    }

    fun sendBinary(data: ByteArray) {
        if (closed) {
            logger.w { "sendBinary() on closed channel $label" }
            return
        }
        if (outgoing.trySend(Outgoing.Binary(data)).isFailure) {
            logger.e { "outgoing channel rejected binary frame on $label" }
        }
    }

    suspend fun close() {
        if (closed) return
        closed = true
        logger.i { "Closing data channel $label" }
        // Close the outgoing Channel first so the drain coroutine exits naturally after
        // flushing any queued sends. Then cancel scope (cancels receive + state collector)
        // and close the underlying Ktor channel. The state event collector won't deliver
        // the resulting Closed event since scope is gone, so push it manually.
        outgoing.close()
        scope.cancel()
        dataChannel.close()
        _state.update { DataChannelState.Closed }
    }
}

private fun WebRtc.DataChannel.State.toCommon(): DataChannelState = when (this) {
    WebRtc.DataChannel.State.CONNECTING -> DataChannelState.Connecting
    WebRtc.DataChannel.State.OPEN -> DataChannelState.Open
    WebRtc.DataChannel.State.CLOSING -> DataChannelState.Closing
    WebRtc.DataChannel.State.CLOSED -> DataChannelState.Closed
}
