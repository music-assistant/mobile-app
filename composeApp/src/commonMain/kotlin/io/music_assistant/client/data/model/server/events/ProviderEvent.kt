package io.music_assistant.client.data.model.server.events

import io.music_assistant.client.data.model.server.EventType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Free-form event of a provider instance, named by [objectId]. The payload shape belongs to that
 * provider, so it stays raw here and each consumer reads its own keys.
 */
@Serializable
data class ProviderEvent(
    @SerialName("event") override val event: EventType,
    @SerialName("object_id") override val objectId: String? = null,
    @SerialName("data") override val data: JsonObject? = null,
) : Event<JsonObject>
