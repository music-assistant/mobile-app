package io.music_assistant.client.data.model.server.events

import io.music_assistant.client.data.model.client.Player.Companion.toPlayer
import io.music_assistant.client.data.model.server.EventType
import io.music_assistant.client.data.model.server.ServerPlayer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PlayerUpdatedEvent(
    @SerialName("event") override val event: EventType,
    @SerialName("object_id") override val objectId: String? = null,
    @SerialName("data") override val data: ServerPlayer,
) : Event<ServerPlayer> {
    fun player() = data.toPlayer()
}
