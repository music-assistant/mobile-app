package io.music_assistant.client.data.model.server.events

import io.music_assistant.client.data.model.server.EventType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The played state or the resume position of an item changed. The server sends it when playback
 * stops or ends, and on a mark as played or unplayed. It goes to every user, so a client checks
 * [ServerPlaylogUpdate.userId] first.
 */
@Serializable
data class PlaylogUpdatedEvent(
    @SerialName("event") override val event: EventType,
    @SerialName("object_id") override val objectId: String? = null,
    @SerialName("data") override val data: ServerPlaylogUpdate,
) : Event<ServerPlaylogUpdate>

/** [userId] is the user that the change applies to, or null for all users. */
@Serializable
data class ServerPlaylogUpdate(
    @SerialName("userid") val userId: String? = null,
)
