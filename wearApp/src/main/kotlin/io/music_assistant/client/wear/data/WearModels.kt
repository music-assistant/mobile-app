package io.music_assistant.client.wear.data

/** A player (or player group) as the watch shows it. */
data class WearPlayer(
    val id: String,
    val name: String,
    val isGroup: Boolean,
    val memberCount: Int,
    val isAvailable: Boolean,
    val isListed: Boolean,
    val isPlaying: Boolean,
    val queueId: String?,
    val canSetVolume: Boolean,
    val canNextPrevious: Boolean,
    /** Percent, 0..100. Null when the player exposes no volume at all. */
    val volume: Int?,
    val title: String?,
    val artist: String?,
    val imageUrl: String?,
    val duration: Double?,
) {
    /** A group commands its members' volume together, like the phone app's player card. */
    val usesGroupVolume: Boolean = memberCount > 0

    /** The player can be chosen on the watch: the server can reach it and it is not hidden. */
    val isSelectable: Boolean = isAvailable && isListed

    val subtitle: String? = listOfNotNull(artist, title).takeIf { it.isNotEmpty() }?.first()
}

/** One queue row. [id] is the server's `queue_item_id`, which play and remove both address. */
data class WearQueueItem(
    val id: String,
    val title: String,
    val artist: String?,
    val imageUrl: String?,
)

/** The playback state of one queue, without its items. */
data class WearQueue(
    val id: String,
    val currentItemId: String?,
    val currentIndex: Int,
    val itemCount: Int,
    val ended: Boolean,
    val elapsedSeconds: Double,
    val currentTitle: String?,
    val currentArtist: String?,
    val currentImageUrl: String?,
    val currentDuration: Double?,
) {
    /** Null once the queue has played to its end, so no row is marked as playing. */
    val activeItemId: String? = currentItemId.takeUnless { ended }
}

/** What the now-playing screen draws. */
data class NowPlaying(
    val title: String,
    val artist: String?,
    val imageUrl: String?,
    val isPlaying: Boolean,
    val positionSeconds: Double,
    val durationSeconds: Double?,
) {
    /** The fraction played at [seconds], clamped: a server can report past the end. */
    fun progressAt(seconds: Double): Float? = durationSeconds
        ?.takeIf { it > 0 }
        ?.let { (seconds / it).toFloat().coerceIn(0f, 1f) }

    /** The fraction played at the position the server last reported. */
    val progress: Float? = progressAt(positionSeconds)
}
