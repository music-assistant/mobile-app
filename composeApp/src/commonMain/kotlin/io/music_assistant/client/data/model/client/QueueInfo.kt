package io.music_assistant.client.data.model.client

import io.music_assistant.client.data.model.client.QueueTrack.Companion.toQueueTrack
import io.music_assistant.client.data.model.server.RepeatMode
import io.music_assistant.client.data.model.server.ServerQueue

data class QueueInfo(
    val id: String,
    val available: Boolean,
    val shuffleEnabled: Boolean,
    val repeatMode: RepeatMode?,
    val elapsedTime: Double?,
    val currentItem: QueueTrack?,
) {
    companion object Companion {
        fun ServerQueue.toQueue() = QueueInfo(
            id = queueId,
            available = available,
            shuffleEnabled = shuffleEnabled,
            repeatMode = repeatMode,
            elapsedTime = elapsedTime,
            currentItem = currentItem?.toQueueTrack(),
        )
    }
}
