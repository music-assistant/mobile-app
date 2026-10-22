package io.music_assistant.sendspin.wire

import io.music_assistant.sendspin.api.WireVersion

/**
 * One timestamped audio chunk as received on the wire: a view into [body]
 * starting at [offset]. No payload copy is made; the array is owned by the
 * chunk from here on.
 */
internal class AudioChunk(
    /** Server presentation time in microseconds (server clock). */
    val timestampMicros: Long,
    val body: ByteArray,
    val offset: Int,
) {
    val length: Int get() = body.size - offset
}

/**
 * A decrypted player-role message: 8 byte big-endian int64 timestamp, on spec
 * 1.0.0-rc1 a 4 byte big-endian uint32 `send_ahead`, then payload. This client
 * implements only `player@v1`, so only audio chunks (type 4) are parsed; other
 * player types are reported as [BinaryFrame.Other].
 *
 * `send_ahead` is the lead the server had when it sent the chunk (0 and
 * 0xFFFFFFFF mean "not measured"). It carries no scheduling meaning and is
 * skipped; extend [AudioChunk] when the scheduler wants to measure arrival delay.
 */
internal sealed interface BinaryFrame {
    data class Audio(val chunk: AudioChunk) : BinaryFrame
    data class Other(val type: Int) : BinaryFrame

    /** Shorter than its header, or a negative timestamp: a protocol error on spec 1.0.0-rc1. */
    data object Malformed : BinaryFrame
}

internal object BinaryFrames {
    const val TIMESTAMP_BYTES = 8
    const val SEND_AHEAD_BYTES = 4
    private const val TYPE_AUDIO_CHUNK = 4
    private const val BYTE_MASK = 0xFFL
    private const val BITS_PER_BYTE = 8

    /** Header bytes before the encoded audio. */
    fun headerBytes(wire: WireVersion): Int = when (wire) {
        WireVersion.LEGACY_9 -> TIMESTAMP_BYTES
        WireVersion.SPEC_1_0 -> TIMESTAMP_BYTES + SEND_AHEAD_BYTES
    }

    fun parse(wire: WireVersion, type: Int, body: ByteArray): BinaryFrame {
        if (type != TYPE_AUDIO_CHUNK) return BinaryFrame.Other(type)
        val header = headerBytes(wire)
        if (body.size < header) return BinaryFrame.Malformed
        var timestamp = 0L
        for (i in 0 until TIMESTAMP_BYTES) {
            timestamp = (timestamp shl BITS_PER_BYTE) or (body[i].toLong() and BYTE_MASK)
        }
        if (timestamp < 0) return BinaryFrame.Malformed
        return BinaryFrame.Audio(AudioChunk(timestamp, body, header))
    }
}
