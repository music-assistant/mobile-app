package io.music_assistant.sendspin.fakes

import io.music_assistant.sendspin.api.AudioDecoder
import io.music_assistant.sendspin.api.DecoderFactory
import io.music_assistant.sendspin.wire.AudioCodec
import io.music_assistant.sendspin.wire.AudioFormatSpec

/** Pass-through decoders: chunk bytes are the PCM. [failing] codecs return empty output. */
class FakeDecoderFactory(
    private val supported: Set<AudioCodec> = setOf(AudioCodec.PCM, AudioCodec.FLAC),
    var failing: Boolean = false,
) : DecoderFactory {
    val created = mutableListOf<FakeDecoder>()

    override fun supports(codec: AudioCodec): Boolean = codec in supported

    override fun create(codec: AudioCodec): AudioDecoder {
        require(supports(codec))
        return FakeDecoder().also { created += it }
    }

    inner class FakeDecoder : AudioDecoder {
        var configured: AudioFormatSpec? = null
        var header: ByteArray? = null
        var released = false
        var resets = 0

        /** Real decoders are stateful: each call must correspond to exactly one chunk. */
        var decodes = 0

        override fun configure(format: AudioFormatSpec, codecHeader: ByteArray?) {
            configured = format
            header = codecHeader
        }

        override fun decode(input: ByteArray, offset: Int, length: Int): ByteArray {
            decodes++
            return if (failing) ByteArray(0) else input.copyOfRange(offset, offset + length)
        }

        override val outputBitDepth: Int get() = 16

        override val outputCodec: AudioCodec get() = AudioCodec.PCM

        override fun reset() {
            resets++
        }

        override fun release() {
            released = true
        }
    }
}
