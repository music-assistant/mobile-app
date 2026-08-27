package io.music_assistant.client.data.model.server.events

import io.music_assistant.client.data.model.server.EventType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

interface Event<T> {
    val event: EventType
    val objectId: String?
    val data: T?
}

/**
 * Event envelope, decoded first to resolve the concrete event shape.
 *
 * [eventType] is nullable with a `null` default on purpose: `myJson` sets
 * `coerceInputValues`, so an event kind this client does not model yet
 * coerces to `null` instead of throwing, and a malformed envelope with no
 * `event` key falls back to the same default.
 */
@Serializable
data class GenericEvent(
    @SerialName("event") val eventType: EventType? = null,
)
