package io.music_assistant.client.webrtc

import co.touchlab.kermit.Logger
import io.ktor.client.webrtc.DataChannelEvent
import io.ktor.client.webrtc.WebRtc
import io.ktor.client.webrtc.WebRtcDataChannel
import io.ktor.utils.io.ExperimentalKtorApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * WebRTC data channel wrapper backed by `io.ktor:ktor-client-webrtc`. Text and
 * binary share one ordered, unbounded [inbound] stream (single collector per
 * instance); a drop policy would strand the consumer's protocol state machine.
 * State propagation is event-driven — a poll-based version caused a ~50 ms lag
 * that broke the auth handshake on first connect; do not reintroduce.
 */
@OptIn(ExperimentalKtorApi::class)
class DataChannelWrapper internal constructor(
    private val dataChannel: WebRtcDataChannel?,
    connectionEvents: SharedFlow<DataChannelEvent>?,
    receiveSource: DataChannelReceiveSource,
    initialState: DataChannelState,
    val label: String,
) {
    constructor(
        dataChannel: WebRtcDataChannel,
        connectionEvents: SharedFlow<DataChannelEvent>,
    ) : this(
        dataChannel = dataChannel,
        connectionEvents = connectionEvents,
        receiveSource = KtorDataChannelReceiveSource(dataChannel),
        initialState = dataChannel.state.toCommon(),
        label = dataChannel.label,
    )

    private val logger = Logger.withTag("DataChannelWrapper")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // close() is called exactly once per channel from WebRTCConnectionManager's
    // single-threaded cleanup path; an atomic guard would be defensive overkill.
    // Worst-case races (send() after close()) surface as a logged Ktor exception.
    private var closed = false

    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<DataChannelState> = _state.asStateFlow()

    private val inboundChannel = Channel<DataChannelInbound>(Channel.UNLIMITED)

    /** The single ordered inbound stream (one collector only). */
    val inbound: Flow<DataChannelInbound> = inboundChannel.receiveAsFlow()

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

    private val drainJob: Job

    init {
        // Drain outgoing messages — exits naturally when `outgoing` is closed by close().
        drainJob = scope.launch {
            for (msg in outgoing) {
                runCatchingNonCancellation("send failed on channel $label") {
                    when (msg) {
                        is Outgoing.Text -> dataChannel?.send(msg.data)
                        is Outgoing.Binary -> dataChannel?.send(msg.data)
                    }
                }
            }
        }

        // Receive loop — the only producer of `inbound`, so source order is preserved
        // by construction; closed on exit so a collector doesn't hang on a dead feed.
        scope.launch {
            try {
                runCatchingNonCancellation("receive loop failed on channel $label") {
                    while (true) {
                        inboundChannel.send(receiveSource.receive())
                    }
                }
            } finally {
                inboundChannel.close()
            }
        }

        // State propagation — filtered by channel identity so siblings on the same
        // peer connection don't bleed into our state.
        if (connectionEvents != null && dataChannel != null) {
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
        // Close the outgoing Channel, then give the drain a bounded chance to
        // flush queued sends before the scope is cancelled out from under it.
        // The state event collector won't deliver the resulting Closed event
        // since the scope is gone, so push it manually.
        outgoing.close()
        withTimeoutOrNull(CLOSE_FLUSH_TIMEOUT_MILLIS) { drainJob.join() }
        scope.cancel()
        dataChannel?.close()
        _state.update { DataChannelState.Closed }
    }
}

private const val CLOSE_FLUSH_TIMEOUT_MILLIS = 500L

/** Production receive source: pulls from the Ktor WebRTC channel. */
@OptIn(ExperimentalKtorApi::class)
private class KtorDataChannelReceiveSource(
    private val dataChannel: WebRtcDataChannel,
) : DataChannelReceiveSource {
    override suspend fun receive(): DataChannelInbound =
        when (val msg = dataChannel.receive()) {
            is WebRtc.DataChannel.Message.Text -> DataChannelInbound.Text(msg.data)
            is WebRtc.DataChannel.Message.Binary -> DataChannelInbound.Binary(msg.data)
        }
}

@OptIn(ExperimentalKtorApi::class)
private fun WebRtc.DataChannel.State.toCommon(): DataChannelState = when (this) {
    WebRtc.DataChannel.State.CONNECTING -> DataChannelState.Connecting
    WebRtc.DataChannel.State.OPEN -> DataChannelState.Open
    WebRtc.DataChannel.State.CLOSING -> DataChannelState.Closing
    WebRtc.DataChannel.State.CLOSED -> DataChannelState.Closed
}
