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
     * the last known server stamp; see `LocalPlayerAdapter`.
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
    /** Ambient sound overlay, or null when the server does not support the feature. */
    val overlay: QueueOverlay? = null,
    /** The server runs a play action on this queue, started by any client. */
    val playActionInProgress: Boolean = false,
    /** Number of items in the queue, or null when the server did not send it. */
    val itemCount: Int? = null,
)

/**
 * Whether a plain `next` moves the queue. The server ignores it without a current item, and on
 * the last item unless repeat is ALL (`player_queues/next`). An unknown count counts as a move.
 */
val QueueInfo.nextMovesQueue: Boolean
    get() {
        val index = currentIndex ?: return false
        val count = itemCount ?: return true
        return index < count - 1 || repeatMode == RepeatMode.ALL
    }

/**
 * A looping sound effect mixed into the queue stream by the server. [sourceUri] and
 * [sourceName] keep the last selected sound while [enabled] is false. [volume] is relative
 * to the music in percent (100 = equally loud).
 */
data class QueueOverlay(
    val enabled: Boolean,
    val sourceUri: String?,
    val sourceName: String?,
    val volume: Int,
) {
    companion object {
        /** The server default: the sound plays as loud as the music. */
        const val DEFAULT_VOLUME = 100
    }
}

/** Strict-older-than on [QueueInfo.elapsedTimeLastUpdated]. Callers match ids first. */
fun QueueInfo.isBefore(other: QueueInfo): Boolean {
    val mine = elapsedTimeLastUpdated ?: return false
    val theirs = other.elapsedTimeLastUpdated ?: return false
    return mine < theirs
}
