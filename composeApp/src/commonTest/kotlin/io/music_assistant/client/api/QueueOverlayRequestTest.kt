package io.music_assistant.client.api

import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the wire shape of the overlay command against the server's api_command signature:
 * `player_queues/overlay(queue_id: str, enabled: bool | None, source: str | None, volume: int | None)`.
 * The server leaves an omitted argument unchanged, so a null must never reach the wire.
 */
class QueueOverlayRequestTest {
    @Test
    fun `turning off sends only the flag so the server keeps the sound and volume`() {
        val request = Request.Queue.setOverlay(queueId = "q1", enabled = false)

        assertEquals("player_queues/overlay", request.command)
        assertEquals(setOf("queue_id", "enabled"), request.args?.keys)
        assertEquals(JsonPrimitive(false), request.args?.get("enabled"))
    }

    @Test
    fun `turning on selects the sound and volume in the same call`() {
        val request = Request.Queue.setOverlay(
            queueId = "q1",
            enabled = true,
            source = "ambient_sounds://sound_effect/rain",
            volume = 80,
        )

        assertEquals(JsonPrimitive("q1"), request.args?.get("queue_id"))
        assertEquals(JsonPrimitive(true), request.args?.get("enabled"))
        assertEquals(JsonPrimitive("ambient_sounds://sound_effect/rain"), request.args?.get("source"))
        assertEquals(JsonPrimitive(80), request.args?.get("volume"))
    }

    @Test
    fun `the catalog request has no arguments`() {
        val request = Request.SoundEffects.all()

        assertEquals("music/sound_effects", request.command)
    }
}
