package io.music_assistant.client.webrtc

import io.music_assistant.client.utils.monotonicMicros

/** One inbound data-channel message, preserving the text/binary distinction. */
sealed interface DataChannelInbound {
    class Text(val text: String) : DataChannelInbound

    /** [receivedAtMicros] is [monotonicMicros] when the channel delivered the message, before any queue. */
    class Binary(val bytes: ByteArray, val receivedAtMicros: Long = monotonicMicros()) : DataChannelInbound
}

/**
 * The receive half of a data channel, as consumed by [DataChannelWrapper]'s
 * single receive loop. Production wraps the Ktor WebRTC channel; tests inject
 * a fake to drive the wrapper's ordered inbound stream deterministically.
 */
interface DataChannelReceiveSource {
    /** Suspends for the next message; throws when the channel is closed. */
    suspend fun receive(): DataChannelInbound
}
