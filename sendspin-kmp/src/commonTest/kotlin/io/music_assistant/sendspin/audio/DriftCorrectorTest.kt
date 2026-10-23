package io.music_assistant.sendspin.audio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DriftCorrectorTest {
    private val depths = listOf(2, 3)

    /** Interleaved little-endian PCM of [values] (one per sample), [bytes] per sample. */
    private fun pcm(values: List<Int>, bytes: Int): ByteArray {
        val out = ByteArray(values.size * bytes)
        values.forEachIndexed { i, v -> for (b in 0 until bytes) out[i * bytes + b] = (v shr (8 * b)).toByte() }
        return out
    }

    /** A rising ramp: frame f holds value (start + f) * 10 + channel. */
    private fun ramp(frames: Int, channels: Int = 2, start: Int = 0, bytes: Int = 2): ByteArray =
        pcm((0 until frames).flatMap { f -> (0 until channels).map { ch -> (start + f) * 10 + ch } }, bytes)

    /** Sample [index] in interleaved order, sign-extended. */
    private fun sample(pcm: ByteArray, index: Int, bytes: Int = 2): Int {
        val at = index * bytes
        var v = pcm[at + bytes - 1].toInt() shl (8 * (bytes - 1))
        for (b in 0 until bytes - 1) v = v or ((pcm[at + b].toInt() and 0xFF) shl (8 * b))
        return v
    }

    @Test
    fun behindScheduleDropsFramesAheadInsertsThemWithinTheBound() = depths.forEach { bytes ->
        val corrector = DriftCorrector(channels = 2, bytesPerSample = bytes)
        val block = ramp(48_000, bytes = bytes) // one second at 48 kHz
        val frame = 2 * bytes
        val faster = corrector.correct(block, driftMicros = 500_000, blockMicros = 1_000_000)
        assertTrue(faster.size < block.size, "net drop when behind")
        assertTrue(block.size - faster.size <= 0.0021 * block.size + 2 * frame, "bounded to 0.2%")
        corrector.reset()
        val slower = corrector.correct(block, driftMicros = -500_000, blockMicros = 1_000_000)
        assertTrue(slower.size > block.size - 2 * frame, "net insert when ahead (one frame carried)")
        assertTrue(slower.size - block.size <= 0.0021 * block.size)
    }

    @Test
    fun smallDriftIsProportional() = depths.forEach { bytes ->
        val corrector = DriftCorrector(channels = 1, bytesPerSample = bytes)
        val block = ramp(10_000, channels = 1, bytes = bytes)
        val out = corrector.correct(block, driftMicros = 1_000, blockMicros = 1_000_000)
        // 0.1% of 10 000 frames: about 10 frames dropped, plus the one carried.
        val dropped = (block.size - out.size) / bytes
        assertTrue(dropped in 9..13, "dropped $dropped at $bytes bytes")
    }

    @Test
    fun stepOneIsFrameCountPreservingAcrossBlocksAndContinuous() = depths.forEach { bytes ->
        val corrector = DriftCorrector(channels = 1, bytesPerSample = bytes)
        val longBlock = 1_000_000_000L // 1 us of drift over 1000 s: step 1
        val a = corrector.correct(ramp(100, 1, 0, bytes), driftMicros = 1, blockMicros = longBlock)
        val b = corrector.correct(ramp(100, 1, 100, bytes), driftMicros = 1, blockMicros = longBlock)
        assertEquals(99 * bytes, a.size, "the last frame is carried")
        assertEquals(100 * bytes, b.size, "the carried frame is emitted")
        assertEquals(990, sample(b, 0, bytes), "block b starts with block a's last frame")
        assertEquals(1000, sample(b, 1, bytes))
        assertEquals(1980, sample(b, 99, bytes))
    }

    @Test
    fun twentyFourBitExtremesSurviveBitExact() {
        val values = listOf(8_388_607, -8_388_608, -1, 0, 1, -65_536, 65_535)
        val corrector = DriftCorrector(channels = 1, bytesPerSample = 3)
        val first = corrector.correct(pcm(values, 3), driftMicros = 1, blockMicros = 1_000_000_000)
        val second = corrector.correct(pcm(listOf(0), 3), driftMicros = 1, blockMicros = 1_000_000_000)
        val out = first + second
        assertEquals(values, values.indices.map { sample(out, it, 3) }, "sign extension and byte order")
    }

    @Test
    fun twentyFourBitInterpolationStaysInRangeAcrossZeroAndExtremes() {
        val corrector = DriftCorrector(channels = 1, bytesPerSample = 3)
        val swing = List(2_000) { if (it % 2 == 0) 8_388_607 else -8_388_608 }
        val out = corrector.correct(pcm(swing, 3), driftMicros = 2_000, blockMicros = 1_000_000)
        val samples = (0 until out.size / 3).map { sample(out, it, 3) }
        assertTrue(samples.all { it in -8_388_608..8_388_607 })
        assertTrue(samples.any { it in -1_000_000..1_000_000 }, "interpolated between the extremes")
    }

    @Test
    fun twentyFourBitStereoKeepsChannelsAligned() {
        val corrector = DriftCorrector(channels = 2, bytesPerSample = 3)
        val block = pcm((0 until 1_000).flatMap { listOf(-4_000_000, 4_000_000) }, 3)
        val out = corrector.correct(block, driftMicros = 1_000, blockMicros = 10_000)
        val frames = out.size / 6
        assertTrue(frames > 0)
        for (f in 0 until frames) {
            assertEquals(-4_000_000, sample(out, 2 * f, 3), "left at frame $f")
            assertEquals(4_000_000, sample(out, 2 * f + 1, 3), "right at frame $f")
        }
    }

    @Test
    fun passesThroughWhenNothingToCorrectOrUnsupportedDepth() {
        val block = ramp(100)
        assertSame(block, DriftCorrector(2, 2).correct(block, driftMicros = 0, blockMicros = 1_000))
        val wide = ramp(100, bytes = 4)
        assertSame(wide, DriftCorrector(2, 4).correct(wide, driftMicros = 5_000, blockMicros = 1_000))
    }
}
