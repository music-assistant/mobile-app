package io.music_assistant.client.api

import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Pins the wire shape of `music/playlists/playlist_tracks`. `force_refresh` is the only
 * cache-bypass argument the server exposes for a listing, and it is what makes a provider
 * playlist ("Random Album") produce a new selection on demand (issue #958).
 *
 * A typo in the argument name fails silently — the server ignores it and serves the cached
 * list, so the refresh button would just re-render the same tracks.
 */
class PlaylistTracksRequestTest {
    @Test
    fun refreshCarriesForceRefreshFlag() {
        val request = Request.Playlist.getTracks(
            itemId = "p1",
            providerInstanceIdOrDomain = "spotify",
            forceRefresh = true,
        )

        assertEquals("music/playlists/playlist_tracks", request.command)
        assertEquals(JsonPrimitive("p1"), request.args?.get("item_id"))
        assertEquals(
            JsonPrimitive("spotify"),
            request.args?.get("provider_instance_id_or_domain"),
        )
        assertEquals(JsonPrimitive(true), request.args?.get("force_refresh"))
    }

    @Test
    fun normalLoadOmitsForceRefresh() {
        // Absent, not `false`: the server default stays authoritative and the cached list is used.
        val request = Request.Playlist.getTracks(
            itemId = "p1",
            providerInstanceIdOrDomain = "spotify",
        )

        assertNull(request.args?.get("force_refresh"))
        assertEquals(
            setOf("item_id", "provider_instance_id_or_domain"),
            request.args?.keys,
        )
    }
}
