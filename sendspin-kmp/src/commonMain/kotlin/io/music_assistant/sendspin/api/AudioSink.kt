package io.music_assistant.sendspin.api

import kotlinx.coroutines.flow.Flow

/** Platform audio output. [open] builds a fresh device stream every time. */
public interface AudioSink {
    public fun open(format: SinkFormat): SinkHandle
}

/**
 * What the sink receives. Normally PCM; a platform whose native player
 * decodes itself (iOS) provides pass-through decoders and gets [codec] with
 * the stream's [codecHeaderBase64], and the scheduler treats the bytes as opaque.
 */
public data class SinkFormat(
    val sampleRate: Int,
    val channels: Int,
    /** 16, 24, or 32. */
    val bitDepth: Int,
    val codec: AudioCodec = AudioCodec.PCM,
    val codecHeaderBase64: String? = null,
) {
    public val isPcm: Boolean get() = codec == AudioCodec.PCM
    public val bytesPerFrame: Int get() = channels * bitDepth / 8
}

public interface SinkHandle : AutoCloseable {
    /**
     * Writes interleaved PCM. Blocks until the sink accepted the bytes.
     * Returns the bytes accepted (at least 1), or -1 when the sink is dead.
     * There is no zero: a sink that accepts nothing is dead.
     */
    public fun write(pcm: ByteArray, offset: Int, length: Int): Int

    public fun pause()

    public fun resume()

    /** Drops audio queued inside the sink. */
    public fun flush()

    /** Where the device is in the written stream, or null when the platform cannot say. */
    public fun position(): SinkPosition?

    /** Cumulative count of device underruns; 0 when the platform cannot say. */
    public fun underrunCount(): Int

    /** Output latency after the last written frame, or null when unknown. */
    public val latencyMicros: Long?

    public val events: Flow<SinkEvent>
}

/** [framesPlayed] frames had reached the device at local time [atMicros]. */
public data class SinkPosition(val framesPlayed: Long, val atMicros: Long)

public enum class SinkEvent {
    /**
     * Output taken away (focus loss, call, headphones unplugged): the stream ends.
     * The sink silences itself and keeps accepting writes, so a writer never blocks.
     */
    FocusLost,

    /** The interruption that caused [FocusLost] is over; the app may resume playback. */
    FocusRegained,
    RouteChanged,
    Died,
}
