package io.music_assistant.client.settings

/** Whether the audiobook library shows each series as one row or every book on its own. */
enum class SeriesGrouping {
    FLAT,
    GROUPED,
    ;

    fun toggled(): SeriesGrouping = when (this) {
        FLAT -> GROUPED
        GROUPED -> FLAT
    }
}
