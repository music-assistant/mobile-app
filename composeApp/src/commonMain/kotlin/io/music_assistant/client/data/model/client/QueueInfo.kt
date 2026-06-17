package io.music_assistant.client.data.model.client

import io.music_assistant.client.data.model.client.items.AppMediaItem

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
     * the last known server stamp; see `LocalPlayerController`.
     */
    val elapsedTimeLastUpdated: Double?,
    val currentItem: QueueTrack?,
    val radioSource: List<AppMediaItem>,
    /** Server-derived: the active source is a dynamic/smart playlist (rule-generated). */
    val isDynamicPlaylist: Boolean = false,
    val playbackSpeed: Double? = null,
) {
    val isRadioOn: Boolean = radioSource.isNotEmpty()
}

/** Strict-older-than on [QueueInfo.elapsedTimeLastUpdated]. Callers match ids first. */
fun QueueInfo.isBefore(other: QueueInfo): Boolean {
    val mine = elapsedTimeLastUpdated ?: return false
    val theirs = other.elapsedTimeLastUpdated ?: return false
    return mine < theirs
}
