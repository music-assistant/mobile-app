package io.music_assistant.client.data.model.server

import io.music_assistant.client.data.factory.MediaItemFactory
import io.music_assistant.client.data.factory.QueueFactory
import io.music_assistant.client.data.model.client.items.Album
import io.music_assistant.client.data.model.client.items.RecommendationFolder
import io.music_assistant.client.ui.compose.item.opensItemDetails
import io.music_assistant.client.utils.myJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `QueueItem.origin` (server 2.11, schema 90): the container ItemMapping becomes
 * [io.music_assistant.client.data.model.client.QueueTrack.origin].
 */
class QueueItemOriginTest {
    private val queueFactory = QueueFactory(MediaItemFactory(StubServiceClient()))

    private fun queueItemJson(origin: String) = """
        {
          "queue_item_id": "qi-1",
          "media_item": {
            "item_id": "t1",
            "provider": "library",
            "name": "Track",
            "media_type": "track"
          }$origin
        }
    """.trimIndent()

    private fun createTrack(origin: String) =
        assertNotNull(queueFactory.createTrack(myJson.decodeFromString<ServerQueueItem>(queueItemJson(origin))))

    @Test
    fun mapsAlbumContainer() {
        val origin = createTrack(
            """,
            "origin": {
              "container": {
                "item_id": "42", "provider": "library", "name": "Kind of Blue",
                "media_type": "album", "uri": "library://album/42", "is_playable": true
              },
              "provider_instance": "spotify--abc",
              "item_id": "sp-track-1"
            }""",
        ).origin

        val album = assertIs<Album>(origin)
        assertEquals("42", album.itemId)
        assertEquals("library", album.provider)
        assertEquals("Kind of Blue", album.name)
        assertTrue(album.opensItemDetails)
    }

    // A folder has no details screen: the dialog shows it as text only.
    @Test
    fun mapsFolderContainerWithoutDetails() {
        val origin = createTrack(
            """,
            "origin": {
              "container": {
                "item_id": "music/jazz", "provider": "filesystem_local--x", "name": "jazz",
                "media_type": "folder"
              }
            }""",
        ).origin

        assertFalse(assertIs<RecommendationFolder>(origin).opensItemDetails)
    }

    // An audiobook or episode on its own has a pin but no container.
    @Test
    fun noOriginWithoutContainer() {
        assertNull(createTrack(""", "origin": {"provider_instance": "abs--x", "item_id": "b1"}""").origin)
    }

    // A single track on its own.
    @Test
    fun noOriginWhenNull() {
        assertNull(createTrack(""", "origin": null""").origin)
    }

    // Server 2.10 sends no key.
    @Test
    fun noOriginWhenAbsent() {
        assertNull(createTrack("").origin)
    }
}
