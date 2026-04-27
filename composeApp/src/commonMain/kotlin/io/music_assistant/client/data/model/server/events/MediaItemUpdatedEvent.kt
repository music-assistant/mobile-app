package io.music_assistant.client.data.model.server.events

import io.music_assistant.client.data.model.server.EventType
import io.music_assistant.client.data.model.server.ServerMediaItem
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MediaItemUpdatedEvent(
    @SerialName("event") override val event: EventType,
    @SerialName("object_id") override val objectId: String,
    @SerialName("data") override val data: ServerMediaItem,
) : Event<ServerMediaItem>
