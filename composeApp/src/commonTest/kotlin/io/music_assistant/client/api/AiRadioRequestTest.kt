package io.music_assistant.client.api

import io.music_assistant.client.utils.myJson
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
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
    fun hostsListTakesNoArgs() {
        val request = Request.AiRadio.hosts()

        assertEquals("ai_radio/hosts/list", request.command)
        assertNull(request.args)
    }

    @Test
    fun queueDjStatusTakesNoArgs() {
        val request = Request.AiRadio.queueDjStatus()

        assertEquals("ai_radio/queue_dj/status", request.command)
        assertNull(request.args)
    }

    @Test
    fun setQueueDjCarriesQueueAndHost() {
        val request = Request.AiRadio.setQueueDj(queueId = "queue-1", hostId = "host-1")

        assertEquals("ai_radio/queue_dj/set", request.command)
        assertEquals(JsonPrimitive("queue-1"), request.args?.get("queue_id"))
        assertEquals(JsonPrimitive("host-1"), request.args?.get("host_id"))
        assertEquals(setOf("queue_id", "host_id"), request.args?.keys)
    }

    /**
     * Off is an explicit null: the server's `host_id` has no default, so a dropped key would
     * fail the call. Encoded the way `KtorServiceClient` puts a request on the wire.
     */
    @Test
    fun setQueueDjOffSendsAnExplicitNullHost() {
        val request = Request.AiRadio.setQueueDj(queueId = "queue-1", hostId = null)

        assertEquals(JsonNull, request.args?.get("host_id"))
        val wire = myJson.encodeToJsonElement(Request.serializer(), request).jsonObject
        assertEquals(JsonNull, wire["args"]?.jsonObject?.get("host_id"))
    }

    @Test
    fun radioTracksRequestCarriesStationAndProvider() {
        val request = Request.RadioStation.getTracks(
            itemId = "radio-1",
            providerInstanceIdOrDomain = "provider-1",
        )

        assertEquals("music/radios/radio_tracks", request.command)
        assertEquals(JsonPrimitive("radio-1"), request.args?.get("item_id"))
        assertEquals(
            JsonPrimitive("provider-1"),
            request.args?.get("provider_instance_id_or_domain"),
        )
        assertEquals(
            setOf("item_id", "provider_instance_id_or_domain"),
            request.args?.keys,
        )
    }

    @Test
    fun radioVersionsRequestCarriesStationAndProvider() {
        val request = Request.RadioStation.getVersions(
            itemId = "radio-1",
            providerInstanceIdOrDomain = "provider-1",
        )

        assertEquals("music/radios/radio_versions", request.command)
        assertEquals(JsonPrimitive("radio-1"), request.args?.get("item_id"))
        assertEquals(
            JsonPrimitive("provider-1"),
            request.args?.get("provider_instance_id_or_domain"),
        )
        assertEquals(
            setOf("item_id", "provider_instance_id_or_domain"),
            request.args?.keys,
        )
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
