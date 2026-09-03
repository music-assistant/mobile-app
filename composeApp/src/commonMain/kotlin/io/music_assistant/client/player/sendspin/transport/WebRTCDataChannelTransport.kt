package io.music_assistant.client.player.sendspin.transport

import co.touchlab.kermit.Logger
import io.music_assistant.client.webrtc.DataChannelInbound
import io.music_assistant.client.webrtc.DataChannelState
import io.music_assistant.client.webrtc.DataChannelWrapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * [SendspinTransport] over an existing [DataChannelWrapper]. No auto-reconnect:
 * only epoch 1 is ever emitted; a dead channel requires a fresh peer negotiation.
 */
class WebRTCDataChannelTransport(
    private val dataChannelWrapper: DataChannelWrapper,
) : SendspinTransport {
    private val logger = Logger.withTag("WebRTCDataChannelTransport")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Lossless ordered event stream; single collector (the protocol session).
    private val eventsChannel = Channel<InboundTransportEvent>(Channel.UNLIMITED)
    override val events: Flow<InboundTransportEvent> = eventsChannel.receiveAsFlow()

    private var pumping = false

    init {
        logger.i { "Created WebRTC transport for channel: ${dataChannelWrapper.label}" }
    }

    override suspend fun connect() {
        val currentState = dataChannelWrapper.state.value
        logger.i { "Connect called, current state: $currentState" }

        if (currentState != DataChannelState.Open) {
            logger.d { "Waiting for channel to open..." }
            val openState = withTimeoutOrNull(10_000) {
                dataChannelWrapper.state.first { it == DataChannelState.Open }
            }
            if (openState == null) {
                val error =
                    "WebRTC data channel did not open within timeout " +
                        "(current state: ${dataChannelWrapper.state.value})"
                logger.e { error }
                error(error)
            }
        }

        logger.i { "Channel open — starting inbound pump" }
        check(!pumping) { "WebRTC transport is single-use" }
        pumping = true

        // Epoch begins before any frame of the epoch.
        eventsChannel.trySend(InboundTransportEvent.Connected(EPOCH, isReconnect = false))
        scope.launch {
            try {
                dataChannelWrapper.inbound.collect { msg ->
                    val event = when (msg) {
                        is DataChannelInbound.Text -> InboundTransportEvent.Text(EPOCH, msg.text)
                        is DataChannelInbound.Binary -> InboundTransportEvent.Binary(EPOCH, msg.bytes)
                    }
                    eventsChannel.send(event)
                }
            } finally {
                // A normal transport disconnect leaves the wrapper open because the channel is
                // owned by WebRTCConnectionManager. A channel that is already closing/closed is
                // terminal for this single-use Sendspin transport and needs a fresh negotiation.
                val state = dataChannelWrapper.state.value
                if (state == DataChannelState.Open) {
                    // Single producer: the closed signal follows every buffered frame instead of
                    // racing past them on a separate coroutine.
                    eventsChannel.trySend(InboundTransportEvent.Disconnected(EPOCH))
                } else {
                    eventsChannel.trySend(
                        InboundTransportEvent.Error(
                            epoch = EPOCH,
                            cause = IllegalStateException(
                                "WebRTC Sendspin data channel closed (state: $state)",
                            ),
                            permanent = true,
                        ),
                    )
                }
            }
        }
    }

    override suspend fun sendText(message: String) {
        val currentState = dataChannelWrapper.state.value
        if (currentState != DataChannelState.Open) {
            // Closing can race with state reporting and queued protocol sends. The wrapper is
            // best-effort after close, so dropping this frame avoids turning normal teardown
            // into an uncaught coroutine exception. Disconnect the transport as well so the
            // protocol session observes the dead channel promptly.
            logger.w { "Dropping text send while channel is not open (state: $currentState)" }
            disconnect()
            return
        }

        dataChannelWrapper.send(message)
    }

    override suspend fun sendBinary(data: ByteArray) {
        val currentState = dataChannelWrapper.state.value
        if (currentState != DataChannelState.Open) {
            // Closing can race with state reporting and queued protocol sends. The wrapper is
            // best-effort after close, so dropping this frame avoids turning normal teardown
            // into an uncaught coroutine exception. Disconnect the transport as well so the
            // protocol session observes the dead channel promptly.
            logger.w { "Dropping binary send while channel is not open (state: $currentState)" }
            disconnect()
            return
        }

        dataChannelWrapper.sendBinary(data)
    }

    /** Stops the inbound pumps; the channel itself stays owned by WebRTCConnectionManager. */
    override suspend fun disconnect() {
        logger.i { "Disconnecting WebRTC transport (channel ownership stays with the manager)" }
        scope.cancel()
    }

    override fun close() {
        scope.cancel()
    }

    private companion object {
        const val EPOCH = 1
    }
}
