package io.music_assistant.client.player.sendspin.audio

// Opus on iOS uses a deliberate two-stage pipeline:
//   1. The KMP `OpusDecoder.ios` is a pass-through that preserves Opus packet
//      boundaries through the reorder buffer (Opus is stateful — order matters).
//   2. The Swift `OpusLibDecoder` (swift-opus / libopus) does the real decode
//      to interleaved Int16 PCM inside `NativeAudioController`.
actual object Codecs {
    actual val default: Codec = Codec.OPUS
    actual val list: List<Codec> = listOf(Codec.OPUS, Codec.FLAC, Codec.PCM)
}
