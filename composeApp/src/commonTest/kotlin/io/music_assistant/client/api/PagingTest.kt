package io.music_assistant.client.api

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

/**
 * `library_items` and `player_queues/items` answer in one message, so every caller that wants the
 * whole list must walk it in server-sized pages instead of sending one huge `limit`.
 */
class PagingTest {
    @Test
    fun pagedOverwritesLimitAndOffsetAndKeepsOtherArgs() {
        val paged = Request.Track.list(favorite = true, orderBy = "name").paged(limit = 7, offset = 14)

        assertEquals(APICommands.MUSIC_TRACKS_LIBRARY_ITEMS, paged.command)
        assertEquals(JsonPrimitive(7), paged.args?.get("limit"))
        assertEquals(JsonPrimitive(14), paged.args?.get("offset"))
        assertEquals(JsonPrimitive(true), paged.args?.get("favorite"))
        assertEquals(JsonPrimitive("name"), paged.args?.get("order_by"))
    }

    @Test
    fun pagedIssuesAFreshMessageId() {
        val original = Request.Track.list()
        assertNotEquals(original.messageId, original.paged(1, 0).messageId)
    }

    @Test
    fun listBuildersDefaultToTheServerPageSize() {
        assertEquals(JsonPrimitive(SERVER_PAGE_SIZE), Request.Track.list().args?.get("limit"))
        assertEquals(JsonPrimitive(SERVER_PAGE_SIZE), Request.Queue.items("q").args?.get("limit"))
    }

    @Test
    fun fetchAllPagesWalksUntilAShortPage() = runTest {
        val offsets = mutableListOf<Int>()
        val result = Request.Track.list().fetchAllPages(pageSize = 2) { request ->
            val offset = request.args?.get("offset")?.let { (it as JsonPrimitive).content.toInt() } ?: 0
            offsets += offset
            when (offset) {
                0 -> listOf("a", "b")
                2 -> listOf("c", "d")
                else -> listOf("e")
            }
        }

        assertEquals(listOf("a", "b", "c", "d", "e"), result)
        assertEquals(listOf(0, 2, 4), offsets)
    }

    @Test
    fun fetchAllPagesStopsOnAnEmptyFirstPage() = runTest {
        var calls = 0
        val result = Request.Track.list().fetchAllPages(pageSize = 2) {
            calls++
            emptyList<String>()
        }

        assertEquals(emptyList(), result)
        assertEquals(1, calls)
    }

    @Test
    fun fetchAllPagesReturnsNullWhenTheFirstPageFails() = runTest {
        assertNull(Request.Track.list().fetchAllPages<String>(pageSize = 2) { null })
    }

    @Test
    fun fetchAllPagesKeepsCollectedItemsWhenALaterPageFails() = runTest {
        val result = Request.Track.list().fetchAllPages(pageSize = 1) { request ->
            if (request.args?.get("offset") == JsonPrimitive(0)) listOf("a") else null
        }

        assertEquals(listOf("a"), result)
    }

    @Test
    fun fetchAllPagesStopsAtTheDefensiveCeiling() = runTest {
        var calls = 0
        val result = Request.Track.list().fetchAllPages(pageSize = 1) {
            calls++
            listOf("x")
        }

        assertEquals(100, calls)
        assertEquals(100, result?.size)
    }
}
