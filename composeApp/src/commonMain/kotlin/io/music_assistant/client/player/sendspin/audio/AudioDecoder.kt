package io.music_assistant.client.player.sendspin.audio

import io.music_assistant.client.player.sendspin.model.AudioCodec
import io.music_assistant.client.player.sendspin.model.AudioFormatSpec

interface AudioDecoder {
    fun configure(config: AudioFormatSpec, codecHeader: String?)
    fun decode(encodedData: ByteArray): ByteArray
    fun reset()
    fun release()

    /**
     * Returns the audio codec format that this decoder outputs.
     * - PCM: Decoder converts encoded data to raw PCM (Android/Desktop decoders)
     * - OPUS/FLAC/etc: Decoder passes through encoded data (iOS passthrough to MPV)
     */
    fun getOutputCodec(): AudioCodec

    /**
     * Returns the actual bit depth of the decoded output.
     * May differ from the configured bit depth if the decoder cannot produce the
     * requested depth (e.g., Opus always outputs 16-bit).
     * Only meaningful when [getOutputCodec] returns [AudioCodec.PCM].
     * Must be called after [configure].
     */
    fun getOutputBitDepth(): Int
}

class PcmDecoder(private var bitDepth: Int = 16) : AudioDecoder {
    override fun configure(config: AudioFormatSpec, codecHeader: String?) {
        bitDepth = config.bitDepth
    }

    override fun decode(encodedData: ByteArray): ByteArray {
        // PCM is already decoded, just pass through
        return encodedData
    }

    override fun reset() {
        // Nothing to reset
    }

    override fun release() {
        // Nothing to release
    }

    override fun getOutputCodec(): AudioCodec = AudioCodec.PCM
    override fun getOutputBitDepth(): Int = bitDepth
}

// Platform-specific decoders for FLAC and OPUS
expect class FlacDecoder() : AudioDecoder {
    override fun configure(config: AudioFormatSpec, codecHeader: String?)
    override fun decode(encodedData: ByteArray): ByteArray
    override fun reset()
    override fun release()
    override fun getOutputCodec(): AudioCodec
    override fun getOutputBitDepth(): Int
}

expect class OpusDecoder() : AudioDecoder {
    override fun configure(config: AudioFormatSpec, codecHeader: String?)
    override fun decode(encodedData: ByteArray): ByteArray
    override fun reset()
    override fun release()
    override fun getOutputCodec(): AudioCodec
    override fun getOutputBitDepth(): Int
}
