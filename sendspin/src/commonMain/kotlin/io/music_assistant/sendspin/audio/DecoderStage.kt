package io.music_assistant.sendspin.audio

import io.music_assistant.sendspin.api.AudioDecoder
import io.music_assistant.sendspin.api.DecoderFactory
import io.music_assistant.sendspin.api.SinkFormat
import io.music_assistant.sendspin.wire.AudioChunk
import io.music_assistant.sendspin.wire.AudioCodec
import io.music_assistant.sendspin.wire.AudioFormatSpec
import io.music_assistant.sendspin.wire.StreamStartPlayer
import kotlin.io.encoding.Base64

/** Owns one decoder at a time on the audio thread. No locks: single owner. */
internal class DecoderStage(private val factory: DecoderFactory) {
    private var decoder: AudioDecoder? = null
    var consecutiveFailures = 0
        private set

    /** Replaces the decoder for [stream]; returns the format the sink will receive. */
    fun open(stream: StreamStartPlayer): SinkFormat {
        close()
        val codec = codecOf(stream.codec) ?: throw IllegalArgumentException("unsupported codec ${stream.codec}")
        val header = stream.codecHeader?.let { Base64.decode(it) }
        val next = factory.create(codec)
        next.configure(AudioFormatSpec(codec, stream.channels, stream.sampleRate, stream.bitDepth), header)
        decoder = next
        consecutiveFailures = 0
        return SinkFormat(stream.sampleRate, stream.channels, next.outputBitDepth, next.outputCodec, stream.codecHeader)
    }

    /** PCM for [chunk], or null on a decode error (counted in [consecutiveFailures]). */
    fun decode(chunk: AudioChunk): ByteArray? {
        val pcm = decoder?.decode(chunk.body, chunk.offset, chunk.length) ?: return null
        if (pcm.isEmpty()) {
            consecutiveFailures++
            return null
        }
        consecutiveFailures = 0
        return pcm
    }

    fun reset() = decoder?.reset()

    fun close() {
        decoder?.release()
        decoder = null
    }

    fun supports(codecName: String): Boolean = codecOf(codecName)?.let(factory::supports) == true

    private fun codecOf(name: String): AudioCodec? = AudioCodec.entries.firstOrNull {
        it.name.equals(name, ignoreCase = true)
    }
}
