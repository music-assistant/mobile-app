package io.music_assistant.client.data.model.server

import io.music_assistant.client.data.factory.MediaItemFactory
import io.music_assistant.client.data.factory.QueueFactory
import io.music_assistant.client.data.model.client.QueueOption
import io.music_assistant.client.data.model.client.items.Track
import io.music_assistant.client.data.model.client.items.canBeFavorited
import io.music_assistant.client.data.model.client.items.refreshItemAction
import io.music_assistant.client.ui.compose.common.items.ItemAction
import io.music_assistant.client.ui.compose.common.items.resolveLongClickActions
import io.music_assistant.client.ui.compose.common.items.supportsAddToPlaylist
import io.music_assistant.client.utils.myJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the handling of a track that the server knows only by an external id (server 2.11), as
 * the top tracks of an artist list it: provider `external`, no provider mappings, and an album in
 * the same form. The server looks it up when it is opened or played.
 */
class ExternalItemMappingTest {
    private val factory = MediaItemFactory(StubServiceClient())

    private val trackJson = """
        {
          "item_id": "musicbrainz_recordingid:rec-1",
          "provider": "external",
          "name": "Song",
          "media_type": "track",
          "uri": "external://track/musicbrainz_recordingid:rec-1",
          "duration": 240,
          "is_playable": true,
          "provider_mappings": [],
          "artists": [{
            "item_id": "1",
            "provider": "library",
            "name": "Artist",
            "media_type": "artist",
            "uri": "library://artist/1"
          }],
          "album": {
            "item_id": "musicbrainz_albumid:rel-1",
            "provider": "external",
            "name": "Album",
            "media_type": "album",
            "uri": "external://album/musicbrainz_albumid:rel-1"
          }
        }
    """.trimIndent()

    private fun track(): Track =
        assertIs<Track>(factory.create(myJson.decodeFromString<ServerMediaItem>(trackJson)))

    @Test
    fun `an external track plays and its album opens before it plays`() {
        val track = track()

        assertTrue(track.isExternalItem)
        assertTrue(track.isPlayable)
        assertFalse(track.artists.single().isExternalItem)
        val album = assertNotNull(track.album)
        assertTrue(album.isExternalItem)
        assertFalse(album.isPlayable)
    }

    @Test
    fun `an external track offers no favorite or playlist or refresh entry until it is found`() {
        val track = track()
        val spotify = ServerProviderInstance(instanceId = "spotify--1", name = "Spotify", domain = "spotify")

        assertFalse(track.canBeFavorited)
        assertFalse(track.supportsAddToPlaylist)
        assertNull(track.refreshItemAction(RefreshItemAccess.ALL, listOf(spotify)))

        val actions = resolveLongClickActions(
            item = track,
            canEditLibrary = true,
            dislikeSupported = true,
            librarySupported = true,
            canAddToPlaylist = track.supportsAddToPlaylist,
            canRemoveFromPlaylist = false,
            progressSupported = false,
            playMediaSupport = PlayMediaSupport.ALL,
        )
        assertTrue(ItemAction.Play(QueueOption.PLAY) in actions)
        assertTrue(ItemAction.AddToLibrary in actions)
        assertTrue(actions.none { it is ItemAction.SetFavorite })
        assertFalse(ItemAction.AddToPlaylist in actions)
    }

    @Test
    fun `a queue item holds an external track until the server loads it`() {
        val queueItem = myJson.decodeFromString<ServerQueueItem>(
            """{"queue_item_id": "q1", "name": "Song", "duration": 240, "media_item": $trackJson}""",
        )

        val queued = assertNotNull(QueueFactory(factory).createTrack(queueItem))

        assertTrue(queued.isPlayable)
        val track = assertIs<Track>(queued.track)
        assertEquals("external://track/musicbrainz_recordingid:rec-1", track.uri)
        assertFalse(track.canBeFavorited)
    }
}
