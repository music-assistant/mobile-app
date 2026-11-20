package io.music_assistant.sendspin.api

/** Encodes captured PCM for the `source@v1` role. Single-threaded: called only from [SendspinDeps.audioDispatcher]. */
public interface AudioEncoder {
    /**
     * The codec header that `client-stream/start` carries, or null while the encoder
     * has not produced it. FLAC: the `fLaC` marker and the STREAMINFO block.
     */
    public val header: ByteArray?

    /**
     * Takes [audio] and returns the chunks that are complete, possibly none. Each chunk holds
     * whole codec frames of 150 ms at most. Throws when the encoder fails; the player then
     * releases it and continues with PCM.
     */
    public fun encode(audio: CapturedAudio): List<EncodedAudio>

    public fun release()
}

/** [captureTimeMicros] is the capture time of the first sample that a decoder gets from [data]. */
public class EncodedAudio(
    public val data: ByteArray,
    public val captureTimeMicros: Long,
)

public interface EncoderFactory {
    /** Null when the device cannot encode [codec] from [format]. The player asks for [AudioCodec.FLAC] only. */
    public fun create(codec: AudioCodec, format: CaptureFormat): AudioEncoder?
}
