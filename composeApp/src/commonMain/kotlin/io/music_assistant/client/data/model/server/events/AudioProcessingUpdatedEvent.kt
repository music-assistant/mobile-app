package io.music_assistant.client.data.model.server.events

import io.music_assistant.client.data.model.server.AudioProcessingChain
import io.music_assistant.client.data.model.server.EventType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AudioProcessingUpdatedEvent(
    @SerialName("event") override val event: EventType,
    @SerialName("object_id") override val objectId: String? = null,
    @SerialName("data") override val data: AudioProcessingChain? = null,
) : Event<AudioProcessingChain>
