package io.music_assistant.client.data.model.server.events

import io.music_assistant.client.data.model.server.EventType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The favorite state of the connected user changed on a library item (server 2.11 and newer).
 * The server sends it to that user only.
 */
@Serializable
data class FavoriteUpdatedEvent(
    @SerialName("event") override val event: EventType,
    @SerialName("object_id") override val objectId: String? = null,
    @SerialName("data") override val data: ServerFavoriteUpdate,
) : Event<ServerFavoriteUpdate>

/** True is a like, false is a dislike, null is no choice. [uri] is the library item. */
@Serializable
data class ServerFavoriteUpdate(
    @SerialName("uri") val uri: String,
    @SerialName("favorite") val favorite: Boolean? = null,
)
