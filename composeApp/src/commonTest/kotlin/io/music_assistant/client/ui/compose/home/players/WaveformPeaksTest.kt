package io.music_assistant.client.ui.compose.home.players

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class WaveformPeaksTest {
    @Test
    fun `each bar gets the maximum of the bins it covers`() {
        val bins = listOf(0.1f, 0.9f, 0.2f, 0.3f, 0.4f, 0.0f)

        assertContentEquals(floatArrayOf(0.9f, 0.3f, 0.4f), waveformPeaks(bins, 3))
    }

    @Test
    fun `with more bars than bins a bin fills several bars`() {
        val bins = listOf(0.2f, 0.8f)

        assertContentEquals(floatArrayOf(0.2f, 0.2f, 0.8f, 0.8f), waveformPeaks(bins, 4))
    }

    @Test
    fun `an uneven split covers every bin once`() {
        val bins = List(1800) { if (it == 1799) 1f else 0f }

        val peaks = waveformPeaks(bins, 7)

        assertEquals(7, peaks.size)
        assertEquals(1f, peaks.last())
        assertEquals(0f, peaks.dropLast(1).max())
    }

    @Test
    fun `no bars or no bins give no peaks`() {
        assertEquals(0, waveformPeaks(listOf(0.5f), 0).size)
        assertEquals(0, waveformPeaks(emptyList(), 5).size)
    }
}
