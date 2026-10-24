package io.music_assistant.client.data.model.server

import io.music_assistant.client.data.factory.MediaItemFactory
import io.music_assistant.client.data.factory.QueueFactory
import io.music_assistant.client.data.model.client.MediaType
import io.music_assistant.client.data.model.client.QueueOption
import io.music_assistant.client.data.model.client.items.AudioSource
import io.music_assistant.client.data.model.client.items.RecommendationFolder
import io.music_assistant.client.data.model.client.items.canBeFavorited
import io.music_assistant.client.ui.compose.common.items.ItemAction
import io.music_assistant.client.ui.compose.common.items.resolveLongClickActions
import io.music_assistant.client.utils.myJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Pins the handling of `audio_source` items: live inputs a server plugin exposes (VBAN, line-in).
 * The server lists a provider's single startable source directly at the `music/browse` root, and
 * the source becomes the queue's current item once started.
 */
class AudioSourceMappingTest {
    private val factory = MediaItemFactory(StubServiceClient())

    private val sourceJson = """
        {
          "item_id": "vban_in",
          "provider": "vban_receiver--x1",
          "name": "VBAN Receiver: Stream1",
          "media_type": "audio_source",
          "uri": "vban_receiver--x1://audio_source/vban_in",
          "is_playable": true,
          "duration": null,
          "can_play_pause": false,
          "can_seek": false,
          "can_next_previous": false,
          "can_initiate": true
        }
    """.trimIndent()

    private val folderJson = """
        {"item_id":"f1","provider":"tidal","media_type":"folder","name":"Tidal","path":"tidal--1://"}
    """.trimIndent()

    private fun source(): AudioSource =
        assertIs<AudioSource>(factory.create(myJson.decodeFromString<ServerMediaItem>(sourceJson)))

    @Test
    fun `browse root keeps the source next to the folders`() {
        val items = factory.createList(
            myJson.decodeFromString<List<ServerMediaItem>>("[$folderJson,$sourceJson]"),
        )

        assertEquals(2, items.size, "an audio_source must not be dropped from a browse level")
        assertIs<RecommendationFolder>(items[0])
        assertIs<AudioSource>(items[1])
    }

    @Test
    fun `the source keeps the uri that play_media needs`() {
        val source = source()

        assertEquals(MediaType.AUDIO_SOURCE, source.mediaType)
        assertEquals("VBAN Receiver: Stream1", source.displayName)
        assertEquals("vban_receiver--x1://audio_source/vban_in", source.mediaUri)
        assertTrue(source.isPlayable)
    }

    @Test
    fun `the source is the current item once started`() {
        val queueItem = myJson.decodeFromString<ServerQueueItem>(
            """{"queue_item_id":"q1","name":"VBAN Receiver: Stream1","media_item":$sourceJson}""",
        )

        val track = assertNotNull(QueueFactory(factory).createTrack(queueItem))
        assertIs<AudioSource>(track.track)
    }

    @Test
    fun `the source keeps its pause capability`() {
        assertFalse(source().canPlayPause)
        val pausable = sourceJson.replace("\"can_play_pause\": false", "\"can_play_pause\": true")
        assertTrue(
            assertIs<AudioSource>(factory.create(myJson.decodeFromString<ServerMediaItem>(pausable))).canPlayPause,
        )
    }

    @Test
    fun `a source without the flag cannot pause`() {
        val legacy = sourceJson.replace("\"can_play_pause\": false,", "")
        assertFalse(
            assertIs<AudioSource>(factory.create(myJson.decodeFromString<ServerMediaItem>(legacy))).canPlayPause,
        )
    }

    @Test
    fun `the source cannot be favorited`() {
        assertFalse(source().canBeFavorited)
    }

    @Test
    fun `the long-press menu offers play now only`() {
        val actions = resolveLongClickActions(
            item = source(),
            canEditLibrary = true,
            dislikeSupported = true,
            librarySupported = true,
            canAddToPlaylist = false,
            canRemoveFromPlaylist = false,
            progressSupported = false,
            playMediaSupport = PlayMediaSupport(shuffle = true, startFromBeginning = true),
            customizationAllowed = true,
        )

        assertEquals(listOf<ItemAction>(ItemAction.Play(QueueOption.REPLACE)), actions)
    }
}
