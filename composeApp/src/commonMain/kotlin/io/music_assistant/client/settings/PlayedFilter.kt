package io.music_assistant.client.settings

/** Whether the episodes of a podcast include the ones the user played to the end. */
enum class PlayedFilter {
    ALL,
    HIDE_PLAYED,
    ;

    fun toggled(): PlayedFilter = when (this) {
        ALL -> HIDE_PLAYED
        HIDE_PLAYED -> ALL
    }
}
