package io.music_assistant.client.data.model.client

import io.music_assistant.client.data.model.client.items.AppMediaItem

data class QueueInfo(
    val id: String,
    val available: Boolean,
    val currentIndex: Int?,
    val shuffleEnabled: Boolean,
    val repeatMode: RepeatMode?,
    val autoPlayEnabled: Boolean?,
    val elapsedTime: Double?,
    /**
     * Unix epoch seconds (UTC) when [elapsedTime] was last recomputed
     * server-side. Drives [isBefore]. Optimistic writes bump this above
     * the last known server stamp; see `LocalPlayerController`.
     */
    val elapsedTimeLastUpdated: Double?,
    val currentItem: QueueTrack?,
    /**
     * Legacy `radio_source`. Servers from 2.10 on always serialize this as an empty list
     * (radio mode became `sources` + `is_dynamic`), so it is NOT a usable signal — do not
     * derive "radio is on" from it.
     */
    val radioSource: List<AppMediaItem>,
    /**
     * Crossfade on the queue, or null when the server does not support the feature.
     * Nullability is the gate for the badge and the menu entry.
     */
    val crossfadeEnabled: Boolean? = null,
    /** Server-derived: the active source is a dynamic/smart playlist (rule-generated). */
    val isDynamicPlaylist: Boolean = false,
    val playbackSpeed: Double? = null,
)

/** Strict-older-than on [QueueInfo.elapsedTimeLastUpdated]. Callers match ids first. */
fun QueueInfo.isBefore(other: QueueInfo): Boolean {
    val mine = elapsedTimeLastUpdated ?: return false
    val theirs = other.elapsedTimeLastUpdated ?: return false
    return mine < theirs
}
