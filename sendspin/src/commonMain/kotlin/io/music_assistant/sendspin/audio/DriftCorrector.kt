package io.music_assistant.sendspin.audio

import kotlin.math.ceil
import kotlin.math.floor

/**
 * Corrects small playback drift by gently resampling interleaved 16-bit
 * little-endian PCM (linear interpolation), bounded to [maxCorrectionPpm] so
 * the pitch change stays inaudible. Other bit depths pass through.
 *
 * The input is one continuous signal split into blocks. The last frame of a
 * block is carried into the next call so the interpolation runs across block
 * boundaries without dropping or repeating a frame: the output lags the input
 * by at most one frame and is otherwise sample-count preserving at step 1.
 */
internal class DriftCorrector(
    private val channels: Int,
    private val bytesPerSample: Int,
    private val maxCorrectionPpm: Double = 2_000.0,
) {
    private val frameBytes = channels * bytesPerSample

    /** Read position relative to the current block; -1.0 is the carried frame. */
    private var phase = 0.0
    private var carry: ByteArray? = null

    /**
     * @param driftMicros positive when playback is behind schedule (speed up: net drop),
     *   negative when ahead (slow down: net insert).
     * @param blockMicros nominal duration of [pcm] at the stream's sample rate.
     */
    fun correct(pcm: ByteArray, driftMicros: Long, blockMicros: Long): ByteArray {
        if (bytesPerSample != 2 || blockMicros <= 0L) return pcm
        if (driftMicros == 0L && carry == null) return pcm
        val frames = pcm.size / frameBytes
        if (frames == 0) return pcm
        val maxFraction = maxCorrectionPpm / PPM
        val step = 1.0 + (driftMicros.toDouble() / blockMicros).coerceIn(-maxFraction, maxFraction)

        val lastFrame = frames - 1
        val capacity = ceil((lastFrame - phase) / step).toInt() + 2
        val out = ByteArray(capacity * frameBytes)
        var pos = phase
        var o = 0
        while (pos < lastFrame && o < capacity) {
            val idx = floor(pos).toInt()
            val frac = pos - idx
            for (ch in 0 until channels) {
                val a = sampleAt(pcm, idx, ch)
                val b = sampleAt(pcm, idx + 1, ch)
                val v = (a + (b - a) * frac).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                val at = o * frameBytes + ch * 2
                out[at] = v.toByte()
                out[at + 1] = (v shr BITS_PER_BYTE).toByte()
            }
            pos += step
            o++
        }
        carry = pcm.copyOfRange(lastFrame * frameBytes, frames * frameBytes)
        phase = pos - frames
        return out.copyOf(o * frameBytes)
    }

    fun reset() {
        phase = 0.0
        carry = null
    }

    private companion object {
        const val PPM = 1_000_000.0
        const val BYTE_MASK = 0xFF
        const val BITS_PER_BYTE = 8
    }

    private fun sampleAt(pcm: ByteArray, frame: Int, channel: Int): Int {
        val source = if (frame < 0) carry ?: pcm else pcm
        val at = (if (frame < 0) 0 else frame * frameBytes) + channel * 2
        return (source[at].toInt() and BYTE_MASK) or (source[at + 1].toInt() shl BITS_PER_BYTE)
    }
}
