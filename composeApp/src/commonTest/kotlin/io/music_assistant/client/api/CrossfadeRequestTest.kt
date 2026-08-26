package io.music_assistant.client.api

import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the wire shape of the crossfade command against the server's api_command signature:
 * `player_queues/crossfade(queue_id: str, crossfade_enabled: bool)`.
 *
 * A typo in either name fails silently at runtime — the server rejects the call and the
 * badge simply never changes — so it has to be caught here.
 */
class CrossfadeRequestTest {
    @Test
    fun enableCarriesQueueIdAndFlag() {
        val request = Request.Queue.setCrossfade(queueId = "q1", enabled = true)

        assertEquals("player_queues/crossfade", request.command)
        assertEquals(JsonPrimitive("q1"), request.args?.get("queue_id"))
        assertEquals(JsonPrimitive(true), request.args?.get("crossfade_enabled"))
        assertEquals(setOf("queue_id", "crossfade_enabled"), request.args?.keys)
    }

    @Test
    fun disableSendsFalseRatherThanOmittingTheFlag() {
        // The server command is an absolute setter, not a toggle.
        val request = Request.Queue.setCrossfade(queueId = "q1", enabled = false)

        assertEquals(JsonPrimitive(false), request.args?.get("crossfade_enabled"))
    }
}
