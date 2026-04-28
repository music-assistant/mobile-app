package io.music_assistant.client.data.model.server

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class MediaType {
    @SerialName("artist")
    ARTIST,

    @SerialName("album")
    ALBUM,

    @SerialName("track")
    TRACK,

    @SerialName("playlist")
    PLAYLIST,

    @SerialName("radio")
    RADIO,

    @SerialName("audiobook")
    AUDIOBOOK,

    @SerialName("podcast")
    PODCAST,

    @SerialName("podcast_episode")
    PODCAST_EPISODE,

    @SerialName("genre")
    GENRE,

    @SerialName("folder")
    FOLDER,

    @SerialName("flow_stream")
    FLOW_STREAM,

    @SerialName("announcement")
    ANNOUNCEMENT,

    @SerialName("unknown")
    UNKNOWN,
}
