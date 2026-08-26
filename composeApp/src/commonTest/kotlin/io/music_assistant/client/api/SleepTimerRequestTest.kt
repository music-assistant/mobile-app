package io.music_assistant.client.api

import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

/** Pins the wire shape of the sleep-timer commands against the server's api_command signatures. */
class SleepTimerRequestTest {
    @Test
    fun setCarriesPlayerIdAndSeconds() {
        val request = Request.Player.setSleepTimer(playerId = "player-1", seconds = 900)

        assertEquals("players/sleep_timer/set", request.command)
        assertEquals(JsonPrimitive("player-1"), request.args?.get("player_id"))
        assertEquals(JsonPrimitive(900), request.args?.get("seconds"))
    }

    @Test
    fun clearCarriesPlayerIdOnly() {
        val request = Request.Player.clearSleepTimer(playerId = "player-1")

        assertEquals("players/sleep_timer/clear", request.command)
        assertEquals(JsonPrimitive("player-1"), request.args?.get("player_id"))
        assertEquals(setOf("player_id"), request.args?.keys)
    }
}
