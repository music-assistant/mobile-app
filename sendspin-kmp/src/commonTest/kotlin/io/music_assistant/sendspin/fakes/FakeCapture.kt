package io.music_assistant.sendspin.fakes

import io.music_assistant.sendspin.api.AudioCapture
import io.music_assistant.sendspin.api.AudioCodec
import io.music_assistant.sendspin.api.AudioEncoder
import io.music_assistant.sendspin.api.CaptureFormat
import io.music_assistant.sendspin.api.CapturedAudio
import io.music_assistant.sendspin.api.EncodedAudio
import io.music_assistant.sendspin.api.EncoderFactory
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** An input that the test feeds by hand. Every collection reads the same queue. */
class FakeCapture(
    override val format: CaptureFormat = CaptureFormat(sampleRate = 48_000, channels = 2, bitDepth = 16),
) : AudioCapture {
    private val input = Channel<CapturedAudio>(Channel.UNLIMITED)

    /** How many recordings started. */
    var recordings = 0
        private set

    var recording = false
        private set

    override fun frames(): Flow<CapturedAudio> = flow {
        recordings++
        recording = true
        try {
            for (audio in input) emit(audio)
        } finally {
            recording = false
        }
    }

    /** [millis] of audio captured at [timeMicros], every byte set to [marker]. */
    fun capture(timeMicros: Long, millis: Int = 20, marker: Byte = 1): ByteArray =
        ByteArray(format.sampleRate * millis / 1_000 * format.bytesPerFrame) { marker }
            .also { input.trySend(CapturedAudio(it, timeMicros)) }

    /** The input ends, as when the user revokes the capture. */
    fun end() {
        input.close()
    }
}

/**
 * Pass-through "FLAC": one chunk for each input, with the input bytes. The header shows
 * after [headerAfter] inputs (never when null), and input number [failAt] throws.
 */
class FakeEncoderFactory(
    private val headerAfter: Int? = 1,
    private val failAt: Int? = null,
) : EncoderFactory {
    val created = mutableListOf<FakeEncoder>()

    override fun create(codec: AudioCodec, format: CaptureFormat): AudioEncoder {
        require(codec == AudioCodec.FLAC)
        return FakeEncoder().also { created += it }
    }

    inner class FakeEncoder : AudioEncoder {
        private var inputs = 0
        var released = false
            private set

        override val header: ByteArray?
            get() = HEADER.takeIf { headerAfter != null && inputs >= headerAfter }

        override fun encode(audio: CapturedAudio): List<EncodedAudio> {
            inputs++
            check(inputs != failAt) { "encoder broke" }
            return listOf(EncodedAudio(audio.pcm, audio.captureTimeMicros))
        }

        override fun release() {
            released = true
        }
    }

    companion object {
        val HEADER = byteArrayOf(0x66, 0x4C, 0x61, 0x43, 1, 2, 3)
    }
}
