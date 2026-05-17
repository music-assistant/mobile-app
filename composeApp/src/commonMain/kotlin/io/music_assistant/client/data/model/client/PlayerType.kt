package io.music_assistant.client.data.model.client

enum class PlayerType(val serverValue: String) {
    PLAYER("player"),
    GROUP("group"),
    STEREO_PAIR("stereo_pair"),
    ;

    companion object {
        private val byServerValue = entries.associateBy { it.serverValue }
        fun fromServer(raw: String?): PlayerType? = raw?.let { byServerValue[it] }
    }
}
