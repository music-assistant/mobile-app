package io.music_assistant.sendspin.api

import kotlin.test.Test
import kotlin.test.assertEquals

class AudioFormatSpecTest {
    private fun List<AudioFormatSpec>.labels() = map { "${it.codec.name.lowercase()}/${it.sampleRate}/${it.bitDepth}" }

    @Test
    fun losslessCodecListsTheCdRateAfter48kHzSoTheFirstFormatStays48kHz() {
        assertEquals(
            listOf("flac/48000/24", "flac/48000/16", "flac/44100/24", "flac/44100/16", "pcm/48000/16"),
            advertisedFormats(listOf(AudioCodec.FLAC), bitDepth = 24) { true }.labels(),
        )
    }

    @Test
    fun opusStaysAt48kHzOnly() {
        assertEquals(
            listOf("opus/48000/16", "pcm/48000/16"),
            advertisedFormats(listOf(AudioCodec.OPUS), bitDepth = 24) { true }.labels(),
        )
    }

    @Test
    fun preferredPcmKeepsBothRatesWithoutADuplicateGuaranteedFormat() {
        assertEquals(
            listOf("pcm/48000/16", "pcm/44100/16"),
            advertisedFormats(listOf(AudioCodec.PCM), bitDepth = 16) { true }.labels(),
        )
    }

    @Test
    fun bufferCapacityStaysSizedFor48kHz() {
        val formats = advertisedFormats(listOf(AudioCodec.FLAC), bitDepth = 16) { true }
        assertEquals(10 * 192_000, bufferCapacityBytes(formats, seconds = 10))
    }
}
