package io.music_assistant.client.auto

import io.music_assistant.client.data.model.client.MediaType
import io.music_assistant.client.data.model.client.items.RecommendationFolder
import io.music_assistant.client.ui.compose.library.LibraryCategory
import io.music_assistant.client.ui.compose.library.carTabCategories
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * AI Radio station ids share the sub-list separator with tab ids, so the parsers have to stay
 * mutually exclusive. A collision would route a station tap into the queue-play path, which
 * demands a media URI a station does not have.
 */
class AutoMediaIdsTest {
    @Test
    fun `station id round-trips`() {
        val encoded = MediaIds.aiRadioStationIdOf("morning_show")

        assertEquals("morning_show", MediaIds.aiRadioStationOf(encoded))
    }

    @Test
    fun `a non-station id is not decoded as one`() {
        assertNull(MediaIds.aiRadioStationOf(MediaIds.TAB_AI_RADIO))
        assertNull(MediaIds.aiRadioStationOf(MediaIds.ROOT))
        assertNull(MediaIds.aiRadioStationOf(MediaIds.subListIdOf(MediaType.ALBUM, AutoSubList.NEW)))
    }

    @Test
    fun `a station id is not parsed as a sub-list or a parent ref`() {
        val encoded = MediaIds.aiRadioStationIdOf("morning_show")

        assertNull(MediaIds.parseSubListId(encoded))
        assertNull(ParentRef.parse(encoded))
        assertNull(MediaIds.tabMediaTypeOf(encoded))
    }

    @Test
    fun `every car tab category has a tab id`() {
        val ids = carTabCategories.map { MediaIds.tabIdOf(it) }

        assertEquals(carTabCategories.size, ids.toSet().size)
        assertTrue(MediaIds.TAB_AI_RADIO in ids)
        assertEquals(MediaIds.TAB_AI_RADIO, MediaIds.tabIdOf(LibraryCategory.AI_RADIO))
    }

    @Test
    fun `home tab has its own tab id`() {
        assertEquals(MediaIds.TAB_HOME, MediaIds.tabIdOf(LibraryCategory.HOME))
        assertNull(MediaIds.tabMediaTypeOf(MediaIds.TAB_HOME))
        assertFalse(MediaIds.isHomeRowId(MediaIds.TAB_HOME))
    }

    @Test
    fun `a home row id is not parsed as a sub-list, a parent ref or a station`() {
        val encoded = MediaIds.homeRowIdOf(homeFolder(provider = "ytmusic", itemId = "mixed__for__you"))

        assertTrue(MediaIds.isHomeRowId(encoded))
        assertNull(MediaIds.parseSubListId(encoded))
        assertNull(ParentRef.parse(encoded))
        assertNull(MediaIds.aiRadioStationOf(encoded))
        assertNull(MediaIds.tabMediaTypeOf(encoded))
    }

    @Test
    fun `home row ids do not collide across field boundaries`() {
        assertNotEquals(
            MediaIds.homeRowIdOf(homeFolder(provider = "apple_music", itemId = "xyz")),
            MediaIds.homeRowIdOf(homeFolder(provider = "apple", itemId = "music_xyz")),
        )
    }

    private fun homeFolder(provider: String, itemId: String) = RecommendationFolder(
        itemId = itemId,
        provider = provider,
        name = itemId,
        uri = null,
        images = emptyMap(),
    )
}
