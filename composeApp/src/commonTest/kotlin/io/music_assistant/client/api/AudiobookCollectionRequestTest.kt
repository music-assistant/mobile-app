package io.music_assistant.client.api

import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Pins the wire shape of series grouping. A typo in `collapse_collections` fails silently: the
 * server drops unknown arguments into `**kwargs` and returns the flat book list.
 */
class AudiobookCollectionRequestTest {
    @Test
    fun groupedListingSendsCollapseCollections() {
        val request = Request.Audiobook.listLibrary(collapseCollections = true)

        assertEquals("music/audiobooks/library_items", request.command)
        assertEquals(JsonPrimitive(true), request.args?.get("collapse_collections"))
    }

    @Test
    fun flatListingOmitsCollapseCollections() {
        // Absent, not `false`: the request stays identical to the one older servers already accept.
        assertNull(Request.Audiobook.listLibrary().args?.get("collapse_collections"))
    }

    @Test
    fun getCollectionSendsOnlyTheItemId() {
        val request = Request.Audiobook.getCollection("audiobook___The Expanse")

        assertEquals("music/audiobooks/get_collection", request.command)
        assertEquals(setOf("item_id"), request.args?.keys)
        assertEquals(JsonPrimitive("audiobook___The Expanse"), request.args?.get("item_id"))
    }
}
