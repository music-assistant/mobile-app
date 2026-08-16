package io.music_assistant.client.data.model.client

import io.music_assistant.client.data.model.client.items.Album
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.client.items.PlayableItem
import io.music_assistant.client.data.model.client.items.PodcastEpisode
import io.music_assistant.client.data.model.client.items.Track

enum class SortField(val serverKey: String, val displayName: String) {
    ORIGINAL("original", "Original"),
    NAME("sort_name", "Name"),
    DURATION("duration", "Duration"),
    DATE_ADDED("timestamp_added", "Date added"),
    DATE_MODIFIED("timestamp_modified", "Date modified"),
    LAST_PLAYED("last_played", "Last played"),
    PLAY_COUNT("play_count", "Play count"),
    YEAR("year", "Year"),
    POSITION("position", "Position"),
    ARTIST_NAME("artist_name", "Artist"),
    RELEASE_DATE("release_date", "Release date"),
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
        MediaType.ALBUM -> listOf(
            SortField.NAME,
            SortField.ARTIST_NAME,
            SortField.YEAR,
            SortField.DATE_ADDED,
            SortField.LAST_PLAYED,
            SortField.PLAY_COUNT,
        )
        MediaType.TRACK -> listOf(
            SortField.NAME,
            SortField.DURATION,
            SortField.DATE_ADDED,
            SortField.LAST_PLAYED,
            SortField.PLAY_COUNT,
        )
        MediaType.PLAYLIST -> listOf(
            SortField.NAME,
            SortField.DATE_ADDED,
            SortField.DATE_MODIFIED,
            SortField.LAST_PLAYED,
            SortField.PLAY_COUNT,
        )
        MediaType.AUDIOBOOK -> listOf(SortField.NAME, SortField.DATE_ADDED, SortField.LAST_PLAYED, SortField.PLAY_COUNT)
        MediaType.PODCAST -> listOf(
            SortField.NAME,
            SortField.DATE_ADDED,
            SortField.DATE_MODIFIED,
            SortField.LAST_PLAYED,
            SortField.PLAY_COUNT,
        )
        MediaType.RADIO -> listOf(SortField.NAME, SortField.DATE_ADDED, SortField.LAST_PLAYED, SortField.PLAY_COUNT)
        MediaType.GENRE -> listOf(SortField.NAME, SortField.DATE_ADDED, SortField.PLAY_COUNT)
        else -> listOf(SortField.NAME)
    }

    fun defaultFor(mediaType: MediaType): SortOption = when (mediaType) {
        MediaType.PODCAST -> SortOption(SortField.DATE_ADDED, descending = true)
        else -> SortOption(SortField.NAME)
    }

    fun fieldsFor(context: SubItemContext): List<SortField> = when (context) {
        SubItemContext.ARTIST_ALBUMS -> listOf(SortField.NAME, SortField.YEAR)
        SubItemContext.ARTIST_TRACKS -> listOf(SortField.NAME, SortField.DURATION)
        SubItemContext.ALBUM_TRACKS -> listOf(SortField.ORIGINAL)
        SubItemContext.PLAYLIST_ITEMS -> listOf(SortField.ORIGINAL)
        SubItemContext.PODCAST_EPISODES -> listOf(SortField.NAME, SortField.RELEASE_DATE, SortField.DURATION)
    }

    fun defaultFor(context: SubItemContext): SortOption = when (context) {
        SubItemContext.ARTIST_ALBUMS -> SortOption(SortField.YEAR, descending = true)
        SubItemContext.ALBUM_TRACKS -> SortOption(SortField.ORIGINAL)
        SubItemContext.PLAYLIST_ITEMS -> SortOption(SortField.ORIGINAL)
        SubItemContext.PODCAST_EPISODES -> SortOption(SortField.RELEASE_DATE, descending = true)
        else -> SortOption(SortField.NAME)
    }
}

enum class SubItemContext {
    ARTIST_ALBUMS,
    ARTIST_TRACKS,
    ALBUM_TRACKS,
    PLAYLIST_ITEMS,
    PODCAST_EPISODES,
}

fun <T> List<T>.clientSorted(option: SortOption, context: SubItemContext? = null): List<T> {
    val comparator: Comparator<T> = when (option.field) {
        SortField.ORIGINAL -> if (context == SubItemContext.PLAYLIST_ITEMS) {
            compareBy<T, Int?>(nullsLast()) { (it as? Track)?.position }
        } else {
            compareBy<T, Int?>(nullsLast()) {
                (it as? Track)?.discNumber
            }.thenBy(nullsLast()) {
                (it as? Track)?.trackNumber
            }
        }
        SortField.NAME -> compareBy(String.CASE_INSENSITIVE_ORDER) {
            (
                (it as? AppMediaItem)?.sortName ?: (it as? AppMediaItem)?.displayName
                ?: (it as? PlayableItem)?.displayName
            ).orEmpty()
        }
        SortField.DURATION -> compareBy { (it as? PlayableItem)?.duration ?: 0.0 }
        SortField.YEAR -> compareBy { (it as? Album)?.year ?: 0 }
        SortField.RELEASE_DATE -> compareBy(String.CASE_INSENSITIVE_ORDER) {
            (it as? PodcastEpisode)?.releaseDate.orEmpty()
        }
        SortField.ARTIST_NAME -> compareBy(String.CASE_INSENSITIVE_ORDER) {
            (
                (it as? Track)?.artists?.firstOrNull()?.displayName
                ?: (it as? Album)?.artists?.firstOrNull()?.displayName
            ).orEmpty()
        }
        else -> compareBy(String.CASE_INSENSITIVE_ORDER) {
            (
                (it as? AppMediaItem)?.sortName ?: (it as? AppMediaItem)?.displayName
                ?: (it as? PlayableItem)?.displayName
            ).orEmpty()
        }
    }
    return if (option.descending) sortedWith(comparator.reversed()) else sortedWith(comparator)
}
