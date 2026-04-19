package io.music_assistant.client.player.sendspin.audio

actual object Codecs {
    actual val default: Codec = Codec.OPUS
    actual val list: List<Codec> = listOf(Codec.OPUS, Codec.FLAC, Codec.PCM)
}
