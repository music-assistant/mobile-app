package io.music_assistant.client.data.model.client

enum class QueueOption(val serverValue: String) {
    PLAY("play"),
    REPLACE("replace"),
    NEXT("next"),
    ADD("add"),
    ;

    companion object {
        private val byServerValue = entries.associateBy { it.serverValue }
        fun fromServer(raw: String?): QueueOption? = raw?.let { byServerValue[it] }
    }
}
