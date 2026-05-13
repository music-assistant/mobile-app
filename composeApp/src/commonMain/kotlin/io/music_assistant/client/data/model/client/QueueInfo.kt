package io.music_assistant.client.data.model.client

import io.music_assistant.client.data.model.client.AppMediaItem.Companion.toAppMediaItemList
import io.music_assistant.client.data.model.client.QueueTrack.Companion.toQueueTrack
import io.music_assistant.client.data.model.server.RepeatMode
import io.music_assistant.client.data.model.server.ServerQueue

data class QueueInfo(
    val id: String,
    val available: Boolean,
    val currentIndex: Int?,
    val shuffleEnabled: Boolean,
    val repeatMode: RepeatMode?,
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
    val isDynamic = radioSource.size == 1 &&
            (radioSource[0] as? AppMediaItem.Playlist)?.isDynamic == true

    companion object Companion {
        fun ServerQueue.toQueue() = QueueInfo(
            id = queueId,
            available = available,
            currentIndex = currentIndex,
            shuffleEnabled = shuffleEnabled,
            repeatMode = repeatMode,
            elapsedTime = elapsedTime,
            elapsedTimeLastUpdated = elapsedTimeLastUpdated,
            currentItem = currentItem?.toQueueTrack(),
            radioSource = radioSource?.toAppMediaItemList() ?: emptyList(),
        )
    }
}

/** Strict-older-than on [QueueInfo.elapsedTimeLastUpdated]. Callers match ids first. */
fun QueueInfo.isBefore(other: QueueInfo): Boolean {
    val mine = elapsedTimeLastUpdated ?: return false
    val theirs = other.elapsedTimeLastUpdated ?: return false
    return mine < theirs
}
