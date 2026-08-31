package io.music_assistant.client.data.model.server

import io.music_assistant.client.data.factory.MediaItemFactory
import io.music_assistant.client.data.factory.QueueFactory
import io.music_assistant.client.data.model.client.MediaType
import io.music_assistant.client.data.model.client.items.SoundEffect
import io.music_assistant.client.utils.myJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the queue's handling of `sound_effect` items — the spoken host clips the AI Radio
 * plugin splices between tracks.
 *
 * Before `MediaType.SOUND_EFFECT` existed, [MediaItemFactory] returned null for these and
 * [QueueFactory] dropped the item, so a clip played while staying invisible in the queue.
 */
class SoundEffectQueueItemTest {
    private val queueFactory = QueueFactory(MediaItemFactory(StubServiceClient()))

    private val clipJson = """
        {
          "queue_item_id": "dj-session-1_000",
          "name": "Weather",
          "duration": 18.5,
          "media_item": {
            "item_id": "dj-session-1_000",
            "provider": "ai_radio--abcdef",
            "name": "Weather",
            "media_type": "sound_effect"
          }
        }
    """.trimIndent()

    private fun decode(json: String) = myJson.decodeFromString<ServerQueueItem>(json)

    @Test
    fun clipSurvivesConversionInsteadOfBeingDropped() {
        val track = assertNotNull(
            queueFactory.createTrack(decode(clipJson)),
            "AI Radio host clips must not be dropped from the queue",
        )

        assertEquals("dj-session-1_000", track.id)
        assertIs<SoundEffect>(track.track)
        assertEquals(MediaType.SOUND_EFFECT, track.track.mediaType)
        assertEquals("Weather", track.track.displayName)
        // The clip's duration rides on the queue item, not on the nested media item, so the
        // converted track reports none. Nothing depends on it: the now-playing timeline reads
        // the player's own current_media, and the queue row shows no duration at all.
        assertNull(track.track.duration)
    }

    @Test
    fun clipIsPlayableSoTheQueueRowStaysEnabled() {
        val track = assertNotNull(queueFactory.createTrack(decode(clipJson)))

        // A greyed-out row would misreport a clip the player is about to play.
        assertTrue(track.isPlayable)
    }

    @Test
    fun clipIsNotDroppedFromAMixedQueue() {
        val trackJson = """
            {
              "queue_item_id": "item-2",
              "media_item": {
                "item_id": "t1",
                "provider": "library",
                "name": "Some Song",
                "media_type": "track"
              }
            }
        """.trimIndent()

        val items = queueFactory.createTrackList(
            listOf(decode(clipJson), decode(trackJson)),
        )

        assertEquals(listOf("dj-session-1_000", "item-2"), items.map { it.id })
    }

    @Test
    fun clipCarriesNoLibraryIdentity() {
        val clip = assertIs<SoundEffect>(
            assertNotNull(queueFactory.createTrack(decode(clipJson))).track,
        )

        // Nothing to enqueue, favorite or add to a playlist by: the clip exists only
        // inside this queue, so the item menus must not offer library actions for it.
        assertEquals(null, clip.uri)
        assertEquals(null, clip.favorite)
        assertTrue(!clip.isInLibrary)
        assertTrue(!clip.canStartEndlessMix)
    }
}
