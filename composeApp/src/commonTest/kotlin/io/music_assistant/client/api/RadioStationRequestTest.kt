package io.music_assistant.client.api

import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

/** Pins the wire shape of the radio station commands. */
class RadioStationRequestTest {
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
}
