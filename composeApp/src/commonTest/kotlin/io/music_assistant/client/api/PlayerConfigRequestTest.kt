package io.music_assistant.client.api

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals

/** Pins the player config commands against the server's api_command signatures. */
class PlayerConfigRequestTest {
    @Test
    fun `config reads the full config of one player`() {
        val request = Request.Player.config("player-1")

        assertEquals("config/players/get", request.command)
        assertEquals(setOf("player_id"), request.args?.keys)
        assertEquals(JsonPrimitive("player-1"), request.args?.get("player_id"))
    }

    @Test
    fun `save sends one value under its key`() {
        val request = Request.Player.saveConfigValue("player-1", "sendspin_static_delay", 120)

        assertEquals("config/players/save", request.command)
        assertEquals(setOf("player_id", "values"), request.args?.keys)
        assertEquals(
            buildJsonObject { put("sendspin_static_delay", JsonPrimitive(120)) },
            request.args?.get("values"),
        )
    }
}
