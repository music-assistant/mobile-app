package io.music_assistant.sendspin.android

import io.github.jaredmdobson.concentus.OpusApplication
import io.github.jaredmdobson.concentus.OpusEncoder
import io.music_assistant.sendspin.api.AudioCodec
import io.music_assistant.sendspin.api.AudioFormatSpec
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AndroidDecoderFactoryTest {
    private val factory = AndroidDecoderFactory()

    @Test
    fun opusDecodesToSixteenBitStereoFrames() {
        val decoder = factory.create(AudioCodec.OPUS).apply {
            configure(AudioFormatSpec(AudioCodec.OPUS, CHANNELS, RATE, bitDepth = 16), codecHeader = null)
        }
        val encoder = OpusEncoder(RATE, CHANNELS, OpusApplication.OPUS_APPLICATION_AUDIO)
        val packet = ByteArray(MAX_PACKET)

        val frames = (0 until FRAMES).map { index ->
            val length = encoder.encode(sineFrame(index), 0, FRAME_SAMPLES, packet, 0, packet.size)
            decoder.decode(packet, 0, length)
        }

        assertEquals(16, decoder.outputBitDepth)
        assertEquals(AudioCodec.PCM, decoder.outputCodec)
        frames.forEachIndexed { index, pcm ->
            assertEquals(FRAME_SAMPLES * CHANNELS * 2, pcm.size, "bytes of frame $index")
        }
        // Past the encoder delay, each channel is its own sine: loud and smooth. Wrong byte
        // order or interleaving makes jumps far above the sine's largest step.
        val last = littleEndianSamples(frames.last())
        for (channel in 0 until CHANNELS) {
            val samples = last.filterIndexed { i, _ -> i % CHANNELS == channel }
            assertTrue(samples.maxOf(::abs) > AMPLITUDE / 2, "peak of channel $channel")
            assertTrue(samples.zipWithNext { a, b -> abs(b - a) }.max() < MAX_STEP, "largest step of channel $channel")
        }
    }

    @Test
    fun pcmPassesTheRequestedBytesAndBitDepthThrough() {
        val decoder = factory.create(AudioCodec.PCM).apply {
            configure(AudioFormatSpec(AudioCodec.PCM, CHANNELS, RATE, bitDepth = 24), codecHeader = null)
        }
        val input = ByteArray(12) { it.toByte() }

        assertEquals(24, decoder.outputBitDepth)
        assertEquals(AudioCodec.PCM, decoder.outputCodec)
        assertContentEquals(input, decoder.decode(input, 0, input.size))
        assertContentEquals(input.copyOfRange(3, 9), decoder.decode(input, 3, 6))
    }

    /** Interleaved stereo: [FREQUENCIES] Hz per channel, continuous across frames. */
    private fun sineFrame(index: Int) = ShortArray(FRAME_SAMPLES * CHANNELS) { i ->
        val t = (index * FRAME_SAMPLES + i / CHANNELS).toDouble() / RATE
        (AMPLITUDE * sin(2 * PI * FREQUENCIES[i % CHANNELS] * t)).roundToInt().toShort()
    }

    private fun littleEndianSamples(pcm: ByteArray): List<Int> =
        (0 until pcm.size / 2).map { i -> (pcm[i * 2].toInt() and 0xFF) or (pcm[i * 2 + 1].toInt() shl 8) }

    private companion object {
        const val RATE = 48_000
        const val CHANNELS = 2
        const val FRAME_SAMPLES = 960 // 20 ms
        const val FRAMES = 10
        const val MAX_PACKET = 4000
        const val AMPLITUDE = 16_384
        val FREQUENCIES = intArrayOf(440, 880)

        /** Twice the largest step of the 880 Hz sine (2π·880/48000·16384 ≈ 1887). */
        const val MAX_STEP = 4000
    }
}
