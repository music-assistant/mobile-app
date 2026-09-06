package io.music_assistant.sendspin.wire

/**
 * One timestamped audio chunk as received on the wire: a view into [body]
 * starting at [offset]. No payload copy is made; the array is owned by the
 * chunk from here on.
 */
class AudioChunk(
    /** Server presentation time in microseconds (server clock). */
    val timestampMicros: Long,
    val body: ByteArray,
    val offset: Int,
) {
    val length: Int get() = body.size - offset
}

/**
 * A decrypted player-role message: 8 byte big-endian int64 timestamp, then
 * payload. This client implements only `player@v1`, so only audio chunks
 * (type 4) are parsed; other player types are reported as [BinaryFrame.Other].
 */
sealed interface BinaryFrame {
    data class Audio(val chunk: AudioChunk) : BinaryFrame
    data class Other(val type: Int) : BinaryFrame
    data object Malformed : BinaryFrame
}

object BinaryFrames {
    const val TIMESTAMP_BYTES = 8
    private const val TYPE_AUDIO_CHUNK = 4
    private const val BYTE_MASK = 0xFFL

    fun parse(type: Int, body: ByteArray): BinaryFrame {
        if (type != TYPE_AUDIO_CHUNK) return BinaryFrame.Other(type)
        if (body.size < TIMESTAMP_BYTES) return BinaryFrame.Malformed
        var timestamp = 0L
        for (i in 0 until TIMESTAMP_BYTES) {
            timestamp = (timestamp shl 8) or (body[i].toLong() and BYTE_MASK)
        }
        if (timestamp < 0) return BinaryFrame.Malformed
        return BinaryFrame.Audio(AudioChunk(timestamp, body, TIMESTAMP_BYTES))
    }
}
