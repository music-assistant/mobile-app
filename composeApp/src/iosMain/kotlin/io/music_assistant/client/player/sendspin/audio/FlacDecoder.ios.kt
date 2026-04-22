package io.music_assistant.client.player.sendspin.audio

import io.music_assistant.client.player.sendspin.model.AudioCodec
import io.music_assistant.client.player.sendspin.model.AudioFormatSpec

actual class FlacDecoder : AudioDecoder {
    private var bitDepth: Int = 16

    actual override fun configure(config: AudioFormatSpec, codecHeader: String?) {
        bitDepth = config.bitDepth
    }

    actual override fun decode(encodedData: ByteArray): ByteArray {
        // Pass-through: raw FLAC data decoded by native Swift FLACLibDecoder
        return encodedData
    }

    actual override fun reset() {
        // Nothing to reset
    }

    actual override fun release() {
        // Nothing to release
    }

    actual override fun getOutputCodec(): AudioCodec = AudioCodec.FLAC
    actual override fun getOutputBitDepth(): Int = bitDepth
}
