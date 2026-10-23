package io.music_assistant.sendspin.api

import kotlinx.coroutines.channels.ReceiveChannel

/**
 * Everything the player needs from the app. Supplied as `StateFlow<LocalPlayerConfig?>`;
 * `null` disables the player and is the only "stop".
 *
 * A change to [endpoint], [deviceName], [codecPreference], [bitDepth], or [bufferSeconds]
 * restarts the connection. The audio pipeline is untouched. [userDelayMs] applies live.
 */
data class LocalPlayerConfig(
    val endpoint: Endpoint,
    val deviceName: String,
    /** Ordered preference; formats are advertised only for codecs the [DecoderFactory] supports. */
    val codecPreference: List<AudioCodec>,
    /** 16 or 24: the depth offered for FLAC and PCM. Opus and the PCM fallback are always 16-bit. */
    val bitDepth: Int = 16,
    /**
     * Seconds of audio to buffer. `buffer_capacity` and the local byte cap are this
     * many seconds of the heaviest advertised format ([estimatedBytesPerSecond]).
     */
    val bufferSeconds: Int,
    /** Manual playback lag, added to every chunk's presentation time. */
    val userDelayMs: Int,
)

sealed interface Endpoint {
    /** The app has established that its server cannot provide this player's protocol. */
    data class Unsupported(val reason: String) : Endpoint

    /** Dedicated WebSocket through the MA proxy. Sends `auth` with [authToken] before `client/init`. */
    data class WebSocket(val url: String, val authToken: String, val wire: WireVersion) : Endpoint

    /**
     * WebRTC data channel. Every connection attempt calls [openChannel]; the app
     * renegotiates inside it when needed. A channel is single-use.
     */
    class WebRtc(val wire: WireVersion, val openChannel: suspend () -> SendspinTransport) : Endpoint
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
