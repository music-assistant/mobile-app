package io.music_assistant.sendspin.api

/** Decodes one codec to interleaved PCM. Single-threaded: called only from the audio thread. */
public interface AudioDecoder {
    public fun configure(format: AudioFormatSpec, codecHeader: ByteArray?)

    /**
     * Returns PCM for the encoded bytes at [offset]..[offset]+[length], or empty on a decode error.
     * The PCM must be this input's own: the scheduler aligns it to this chunk's timestamp, so a
     * decoder whose output lags its input is heard as a gap followed by a skip.
     */
    public fun decode(input: ByteArray, offset: Int, length: Int): ByteArray

    /** Bit depth of the output after [configure]. */
    public val outputBitDepth: Int

    /** [AudioCodec.PCM] for a real decoder; the input codec for a pass-through (native decode in the sink). */
    public val outputCodec: AudioCodec

    public fun reset()

    public fun release()
}

public interface DecoderFactory {
    public fun supports(codec: AudioCodec): Boolean

    /** Throws when [codec] is unsupported; check [supports] first. */
    public fun create(codec: AudioCodec): AudioDecoder
}
