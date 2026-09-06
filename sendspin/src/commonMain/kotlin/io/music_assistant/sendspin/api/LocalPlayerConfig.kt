package io.music_assistant.sendspin.api

import io.music_assistant.sendspin.wire.AudioCodec
import kotlinx.coroutines.channels.ReceiveChannel

/**
 * Everything the player needs from the app. Supplied as `StateFlow<LocalPlayerConfig?>`;
 * `null` disables the player and is the only "stop".
 *
 * A change to [endpoint], [deviceName], or [codecPreference] restarts the
 * connection. The audio pipeline is untouched. [userDelayMs] and
 * [bufferCapacityBytes] apply live.
 */
data class LocalPlayerConfig(
    val endpoint: Endpoint,
    val deviceName: String,
    /** Ordered preference; formats are advertised only for codecs the [DecoderFactory] supports. */
    val codecPreference: List<AudioCodec>,
    /** Advertised in `client/hello` as `buffer_capacity` and used as the local byte cap. */
    val bufferCapacityBytes: Int,
    /** Manual playback lag, added to every chunk's presentation time. */
    val userDelayMs: Int,
)

sealed interface Endpoint {
    /** Dedicated WebSocket through the MA proxy. Sends `auth` with [authToken] before `client/init`. */
    data class WebSocket(val url: String, val authToken: String) : Endpoint

    /**
     * WebRTC data channel. Every connection attempt calls [openChannel]; the app
     * renegotiates inside it when needed. A channel is single-use.
     */
    class WebRtc(val openChannel: suspend () -> SendspinTransport) : Endpoint
}

/**
 * Frames in and out over ONE connection. No reconnect, no epochs: when the
 * connection ends, [inbound] closes (with a cause on failure) and the transport
 * is discarded.
 */
interface SendspinTransport {
    val inbound: ReceiveChannel<Frame>

    /** Throws when the connection is gone. */
    suspend fun send(frame: Frame)

    suspend fun close()
}

sealed interface Frame {
    data class Text(val text: String) : Frame
    class Binary(val bytes: ByteArray) : Frame
}
