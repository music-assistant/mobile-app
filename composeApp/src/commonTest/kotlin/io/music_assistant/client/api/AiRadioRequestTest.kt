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

    /**
     * By station, never by session. The server ignores station_id whenever a session_id is
     * present, so sending both would reintroduce the stale-session failure this avoids.
     */
    @Test
    fun stopTargetsTheStationRatherThanASession() {
        val request = Request.AiRadio.stop(stationId = "late-night")

        assertEquals("ai_radio/stop", request.command)
        assertEquals(JsonPrimitive("late-night"), request.args?.get("station_id"))
        assertEquals(setOf("station_id"), request.args?.keys)
    }

    @Test
    fun statusTakesNoArgs() {
        val request = Request.AiRadio.status()

        assertEquals("ai_radio/status", request.command)
        assertNull(request.args)
    }

    /**
     * Not an ai_radio command, but the one AI Radio leans on for its row artwork: a station has
     * no image, so its source playlist is fetched for one. Pinned here so a rename of the shared
     * builder shows up as an AI Radio failure too.
     */
    @Test
    fun sourcePlaylistLookupUsesTheLibraryGetCommand() {
        val request = Request.Playlist.get(
            itemId = "playlist-1",
            providerInstanceIdOrDomain = "library",
        )

        assertEquals("music/playlists/get", request.command)
        assertEquals(JsonPrimitive("playlist-1"), request.args?.get("item_id"))
        assertEquals(
            JsonPrimitive("library"),
            request.args?.get("provider_instance_id_or_domain"),
        )
    }
}
