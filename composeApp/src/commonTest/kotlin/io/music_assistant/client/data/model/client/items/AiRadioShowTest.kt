package io.music_assistant.client.data.model.client.items

import io.music_assistant.client.data.model.client.AppMediaItemFixtures
import io.music_assistant.client.data.model.server.ProviderMapping
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Pins which library items the Radio lists hide as AI Radio shows. */
class AiRadioShowTest {
    private fun radio(vararg mappings: ProviderMapping) = RadioStation(
        itemId = "7",
        provider = "library",
        name = "Late Night",
        providerMappings = mappings.toList(),
        metadata = null,
        favorite = null,
        uri = "library://radio/7",
        images = emptyMap(),
        version = null,
        isPlayable = true,
        isDynamic = false,
    )

    @Test
    fun aLibraryRowMappedToThePluginIsAShow() {
        assertTrue(radio(ProviderMapping("late_night", "ai_radio", "ai_radio")).isAiRadioShow)
    }

    @Test
    fun aNormalStationIsNotAShow() {
        assertFalse(radio(ProviderMapping("s1", "tunein", "tunein--abc")).isAiRadioShow)
        assertFalse(radio().isAiRadioShow)
    }

    @Test
    fun onlyRadioItemsCanBeShows() {
        assertFalse(AppMediaItemFixtures.playlist().isAiRadioShow)
    }
}
