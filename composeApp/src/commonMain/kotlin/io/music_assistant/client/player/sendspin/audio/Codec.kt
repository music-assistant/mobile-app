package io.music_assistant.client.player.sendspin.audio

import io.music_assistant.client.player.sendspin.model.AudioCodec

enum class Codec(
    val decoderInitializer: () -> AudioDecoder,
    val sendspinAudioCodec: AudioCodec,
) {
    PCM({ PcmDecoder() }, AudioCodec.PCM),
    FLAC({ FlacDecoder() }, AudioCodec.FLAC),
    OPUS({ OpusDecoder() }, AudioCodec.OPUS),
    ;

    fun uiTitle() = when (this) {
        OPUS -> "Opus (compressed, lowest bandwidth)"
        FLAC -> "FLAC (lossless, medium bandwidth)"
        PCM -> "PCM (lossless, high bandwidth)"
    }
}

expect object Codecs {
    val default: Codec
    val list: List<Codec>
}

fun codecByName(name: String): Codec? =
    try {
        Codec.valueOf(name)
    } catch (_: Exception) {
        null
    }
