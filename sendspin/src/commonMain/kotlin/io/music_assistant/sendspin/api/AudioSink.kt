package io.music_assistant.sendspin.api

import io.music_assistant.sendspin.wire.AudioCodec
import kotlinx.coroutines.flow.Flow

/** Platform audio output. [open] builds a fresh device stream every time. */
interface AudioSink {
    fun open(format: SinkFormat): SinkHandle
}

/**
 * What the sink receives. Normally PCM; a platform whose native player
 * decodes itself (iOS) provides pass-through decoders and gets [codec] with
 * the stream's [codecHeaderBase64], and the scheduler treats the bytes as opaque.
 */
data class SinkFormat(
    val sampleRate: Int,
    val channels: Int,
    /** 16, 24, or 32. */
    val bitDepth: Int,
    val codec: AudioCodec = AudioCodec.PCM,
    val codecHeaderBase64: String? = null,
) {
    val isPcm: Boolean get() = codec == AudioCodec.PCM
    val bytesPerFrame: Int get() = channels * bitDepth / 8
}

interface SinkHandle : AutoCloseable {
    /**
     * Writes interleaved PCM. Blocks until the sink accepted the bytes.
     * Returns the bytes accepted, or -1 when the sink is dead.
     */
    fun write(pcm: ByteArray, offset: Int, length: Int): Int

    fun pause()

    fun resume()

    /** Drops audio queued inside the sink. */
    fun flush()

    /** Where the device is in the written stream, or null when the platform cannot say. */
    fun position(): SinkPosition?

    /** Cumulative count of device underruns; 0 when the platform cannot say. */
    fun underrunCount(): Int

    /** Output latency after the last written frame, or null when unknown. */
    val latencyMicros: Long?

    val events: Flow<SinkEvent>
}

/** [framesPlayed] frames had reached the device at local time [atMicros]. */
data class SinkPosition(val framesPlayed: Long, val atMicros: Long)

enum class SinkEvent {
    /** Output taken away (focus loss, call, headphones unplugged): the stream ends. */
    FocusLost,

    /** The interruption that caused [FocusLost] is over; the app may resume playback. */
    FocusRegained,
    RouteChanged,
    Died,
}
