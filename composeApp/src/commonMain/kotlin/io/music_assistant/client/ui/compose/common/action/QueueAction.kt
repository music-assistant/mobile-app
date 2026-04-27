package io.music_assistant.client.ui.compose.common.action

sealed interface QueueAction {
    data class RemoveItems(
        val queueId: String,
        val items: List<String>,
    ) : QueueAction

    data class ClearQueue(
        val queueId: String,
    ) : QueueAction

    data class PlayQueueItem(
        val queueId: String,
        val queueItemId: String,
    ) : QueueAction

    data class MoveItem(
        val queueId: String,
        val queueItemId: String,
        val from: Int,
        val to: Int,
    ) : QueueAction

    data class Transfer(
        val sourceId: String,
        val targetId: String,
        val autoplay: Boolean,
    ) : QueueAction
}
