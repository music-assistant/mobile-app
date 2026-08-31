package io.music_assistant.client.api

import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Pins the wire shape of the ai_radio plugin commands against the provider's signatures. */
class AiRadioRequestTest {
    @Test
    fun stationsListTakesNoArgs() {
        val request = Request.AiRadio.stations()

        assertEquals("ai_radio/stations/list", request.command)
        assertNull(request.args)
    }

    @Test
    fun startCarriesStationAndPlayerOverride() {
        val request = Request.AiRadio.start(stationId = "late-night", playerId = "player-1")

        assertEquals("ai_radio/start", request.command)
        assertEquals(JsonPrimitive("late-night"), request.args?.get("station_id"))
        // The server resolves this through players.get_player, so it must be the player id
        // and must travel under player_id_override rather than as a queue id.
        assertEquals(JsonPrimitive("player-1"), request.args?.get("player_id_override"))
        assertEquals(setOf("station_id", "player_id_override"), request.args?.keys)
    }

    @Test
    fun stopCarriesSessionIdOnly() {
        val request = Request.AiRadio.stop(sessionId = "session-1")

        assertEquals("ai_radio/stop", request.command)
        assertEquals(JsonPrimitive("session-1"), request.args?.get("session_id"))
        assertEquals(setOf("session_id"), request.args?.keys)
    }

    @Test
    fun statusTakesNoArgs() {
        val request = Request.AiRadio.status()

        assertEquals("ai_radio/status", request.command)
        assertNull(request.args)
    }
}
