package io.music_assistant.client.ui.compose.item

import io.music_assistant.client.data.model.client.SubItemContext
import io.music_assistant.client.data.model.server.ProviderMapping
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ItemListSortContextTest {
    private val mapping = ProviderMapping("1", "niflheim", "niflheim-1")

    @Test
    fun `each artist list has its own sort context`() {
        assertEquals(SubItemContext.ARTIST_ALL_ALBUMS, ItemList.ArtistAlbums(mapping).sortContext)
        assertEquals(SubItemContext.ARTIST_TOP_TRACKS, ItemList.ArtistTopTracks(listOf(mapping)).sortContext)
        assertEquals(SubItemContext.ARTIST_LIBRARY_ALBUMS, ItemList.ArtistLibrary("1").sortContext)
    }

    @Test
    fun `sort context is not part of the serialized route`() {
        val encoded = Json.encodeToString(ItemList.serializer(), ItemList.ArtistLibrary("1"))
        assertFalse("sortContext" in encoded, encoded)
    }
}
