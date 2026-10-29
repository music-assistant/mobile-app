package io.music_assistant.client.data.model.server

import io.music_assistant.client.data.factory.MediaItemFactory
import io.music_assistant.client.data.model.client.AlbumType
import io.music_assistant.client.data.model.client.items.Album
import io.music_assistant.client.data.model.client.items.Audiobook
import io.music_assistant.client.data.model.client.items.Playlist
import io.music_assistant.client.data.model.client.items.PlaylistAccess
import io.music_assistant.client.data.model.client.items.PlaylistSharing
import io.music_assistant.client.utils.myJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/** The fields of the item details page: album type, texts, publisher, owner and access record. */
class ItemMetadataMappingTest {
    private val factory = MediaItemFactory(StubServiceClient())

    private fun create(json: String) = factory.create(myJson.decodeFromString<ServerMediaItem>(json))

    @Test
    fun albumTypeMapsAndAnUnknownOneIsNull() {
        val ep = assertIs<Album>(
            create("""{"item_id":"1","provider":"library","name":"A","media_type":"album","album_type":"ep"}"""),
        )
        assertEquals(AlbumType.EP, ep.albumType)

        val unknown = assertIs<Album>(
            create("""{"item_id":"1","provider":"library","name":"A","media_type":"album","album_type":"unknown"}"""),
        )
        assertNull(unknown.albumType)
        val absent = assertIs<Album>(
            create("""{"item_id":"1","provider":"library","name":"A","media_type":"album"}"""),
        )
        assertNull(absent.albumType)
    }

    @Test
    fun textsReachTheMetadata() {
        val album = assertIs<Album>(
            create(
                """{"item_id":"1","provider":"library","name":"A","media_type":"album",""" +
                    """"metadata":{"description":"Desc","review":"Rev"}}""",
            ),
        )

        assertEquals("Desc", album.metadata?.description)
        assertEquals("Rev", album.metadata?.review)
    }

    @Test
    fun audiobookPublisherMaps() {
        val book = assertIs<Audiobook>(
            create("""{"item_id":"1","provider":"library","name":"B","media_type":"audiobook","publisher":"Pub"}"""),
        )

        assertEquals("Pub", book.publisher)
    }

    @Test
    fun playlistOwnerAndAccessMapOn211() {
        val playlist = assertIs<Playlist>(
            create(
                """{"item_id":"1","provider":"library","name":"P","media_type":"playlist","owner":"Alex",""" +
                    """"access":{"owner":"u1","sharing":"selected","shared_users":["u2","u3"],"collaborative":true}}""",
            ),
        )

        assertEquals("Alex", playlist.owner)
        assertEquals(
            PlaylistAccess("u1", PlaylistSharing.SELECTED, listOf("u2", "u3"), collaborative = true),
            playlist.access,
        )
    }

    @Test
    fun playlistWithoutARecordOn210HasNoAccess() {
        val playlist = assertIs<Playlist>(
            create("""{"item_id":"1","provider":"library","name":"P","media_type":"playlist","owner":""}"""),
        )

        assertEquals("", playlist.owner)
        assertNull(playlist.access)
    }

    @Test
    fun anUnknownSharingValueKeepsTheRecordWithoutIt() {
        val playlist = assertIs<Playlist>(
            create(
                """{"item_id":"1","provider":"library","name":"P","media_type":"playlist",""" +
                    """"access":{"owner":null,"sharing":"household"}}""",
            ),
        )

        assertEquals(PlaylistAccess(null, null, emptyList(), collaborative = false), playlist.access)
    }
}
