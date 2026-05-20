package io.music_assistant.client.data.model.client

import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.client.items.Playlist

data class QueueInfo(
    val id: String,
    val available: Boolean,
    val currentIndex: Int?,
    val shuffleEnabled: Boolean,
    val repeatMode: RepeatMode?,
    val dontStopTheMusicEnabled: Boolean?,
    val elapsedTime: Double?,
    /**
     * Unix epoch seconds (UTC) when [elapsedTime] was last recomputed
     * server-side. Drives [isBefore]. Optimistic writes bump this above
     * the last known server stamp; see `LocalPlayerRepository`.
     */
    val elapsedTimeLastUpdated: Double?,
    val currentItem: QueueTrack?,
    val radioSource: List<AppMediaItem>,
) {
    val isRadioOn: Boolean = radioSource.isNotEmpty()
    val isDynamicPlaylist = radioSource.size == 1 &&
            (radioSource[0] as? Playlist)?.isDynamic == true
}

/** Strict-older-than on [QueueInfo.elapsedTimeLastUpdated]. Callers match ids first. */
fun QueueInfo.isBefore(other: QueueInfo): Boolean {
    val mine = elapsedTimeLastUpdated ?: return false
    val theirs = other.elapsedTimeLastUpdated ?: return false
    return mine < theirs
}
