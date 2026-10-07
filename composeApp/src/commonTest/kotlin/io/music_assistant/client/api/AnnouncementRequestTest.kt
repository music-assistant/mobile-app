package io.music_assistant.client.api

import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

/** Pins the announcement commands against the server's api_command signatures. */
class AnnouncementRequestTest {
    @Test
    fun `typed announcement leaves unset options to the player`() {
        val request = Request.Player.playAnnouncement(
            "player-1",
            "Dinner is ready",
            preAnnounce = null,
            volumeLevel = null,
        )

        assertEquals("players/cmd/play_announcement", request.command)
        assertEquals(setOf("player_id", "message"), request.args?.keys)
        assertEquals(JsonPrimitive("Dinner is ready"), request.args?.get("message"))
    }

    @Test
    fun `typed announcement carries chosen options`() {
        val request = Request.Player.playAnnouncement("player-1", "Hi", preAnnounce = false, volumeLevel = 40)

        assertEquals(JsonPrimitive(false), request.args?.get("pre_announce"))
        assertEquals(JsonPrimitive(40), request.args?.get("volume_level"))
    }

    @Test
    fun `chime setting reads the player's tts pre-announce value`() {
        val request = Request.Player.announcementChime("player-1")

        assertEquals("config/players/get_value", request.command)
        assertEquals(JsonPrimitive("tts_pre_announce"), request.args?.get("key"))
    }
}
