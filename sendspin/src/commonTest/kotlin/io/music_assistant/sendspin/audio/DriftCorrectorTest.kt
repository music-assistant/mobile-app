package io.music_assistant.sendspin.audio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DriftCorrectorTest {
    /** A rising ramp: frame f holds value (start + f) * 10 + channel. */
    private fun ramp(frames: Int, channels: Int = 2, start: Int = 0): ByteArray {
        val out = ByteArray(frames * channels * 2)
        for (f in 0 until frames) for (ch in 0 until channels) {
            val v = (start + f) * 10 + ch
            val at = (f * channels + ch) * 2
            out[at] = v.toByte()
            out[at + 1] = (v shr 8).toByte()
        }
        return out
    }

    private fun sample(pcm: ByteArray, frame: Int) = (pcm[frame * 2].toInt() and 0xFF) or (pcm[frame * 2 + 1].toInt() shl 8)

    @Test
    fun behindScheduleDropsFramesAheadInsertsThemWithinTheBound() {
        val corrector = DriftCorrector(channels = 2, bytesPerSample = 2)
        val block = ramp(48_000) // one second at 48 kHz
        val faster = corrector.correct(block, driftMicros = 500_000, blockMicros = 1_000_000)
        assertTrue(faster.size < block.size, "net drop when behind")
        assertTrue(block.size - faster.size <= 0.0021 * block.size + 4, "bounded to 0.2%: ${block.size - faster.size}")
        corrector.reset()
        val slower = corrector.correct(block, driftMicros = -500_000, blockMicros = 1_000_000)
        assertTrue(slower.size > block.size - 4, "net insert when ahead (one frame carried)")
        assertTrue(slower.size - block.size <= 0.0021 * block.size)
    }

    @Test
    fun smallDriftIsProportional() {
        val corrector = DriftCorrector(channels = 1, bytesPerSample = 2)
        val block = ramp(10_000, channels = 1)
        val out = corrector.correct(block, driftMicros = 1_000, blockMicros = 1_000_000)
        // 0.1% of 10 000 frames: about 10 frames dropped, plus the one carried.
        val dropped = (block.size - out.size) / 2
        assertTrue(dropped in 9..13, "dropped $dropped")
    }

    @Test
    fun stepOneIsFrameCountPreservingAcrossBlocksAndContinuous() {
        val corrector = DriftCorrector(channels = 1, bytesPerSample = 2)
        val a = corrector.correct(ramp(100, 1, start = 0), driftMicros = 1, blockMicros = 1_000_000_000)
        val b = corrector.correct(ramp(100, 1, start = 100), driftMicros = 1, blockMicros = 1_000_000_000)
        assertEquals(99 * 2, a.size, "the last frame is carried")
        assertEquals(100 * 2, b.size, "the carried frame is emitted")
        assertEquals(990, sample(b, 0), "block b starts with block a's last frame")
        assertEquals(1000, sample(b, 1))
        assertEquals(1980, sample(b, 99))
    }

    @Test
    fun passesThroughWhenNothingToCorrectOrUnsupportedDepth() {
        val block = ramp(100)
        assertSame(block, DriftCorrector(2, 2).correct(block, driftMicros = 0, blockMicros = 1_000))
        assertSame(block, DriftCorrector(2, 3).correct(block, driftMicros = 5_000, blockMicros = 1_000))
    }
}
