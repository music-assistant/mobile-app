package io.music_assistant.client.data.model.server.events

import io.music_assistant.client.data.model.server.EventType
import io.music_assistant.client.data.model.server.ServerInfo
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Live push of the server's core state. [data] is the same payload as the `server/hello`
 * handshake, so it refreshes the cached [ServerInfo].
 *
 * The payload also carries a `status` (CoreState) field, the intended replacement for the
 * deprecated `application_shutdown` event. Nothing consumes it yet, so it is left unmodeled
 * and dropped by `ignoreUnknownKeys`.
 */
@Serializable
data class CoreStateUpdatedEvent(
    @SerialName("event") override val event: EventType,
    @SerialName("object_id") override val objectId: String? = null,
    @SerialName("data") override val data: ServerInfo,
) : Event<ServerInfo>
