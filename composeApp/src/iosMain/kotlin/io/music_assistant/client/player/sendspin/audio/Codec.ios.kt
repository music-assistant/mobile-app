package io.music_assistant.client.player.sendspin.audio

// Opus is intentionally omitted: the iOS OpusDecoder is a pass-through stub
// (no real decoder), so selecting Opus produces broken audio. Restore to the
// list once a real iOS Opus decoder ships.
actual object Codecs {
    actual val default: Codec = Codec.FLAC
    actual val list: List<Codec> = listOf(Codec.FLAC, Codec.PCM)
}
