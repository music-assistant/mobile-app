package io.music_assistant.client.data.model.server.events

import io.music_assistant.client.data.model.server.EventType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The provider list changed. The payload is not decoded: server 2.10 builds it without a user,
 * so it skips the provider filter of the user, which the `providers` command applies.
 */
@Serializable
data class ProvidersUpdatedEvent(
    @SerialName("event") override val event: EventType,
    @SerialName("object_id") override val objectId: String? = null,
) : Event<Unit> {
    override val data: Unit? get() = null
}
