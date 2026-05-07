package io.music_assistant.client.settings

enum class ViewMode {
    GRID,
    LIST,
    ;

    fun toggled(): ViewMode = when (this) {
        GRID -> LIST
        LIST -> GRID
    }
}
