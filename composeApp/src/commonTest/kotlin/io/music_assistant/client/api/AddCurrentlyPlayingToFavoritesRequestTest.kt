package io.music_assistant.client.api

import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

/** Pins the wire shape of the radio-favorite command against the server's api_command signature. */
class AddCurrentlyPlayingToFavoritesRequestTest {
    @Test
    fun carriesPlayerIdOnly() {
        val request = Request.Player.addCurrentlyPlayingToFavorites(playerId = "player-1")

        assertEquals("players/add_currently_playing_to_favorites", request.command)
        assertEquals(JsonPrimitive("player-1"), request.args?.get("player_id"))
        assertEquals(setOf("player_id"), request.args?.keys)
    }
}
