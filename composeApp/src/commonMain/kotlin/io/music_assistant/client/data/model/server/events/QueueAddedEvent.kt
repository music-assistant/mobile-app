package io.music_assistant.client.data.model.server.events

import io.music_assistant.client.data.model.client.QueueInfo.Companion.toQueue
import io.music_assistant.client.data.model.server.EventType
import io.music_assistant.client.data.model.server.ServerQueue
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Server announces a queue (typically fired when a new player connects to
 * MA and MA registers its queue). Payload is a full [ServerQueue]
 * including `current_item` if any, and is the first queue signal the
 * client receives for a newly-added queue.
 */
@Serializable
data class QueueAddedEvent(
    @SerialName("event") override val event: EventType,
    @SerialName("object_id") override val objectId: String? = null,
    @SerialName("data") override val data: ServerQueue,
) : Event<ServerQueue> {
    fun queue() = data.toQueue()
}
