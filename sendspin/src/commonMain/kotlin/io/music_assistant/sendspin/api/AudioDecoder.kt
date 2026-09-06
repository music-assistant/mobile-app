package io.music_assistant.sendspin.api

import io.music_assistant.sendspin.wire.AudioCodec
import io.music_assistant.sendspin.wire.AudioFormatSpec

/** Decodes one codec to interleaved PCM. Single-threaded: called only from the audio thread. */
interface AudioDecoder {
    fun configure(format: AudioFormatSpec, codecHeader: ByteArray?)

    /** Returns PCM for the encoded bytes at [offset]..[offset]+[length], or empty on a decode error. */
    fun decode(input: ByteArray, offset: Int, length: Int): ByteArray

    /** Bit depth of the output after [configure]. */
    val outputBitDepth: Int

    /** [AudioCodec.PCM] for a real decoder; the input codec for a pass-through (native decode in the sink). */
    val outputCodec: AudioCodec

    fun reset()

    fun release()
}

interface DecoderFactory {
    fun supports(codec: AudioCodec): Boolean

    /** Throws when [codec] is unsupported; check [supports] first. */
    fun create(codec: AudioCodec): AudioDecoder
}
