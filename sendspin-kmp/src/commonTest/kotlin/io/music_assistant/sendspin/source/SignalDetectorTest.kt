package io.music_assistant.sendspin.source

import io.music_assistant.sendspin.api.CaptureFormat
import io.music_assistant.sendspin.api.CapturedAudio
import io.music_assistant.sendspin.api.SourceSignal
import kotlin.test.Test
import kotlin.test.assertEquals

class SignalDetectorTest {
    private fun format(bitDepth: Int) = CaptureFormat(sampleRate = 1_000, channels = 1, bitDepth = bitDepth)

    /** One second of mono input at 1 kHz, every sample [sample] in little-endian [bytes] bytes. */
    private fun second(sample: Int, bytes: Int, atSeconds: Int) = CapturedAudio(
        ByteArray(1_000 * bytes) { i -> (sample shr (i % bytes * Byte.SIZE_BITS)).toByte() },
        atSeconds * 1_000_000L,
    )

    private fun SignalDetector.feed(sample: Int, bytes: Int, seconds: IntRange): SourceSignal =
        seconds.map { onAudio(second(sample, bytes, it)) }.last()

    @Test
    fun sixteenBitInputAtTheThresholdIsSilence() {
        val detector = SignalDetector(format(16))
        assertEquals(SourceSignal.Present, detector.feed(-SignalDetector.THRESHOLD, 2, 0..3))
        assertEquals(SourceSignal.Absent, detector.feed(SignalDetector.THRESHOLD, 2, 4..4), "5 s quiet")
        assertEquals(SourceSignal.Present, detector.feed(SignalDetector.THRESHOLD + 1, 2, 5..5))
    }

    @Test
    fun twentyFourBitInputUsesItsTopSixteenBits() {
        val detector = SignalDetector(format(24))
        // 2560 in 24 bits is 10 in its top 16 bits: quiet. A negative dither LSB is quiet too.
        detector.feed(2_560, 3, 0..2)
        assertEquals(SourceSignal.Absent, detector.feed(-1, 3, 3..4))
        assertEquals(SourceSignal.Present, detector.feed(-2_900, 3, 5..5))
    }

    @Test
    fun eightBitInputIsAlwaysPresent() {
        val detector = SignalDetector(format(8))
        assertEquals(SourceSignal.Present, detector.feed(0, 1, 0..9))
    }
}
