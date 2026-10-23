package io.music_assistant.sendspin.api

import kotlinx.coroutines.channels.ReceiveChannel

/**
 * Everything the player needs from the app. Supplied as `StateFlow<LocalPlayerConfig?>`;
 * `null` disables the player and is the only "stop".
 *
 * A change to [endpoint], [deviceName], [codecPreference], [bitDepth], or [bufferSeconds]
 * restarts the connection. The audio pipeline is untouched. [userDelayMs] applies live.
 */
public data class LocalPlayerConfig(
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

public sealed interface Endpoint {
    /** The app has established that its server cannot provide this player's protocol. */
    public data class Unsupported(val reason: String) : Endpoint

    /**
     * Dedicated WebSocket. With an [authToken], the server is behind an authenticating
     * proxy: the player sends `auth` with the token and waits for `auth_ok` before
     * `client/init`. Without one, the Sendspin protocol starts at once.
     */
    public data class WebSocket(val url: String, val wire: WireVersion, val authToken: String? = null) : Endpoint

    /**
     * WebRTC data channel. Every connection attempt calls [openChannel]; the app
     * renegotiates inside it when needed. A channel is single-use.
     */
    public class WebRtc(
        public val wire: WireVersion,
        public val openChannel: suspend () -> SendspinTransport,
    ) : Endpoint
}

/**
 * Frames in and out over ONE connection. No reconnect, no epochs: when the
 * connection ends, [inbound] closes (with a cause on failure) and the transport
 * is discarded.
 */
public interface SendspinTransport {
    public val inbound: ReceiveChannel<Frame>

    /** Throws when the connection is gone. */
    public suspend fun send(frame: Frame)

    public suspend fun close()
}

public sealed interface Frame {
    public data class Text(val text: String) : Frame
    public class Binary(public val bytes: ByteArray) : Frame
}
