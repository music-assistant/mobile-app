package io.music_assistant.client.data.model.server.events

import io.music_assistant.client.data.model.client.QueueInfo.Companion.toQueue
import io.music_assistant.client.data.model.server.EventType
import io.music_assistant.client.data.model.server.ServerQueue
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class QueueItemsUpdatedEvent(
    @SerialName("event") override val event: EventType,
    @SerialName("object_id") override val objectId: String,
    @SerialName("data") override val data: ServerQueue,
) : Event<ServerQueue> {
    fun queue() = data.toQueue()
}
