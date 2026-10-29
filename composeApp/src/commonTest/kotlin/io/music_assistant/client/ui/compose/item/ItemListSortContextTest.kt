package io.music_assistant.client.ui.compose.item

import io.music_assistant.client.data.model.client.ArtistType
import io.music_assistant.client.data.model.client.SubItemContext
import io.music_assistant.client.data.model.server.ProviderMapping
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ItemListSortContextTest {
    private val mapping = ProviderMapping("1", "niflheim", "niflheim-1")

    @Test
    fun `each artist list has its own sort context`() {
        assertEquals(SubItemContext.ARTIST_ALL_ALBUMS, ItemList.ArtistAlbums(mapping).sortContext)
        assertEquals(SubItemContext.ARTIST_TOP_TRACKS, ItemList.ArtistTopTracks(listOf(mapping)).sortContext)
        assertEquals(SubItemContext.ARTIST_TOP_TRACKS, ItemList.ArtistLibraryTopTracks("1", "lastfm").sortContext)
        assertEquals(SubItemContext.ARTIST_LIBRARY_ALBUMS, ItemList.ArtistLibrary("1").sortContext)
        assertEquals(
            SubItemContext.ARTIST_AUDIOBOOKS,
            ItemList.ArtistAudiobooks(listOf(mapping), ArtistType.AUTHOR).sortContext,
        )
        assertEquals(
            SubItemContext.ARTIST_AUDIOBOOKS,
            ItemList.ArtistLibraryAudiobooks("1", ArtistType.NARRATOR).sortContext,
        )
    }

    @Test
    fun `author lists send the artist type and the library list stays in the library`() {
        val provider = ItemList.ArtistAudiobooks(listOf(mapping), ArtistType.NARRATOR).toRequests().single()
        assertEquals("music/artists/artist_audiobooks", provider.command)
        assertEquals("narrator", provider.args?.get("artist_type")?.jsonPrimitive?.content)
        assertEquals("false", provider.args?.get("in_library_only")?.jsonPrimitive?.content)

        val library = ItemList.ArtistLibraryAudiobooks("1", ArtistType.AUTHOR).toRequests().single()
        assertEquals("library", library.args?.get("provider_instance_id_or_domain")?.jsonPrimitive?.content)
        assertEquals("true", library.args?.get("in_library_only")?.jsonPrimitive?.content)
    }

    @Test
    fun `library top tracks round-trips and filters only when narrowed`() {
        val narrowed = ItemList.ArtistLibraryTopTracks("1", "lastfm")
        assertEquals(
            narrowed,
            Json.decodeFromString(ItemList.serializer(), Json.encodeToString(ItemList.serializer(), narrowed)),
        )

        val (merged) = ItemList.ArtistLibraryTopTracks("1").toRequests()
        assertFalse("provider_filter" in merged.args.orEmpty())
        assertEquals("lastfm", narrowed.toRequests().single().args?.get("provider_filter")?.jsonPrimitive?.content)
    }

    @Test
    fun `sort context is not part of the serialized route`() {
        val encoded = Json.encodeToString(ItemList.serializer(), ItemList.ArtistLibrary("1"))
        assertFalse("sortContext" in encoded, encoded)
    }
}
