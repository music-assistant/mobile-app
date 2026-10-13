package io.music_assistant.client.data.model.server

import io.music_assistant.client.data.factory.MediaItemFactory
import io.music_assistant.client.data.model.client.ImageType
import io.music_assistant.client.data.model.client.MediaType
import io.music_assistant.client.data.model.client.items.Audiobook
import io.music_assistant.client.data.model.client.items.MediaCollection
import io.music_assistant.client.utils.myJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * An audiobook series arrives as `media_type: collection` with its books nested in `items`, no
 * artwork and no provider mappings. Before COLLECTION existed the factory dropped such rows
 * silently, so a grouped listing lost every series.
 */
class CollectionMappingTest {
    private val factory = MediaItemFactory(StubServiceClient())

    private fun book(id: String, image: Boolean) = buildString {
        append("""{"item_id":"$id","provider":"library","name":"Book $id","media_type":"audiobook"""")
        if (image) append(""","image":{"type":"thumb","path":"/img/$id.jpg","provider":"library"}""")
        append("}")
    }

    private fun collection(vararg books: String) = factory.create(
        myJson.decodeFromString<ServerMediaItem>(
            """{"item_id":"audiobook___The Expanse","provider":"library","name":"The Expanse",""" +
                """"media_type":"collection","uri":"library://collection/audiobook___The Expanse",""" +
                """"provider_mappings":[],"items":[${books.joinToString(",")}]}""",
        ),
    )

    @Test
    fun `a collection maps with its books in server order`() {
        val series = assertIs<MediaCollection>(collection(book("1", image = false), book("2", image = false)))

        assertEquals(MediaType.COLLECTION, series.mediaType)
        assertEquals("library://collection/audiobook___The Expanse", series.mediaUri)
        assertEquals(listOf("1", "2"), series.items.map { it.itemId })
        assertTrue(series.items.all { it is Audiobook })
    }

    @Test
    fun `a collection borrows the artwork of its first book that has one`() {
        val series = assertIs<MediaCollection>(collection(book("1", image = false), book("2", image = true)))

        assertNotNull(series.image(ImageType.THUMB))
        assertEquals(series.items[1].images, series.images)
    }

    @Test
    fun `an empty collection still maps without artwork`() {
        val series = assertIs<MediaCollection>(collection())

        assertTrue(series.items.isEmpty())
        assertTrue(series.images.isEmpty())
    }
}
