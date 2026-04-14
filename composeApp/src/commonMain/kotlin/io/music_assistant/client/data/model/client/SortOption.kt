package io.music_assistant.client.data.model.client

import io.music_assistant.client.data.model.server.MediaType

enum class SortField(val serverKey: String, val displayName: String) {
    NAME("sort_name", "Name"),
    DURATION("duration", "Duration"),
    DATE_ADDED("timestamp_added", "Date added"),
    DATE_MODIFIED("timestamp_modified", "Date modified"),
    LAST_PLAYED("last_played", "Last played"),
    PLAY_COUNT("play_count", "Play count"),
    YEAR("year", "Year"),
    POSITION("position", "Position"),
    ARTIST_NAME("artist_name", "Artist"),
}

data class SortOption(
    val field: SortField,
    val descending: Boolean = false,
) {
    fun toServerString(): String = if (descending) "${field.serverKey}_desc" else field.serverKey
}

object SortConfig {
    fun fieldsFor(mediaType: MediaType): List<SortField> = when (mediaType) {
        MediaType.ARTIST -> listOf(SortField.NAME, SortField.DATE_ADDED, SortField.LAST_PLAYED, SortField.PLAY_COUNT)
        MediaType.ALBUM -> listOf(SortField.NAME, SortField.ARTIST_NAME, SortField.YEAR, SortField.DATE_ADDED, SortField.LAST_PLAYED, SortField.PLAY_COUNT)
        MediaType.TRACK -> listOf(SortField.NAME, SortField.ARTIST_NAME, SortField.DURATION, SortField.YEAR, SortField.DATE_ADDED, SortField.LAST_PLAYED, SortField.PLAY_COUNT)
        MediaType.PLAYLIST -> listOf(SortField.NAME, SortField.DATE_ADDED, SortField.DATE_MODIFIED, SortField.LAST_PLAYED, SortField.PLAY_COUNT)
        MediaType.AUDIOBOOK -> listOf(SortField.NAME, SortField.DATE_ADDED, SortField.LAST_PLAYED, SortField.PLAY_COUNT)
        MediaType.PODCAST -> listOf(SortField.NAME, SortField.DATE_ADDED, SortField.DATE_MODIFIED, SortField.LAST_PLAYED, SortField.PLAY_COUNT)
        MediaType.RADIO -> listOf(SortField.NAME, SortField.DATE_ADDED, SortField.LAST_PLAYED, SortField.PLAY_COUNT)
        MediaType.GENRE -> listOf(SortField.NAME, SortField.DATE_ADDED, SortField.PLAY_COUNT)
        else -> listOf(SortField.NAME)
    }

    fun defaultFor(mediaType: MediaType): SortOption = when (mediaType) {
        MediaType.PODCAST -> SortOption(SortField.DATE_ADDED, descending = true)
        else -> SortOption(SortField.NAME)
    }
}
