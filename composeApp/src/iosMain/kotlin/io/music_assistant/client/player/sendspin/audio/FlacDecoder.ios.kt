package io.music_assistant.client.player.sendspin.audio

import io.music_assistant.client.player.sendspin.model.AudioCodec
import io.music_assistant.client.player.sendspin.model.AudioFormatSpec

actual class FlacDecoder : AudioDecoder {
    actual override fun configure(config: AudioFormatSpec, codecHeader: String?) {
        // Pass-through: No configuration needed for raw stream passing
    }

    actual override fun decode(encodedData: ByteArray): ByteArray {
        // Pass-through: Return raw flac data to be handled by MPV
        return encodedData
    }

    actual override fun reset() {
        // Nothing to reset
    }

    actual override fun release() {
        // Nothing to release
    }

    actual override fun getOutputCodec(): AudioCodec = AudioCodec.FLAC
}
