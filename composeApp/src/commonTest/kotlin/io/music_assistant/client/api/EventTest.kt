package io.music_assistant.client.api

import io.music_assistant.client.data.model.server.events.PlayerRemovedEvent
import io.music_assistant.client.data.model.server.events.PlayerUpdatedEvent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Guards the contract of [Event.event]: event deserialization is the one
 * place where *any* server-side schema drift reaches the app, because
 * events are pushed over the websocket rather than pulled by an RPC we
 * could defensively retry. An uncaught [SerializationException] here
 * would bubble to the websocket message-collector coroutine and abort the
 * process, so `event()` must:
 *
 *  - decode known event types normally,
 *  - return `null` for unknown event types,
 *  - return `null` when the payload shape for a known event type is wrong,
 *  - return `null` when the envelope itself fails to parse (no `event`
 *    field, non-string value, etc.) — including not throwing out of the
 *    `Event` constructor.
 */
class EventTest {
    private fun parse(raw: String): Event =
        Event(Json.parseToJsonElement(raw) as JsonObject)

    @Test
    fun decodesKnownEvent() {
        val raw = """{
            "event": "player_removed",
            "object_id": "pl1",
            "data": null
        }"""

        val decoded = parse(raw).event()

        assertNotNull(decoded)
        assertTrue(decoded is PlayerRemovedEvent)
        assertEquals("pl1", decoded.objectId)
    }

    @Test
    fun decodesPlayerUpdatedEventWithMinimalServerPlayer() {
        // The ServerPlayer inside PLAYER_UPDATED events carries only the
        // fields the server feels like sending on that update; all
        // non-identity fields fall back to defaults.
        val raw = """{
            "event": "player_updated",
            "object_id": "pl1",
            "data": {"player_id": "pl1"}
        }"""

        val decoded = parse(raw).event()

        assertNotNull(decoded)
        assertTrue(decoded is PlayerUpdatedEvent)
        assertEquals("pl1", decoded.data.playerId)
    }

    @Test
    fun decodesPlayerUpdatedEventWithUnknownPlayerType() {
        // `coerceInputValues` on [myJson] combined with ServerPlayer.type
        // being nullable with a null default means an unknown `type`
        // string degrades to `null` rather than aborting the decode.
        val raw = """{
            "event": "player_updated",
            "object_id": "pl1",
            "data": {"player_id": "pl1", "type": "some_new_server_type"}
        }"""

        val decoded = parse(raw).event()

        assertNotNull(decoded)
        assertTrue(decoded is PlayerUpdatedEvent)
        assertNull(decoded.data.type)
    }

    @Test
    fun returnsNullForUnknownEventType() {
        val raw = """{"event": "something_the_client_doesnt_know_about"}"""

        assertNull(parse(raw).event())
    }

    @Test
    fun returnsNullWhenEnvelopeMissingEventField() {
        // Envelope parse fails (`event` is required in GenericEvent). The
        // constructor must swallow that and leave `type = null` rather than
        // throwing at construction time.
        val raw = """{"object_id": "pl1"}"""

        assertNull(parse(raw).event())
    }

    @Test
    fun returnsNullWhenPayloadShapeIsWrong() {
        // Event type is valid, but `data` is a string where an object is
        // expected — the shape mismatch must be contained, not thrown.
        val raw = """{
            "event": "player_updated",
            "object_id": "pl1",
            "data": "not an object"
        }"""

        assertNull(parse(raw).event())
    }
}
