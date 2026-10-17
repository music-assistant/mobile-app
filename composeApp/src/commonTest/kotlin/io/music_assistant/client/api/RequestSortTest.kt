package io.music_assistant.client.api

import io.music_assistant.client.data.model.client.SortField
import io.music_assistant.client.data.model.client.SortOption
import io.music_assistant.client.data.model.server.ListSort
import io.music_assistant.client.data.model.server.SERVER_2_11_SCHEMA
import io.music_assistant.client.data.model.server.SortWire
import io.music_assistant.client.data.model.server.sortWireFor
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Server 2.11 deprecates `order_by` for `sort_field` and `sort_direction`. Server 2.10 ignores the
 * new arguments and sorts by sort name, so the wire must follow the server schema.
 */
class RequestSortTest {
    private val artistDesc = SortOption(SortField.ARTIST_NAME, descending = true)

    @Test
    fun `the 2_11 schema selects sort_field and an older or unknown schema selects order_by`() {
        assertEquals(SortWire.SORT_FIELD, sortWireFor(SERVER_2_11_SCHEMA))
        assertEquals(SortWire.ORDER_BY, sortWireFor(SERVER_2_11_SCHEMA - 1))
        assertEquals(SortWire.ORDER_BY, sortWireFor(null))
    }

    @Test
    fun `order_by carries the legacy key`() {
        val args = Request.Album.listLibrary(sort = ListSort(artistDesc, SortWire.ORDER_BY)).args

        assertEquals(JsonPrimitive("album_artist_name_desc"), args?.get("order_by"))
        assertNull(args?.get("sort_field"))
        assertNull(args?.get("sort_direction"))
    }

    @Test
    fun `sort_field carries the 2_11 field and the direction`() {
        val args = Request.Album.listLibrary(sort = ListSort(artistDesc, SortWire.SORT_FIELD)).args

        assertEquals(JsonPrimitive("artist_name"), args?.get("sort_field"))
        assertEquals(JsonPrimitive("desc"), args?.get("sort_direction"))
        assertNull(args?.get("order_by"))
    }

    @Test
    fun `an ascending sort sends asc`() {
        val args = Request.Track.list(sort = ListSort(SortOption(SortField.NAME), SortWire.SORT_FIELD)).args

        assertEquals(JsonPrimitive("sort_name"), args?.get("sort_field"))
        assertEquals(JsonPrimitive("asc"), args?.get("sort_direction"))
    }

    @Test
    fun `no sort sends no sort argument`() {
        val args = Request.Genre.listLibrary().args

        assertNull(args?.get("order_by"))
        assertNull(args?.get("sort_field"))
        assertNull(args?.get("sort_direction"))
    }
}
