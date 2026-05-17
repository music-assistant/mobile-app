package io.music_assistant.client.data.model.client

enum class RepeatMode(val serverValue: String) {
    OFF("off"),
    ONE("one"),
    ALL("all"),
    ;

    companion object {
        private val byServerValue = entries.associateBy { it.serverValue }
        fun fromServer(raw: String?): RepeatMode? = raw?.let { byServerValue[it] }
    }
}
