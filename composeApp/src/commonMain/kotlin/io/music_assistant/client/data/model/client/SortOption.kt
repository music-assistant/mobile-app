package io.music_assistant.client.data.model.client

import io.music_assistant.client.data.model.client.items.Album
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.client.items.PlayableItem
import io.music_assistant.client.data.model.client.items.PodcastEpisode
import io.music_assistant.client.data.model.client.items.Track

/**
 * [serverKey] is the server 2.11 `sort_field` value, [legacyKey] the `order_by` key of older servers.
 * The random orders are absent on purpose: a paged list in a random order repeats items.
 */
enum class SortField(val serverKey: String, val displayName: String, val legacyKey: String = serverKey) {
    ORIGINAL("original", "Original"),
    NAME("sort_name", "Name"),
    EXACT_NAME("name", "Exact name"),
    DURATION("duration", "Duration"),
    DATE_ADDED("timestamp_added", "Date added"),
    DATE_MODIFIED("timestamp_modified", "Date modified"),
    DATE_FAVORITED("favorite_timestamp", "Date favorited"),
    LAST_PLAYED("last_played", "Last played"),
    PLAY_COUNT("play_count", "Play count"),
    YEAR("year", "Year"),
    POSITION("position", "Position"),
    ARTIST_NAME("artist_name", "Artist", legacyKey = "album_artist_name"),

    // Client-only: podcast episodes sort on the client.
    RELEASE_DATE("release_date", "Release date"),
    ;

    companion object {
        fun fromServerKey(key: String): SortField? = entries.firstOrNull { it.serverKey == key }
    }
}

data class SortOption(
    val field: SortField,
    val descending: Boolean = false,
) {
    fun toServerString(): String = if (descending) "${field.legacyKey}_desc" else field.legacyKey
}

/** A field that a sort chip offers, and the direction that a new pick of it starts in. */
data class SortOffer(
    val field: SortField,
    val defaultDescending: Boolean = false,
    val supportsDirection: Boolean = true,
)

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
        SubItemContext.RADIO_TRACKS -> listOf(SortField.ORIGINAL)
        SubItemContext.COLLECTION_ITEMS -> listOf(SortField.ORIGINAL)
        SubItemContext.PODCAST_EPISODES -> listOf(SortField.NAME, SortField.RELEASE_DATE, SortField.DURATION)
        SubItemContext.ARTIST_TOP_TRACKS -> listOf(SortField.ORIGINAL, SortField.NAME, SortField.DURATION)
        SubItemContext.ARTIST_ALL_ALBUMS,
        SubItemContext.ARTIST_LIBRARY_ALBUMS,
        -> listOf(SortField.ORIGINAL, SortField.NAME, SortField.ARTIST_NAME, SortField.YEAR)
        SubItemContext.ARTIST_AUDIOBOOKS -> listOf(SortField.ORIGINAL, SortField.NAME, SortField.DURATION)
    }

    /**
     * True when the user can actually change the sort for [context]. A single-field context shows
     * no chip (see ItemSortChip), so its order is fixed and must not be read from settings.
     */
    fun isUserSortable(context: SubItemContext): Boolean = fieldsFor(context).size > 1

    fun defaultFor(context: SubItemContext): SortOption = when (context) {
        SubItemContext.ARTIST_ALBUMS -> SortOption(SortField.YEAR, descending = true)
        SubItemContext.ALBUM_TRACKS -> SortOption(SortField.ORIGINAL)
        SubItemContext.RADIO_TRACKS -> SortOption(SortField.ORIGINAL)
        SubItemContext.PLAYLIST_ITEMS -> SortOption(SortField.ORIGINAL)
        SubItemContext.COLLECTION_ITEMS -> SortOption(SortField.ORIGINAL)
        SubItemContext.PODCAST_EPISODES -> SortOption(SortField.RELEASE_DATE, descending = true)
        SubItemContext.ARTIST_TOP_TRACKS,
        SubItemContext.ARTIST_ALL_ALBUMS,
        SubItemContext.ARTIST_LIBRARY_ALBUMS,
        SubItemContext.ARTIST_AUDIOBOOKS,
        -> SortOption(SortField.ORIGINAL)
        else -> SortOption(SortField.NAME)
    }
}

enum class SubItemContext {
    ARTIST_ALBUMS,
    ARTIST_TRACKS,
    ALBUM_TRACKS,
    PLAYLIST_ITEMS,
    RADIO_TRACKS,
    PODCAST_EPISODES,

    // Books of an audiobook series, fixed in the server's series order.
    COLLECTION_ITEMS,

    // Artist "View all" screens. Distinct from ARTIST_ALBUMS (Android Auto), so each list keeps
    // its own remembered sort.
    ARTIST_TOP_TRACKS,
    ARTIST_ALL_ALBUMS,
    ARTIST_LIBRARY_ALBUMS,

    // The audiobooks of an author or a narrator, in the library and on a provider.
    ARTIST_AUDIOBOOKS,
}

fun <T> List<T>.clientSorted(option: SortOption, context: SubItemContext? = null): List<T> {
    // Outside album and playlist tracks, ORIGINAL is the order the server delivered.
    val hasOriginalComparator = context == SubItemContext.ALBUM_TRACKS || context == SubItemContext.PLAYLIST_ITEMS
    if (option.field == SortField.ORIGINAL && !hasOriginalComparator) {
        return if (option.descending) reversed() else this
    }
    val comparator: Comparator<T> = when (option.field) {
        SortField.ORIGINAL -> if (context == SubItemContext.PLAYLIST_ITEMS) {
            compareBy<T, Int?>(nullsLast()) { (it as? PlayableItem)?.position }
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
