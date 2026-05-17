package io.music_assistant.client.data.model.client

enum class MediaType(val serverValue: String) {
    ARTIST("artist"),
    ALBUM("album"),
    TRACK("track"),
    PLAYLIST("playlist"),
    RADIO("radio"),
    AUDIOBOOK("audiobook"),
    PODCAST("podcast"),
    PODCAST_EPISODE("podcast_episode"),
    GENRE("genre"),
    FOLDER("folder"),
    FLOW_STREAM("flow_stream"),
    ANNOUNCEMENT("announcement"),
    UNKNOWN("unknown"),
    ;

    companion object {
        private val byServerValue = entries.associateBy { it.serverValue }
        fun fromServer(raw: String?): MediaType? = raw?.let { byServerValue[it] }
    }
}
