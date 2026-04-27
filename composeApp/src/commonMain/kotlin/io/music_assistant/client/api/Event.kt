// Log-payload truncation length is a debugging aid, not a protocol value.
@file:Suppress("MagicNumber")

package io.music_assistant.client.api

import co.touchlab.kermit.Logger
import io.music_assistant.client.data.model.server.EventType
import io.music_assistant.client.data.model.server.events.Event
import io.music_assistant.client.data.model.server.events.GenericEvent
import io.music_assistant.client.data.model.server.events.MediaItemAddedEvent
import io.music_assistant.client.data.model.server.events.MediaItemDeletedEvent
import io.music_assistant.client.data.model.server.events.MediaItemPlayedEvent
import io.music_assistant.client.data.model.server.events.MediaItemUpdatedEvent
import io.music_assistant.client.data.model.server.events.PlayerAddedEvent
import io.music_assistant.client.data.model.server.events.PlayerRemovedEvent
import io.music_assistant.client.data.model.server.events.PlayerUpdatedEvent
import io.music_assistant.client.data.model.server.events.QueueItemsUpdatedEvent
import io.music_assistant.client.data.model.server.events.QueueTimeUpdatedEvent
import io.music_assistant.client.data.model.server.events.QueueUpdatedEvent
import io.music_assistant.client.utils.myJson
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

private val logger = Logger.withTag("Event")

data class Event(
    val json: JsonObject,
) {
    private val type: EventType? = runCatching {
        parseEventType(myJson.decodeFromJsonElement<GenericEvent>(json).eventType)
    }.getOrElse { e ->
        logger.w(e) { "Failed to decode event envelope: ${json.toString().take(500)}" }
        null
    }

    /**
     * Decode the server event into its concrete shape. Returns `null` for
     * event types we don't currently model and — critically — also when the
     * payload fails to deserialize. An uncaught [SerializationException]
     * here would bubble to the websocket message collector and abort the
     * Kotlin/Native process, so one malformed event must not take the app
     * down with it.
     */
    @Suppress("MemberNameEqualsClassName") // TODO: rename to `decode()` (touches several callers)
    fun event(): Event<out Any>? = try {
        when (type) {
            EventType.PLAYER_ADDED -> myJson.decodeFromJsonElement<PlayerAddedEvent>(json)
            EventType.PLAYER_REMOVED -> myJson.decodeFromJsonElement<PlayerRemovedEvent>(json)
            EventType.PLAYER_UPDATED -> myJson.decodeFromJsonElement<PlayerUpdatedEvent>(json)
            EventType.MEDIA_ITEM_UPDATED -> myJson.decodeFromJsonElement<MediaItemUpdatedEvent>(json)
            EventType.MEDIA_ITEM_ADDED -> myJson.decodeFromJsonElement<MediaItemAddedEvent>(json)
            EventType.MEDIA_ITEM_DELETED -> myJson.decodeFromJsonElement<MediaItemDeletedEvent>(json)
            EventType.MEDIA_ITEM_PLAYED -> myJson.decodeFromJsonElement<MediaItemPlayedEvent>(json)
            EventType.QUEUE_UPDATED -> myJson.decodeFromJsonElement<QueueUpdatedEvent>(json)
            EventType.QUEUE_TIME_UPDATED -> myJson.decodeFromJsonElement<QueueTimeUpdatedEvent>(json)
            EventType.QUEUE_ITEMS_UPDATED -> myJson.decodeFromJsonElement<QueueItemsUpdatedEvent>(json)
            EventType.PLAYER_SETTINGS_UPDATED,
            EventType.QUEUE_ADDED,
            EventType.QUEUE_SETTINGS_UPDATED,
            EventType.SHUTDOWN,
            EventType.PROVIDERS_UPDATED,
            EventType.PLAYER_CONFIG_UPDATED,
            EventType.SYNC_TASKS_UPDATED,
            EventType.TASKS_UPDATED,
            EventType.AUTH_SESSION,
            EventType.CONNECTED,
            EventType.DISCONNECTED,
            EventType.ALL,
            null,
            -> {
                logger.w { "Unparsed event: $json" }
                null
            }
        }
    } catch (e: SerializationException) {
        logger.w(e) {
            "Failed to decode $type event (payload shape drift?): " +
                    json.toString().take(500)
        }
        null
    }

    private fun parseEventType(value: String): EventType? =
        EventType.entries.find { it.name.equals(value, ignoreCase = true) }
}
