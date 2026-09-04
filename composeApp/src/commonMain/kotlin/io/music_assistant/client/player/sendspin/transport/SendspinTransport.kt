package io.music_assistant.client.player.sendspin.transport

import kotlinx.coroutines.flow.Flow

/**
 * One inbound occurrence on a Sendspin transport, in exact source order.
 *
 * Every event carries the connection **epoch**: a counter incremented on each
 * (re)connection. An epoch's [Connected] event is published before any frame
 * of that epoch, so a consumer can drop frames whose epoch is not current —
 * a stale listener may still emit after a new epoch begins because listener
 * jobs are cancelled asynchronously.
 */
sealed interface InboundTransportEvent {
    val epoch: Int

    /** A connection epoch began. Published before any frame of the epoch. */
    data class Connected(override val epoch: Int, val isReconnect: Boolean) :
        InboundTransportEvent

    data class Text(override val epoch: Int, val text: String) : InboundTransportEvent

    class Binary(override val epoch: Int, val bytes: ByteArray) : InboundTransportEvent

    /** An automatic reconnect attempt is in progress for the next epoch. */
    data class Reconnecting(override val epoch: Int, val attempt: Int) : InboundTransportEvent

    data class Disconnected(override val epoch: Int) : InboundTransportEvent

    /**
     * The transport failed. [permanent] is true when the transport has given
     * up (e.g. reconnect attempts exhausted) and will not recover on its own.
     */
    data class Error(
        override val epoch: Int,
        val cause: Throwable,
        val permanent: Boolean,
    ) : InboundTransportEvent
}

/**
 * Transport abstraction for the Sendspin protocol, usable over WebSocket or a
 * WebRTC data channel.
 *
 * Inbound delivery contract:
 * - [events] is a **single-collector** stream: exactly one consumer (the
 *   protocol session) may collect it. This is required by convention, not
 *   enforced — a second collector would silently split the stream.
 * - Delivery is lossless and source-ordered — text and binary frames share
 *   the one stream, and no event is ever dropped under backpressure. A
 *   dropped control frame would strand the Noise handshake or activation
 *   state machine, so implementations must buffer, never drop.
 * - Subscribe before calling [connect]; a `Connected` emitted synchronously
 *   during [connect] must not be lost.
 */
interface SendspinTransport {
    /** The single ordered inbound event stream. */
    val events: Flow<InboundTransportEvent>

    /**
     * True when this transport opens exactly one epoch: any [InboundTransportEvent.Disconnected]
     * is terminal and recovery needs a fresh transport instance. False when the transport
     * reconnects itself and a disconnect may be followed by another `Connected`.
     */
    val isSingleUse: Boolean

    suspend fun connect()

    suspend fun sendText(message: String)

    suspend fun sendBinary(data: ByteArray)

    suspend fun disconnect()

    fun close()
}
