package io.music_assistant.sendspin.wire

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class AudioCodec {
    @SerialName("opus")
    OPUS,

    @SerialName("flac")
    FLAC,

    @SerialName("pcm")
    PCM,
}
