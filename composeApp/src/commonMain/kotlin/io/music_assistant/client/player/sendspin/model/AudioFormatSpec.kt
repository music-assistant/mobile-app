// PCM bit-depth literals (16/24/32) and sample-rate bounds are audio-format standards;
// naming them `BIT_DEPTH_16` etc. doesn't add clarity over the literal.
@file:Suppress("MagicNumber")

package io.music_assistant.client.player.sendspin.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AudioFormatSpec(
    @SerialName("codec") val codec: AudioCodec,
    @SerialName("channels") val channels: Int,
    @SerialName("sample_rate") val sampleRate: Int,
    @SerialName("bit_depth") val bitDepth: Int,
) {
    init {
        require(channels in 1..32) { "Channels must be between 1 and 32" }
        require(sampleRate in 1..384_000) { "Sample rate must be between 1 and 384000 Hz" }
        require(bitDepth in setOf(16, 24, 32)) { "Bit depth must be 16, 24, or 32" }
    }
}
