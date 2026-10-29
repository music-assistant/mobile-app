package io.music_assistant.client.data.model.server

import io.music_assistant.client.data.factory.PlayerFactory
import io.music_assistant.client.data.model.client.Player
import io.music_assistant.client.data.model.client.QueueInfo
import io.music_assistant.client.data.model.client.QueueTrack
import io.music_assistant.client.data.model.client.RepeatMode
import io.music_assistant.client.data.model.client.SongTiming
import io.music_assistant.client.data.model.client.testRadio
import io.music_assistant.client.data.radioSongTimings
import io.music_assistant.client.utils.myJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * A radio stream that knows its song reports the song's duration and the position in it; the
 * queue is then timed by that song instead of by how long the station has played.
 */
class RadioSongTimingTest {
    private val playerFactory = PlayerFactory(StubServiceClient())

    private fun player(mediaType: String, timing: String, queueItemId: String = "item-1"): Player =
        playerFactory.create(
            myJson.decodeFromString<ServerPlayer>(
                """{"player_id": "pl1", "active_source": "q1", "current_media": {"media_type": "$mediaType",""" +
                    """ "title": "Molly", "queue_item_id": "$queueItemId"$timing}}""",
            ),
        )

    private val songTiming = """, "duration": 430, "elapsed_time": 100, "elapsed_time_last_updated": 1000"""

    private val queue = QueueInfo(
        id = "q1",
        available = true,
        currentIndex = 0,
        shuffleEnabled = false,
        repeatMode = RepeatMode.OFF,
        autoPlayEnabled = null,
        elapsedTime = 1289.0,
        elapsedTimeLastUpdated = null,
        currentItem = QueueTrack(
            id = "item-1",
            track = testRadio(),
            isPlayable = true,
            format = null,
            provider = "test",
        ),
        radioSource = emptyList(),
    )

    @Test
    fun `a radio stream reporting its song carries the song's timing and length`() {
        val media = player("radio", songTiming).currentMedia

        assertEquals(SongTiming(duration = 430.0, elapsed = 100.0, updatedAt = 1000.0), media?.songTiming)
        assertEquals(430.0, media?.duration)
    }

    @Test
    fun `neither a radio stream without song durations nor a track carries any`() {
        val radio = player("radio", """, "elapsed_time": 1289, "elapsed_time_last_updated": 1000""").currentMedia
        assertNull(radio?.songTiming)
        assertNull(radio?.duration)
        val track = player("track", songTiming).currentMedia
        assertNull(track?.songTiming)
        assertEquals(430.0, track?.duration)
    }

    @Test
    fun `the queue playing the radio stream is timed by its song`() {
        assertEquals(
            mapOf("q1" to SongTiming(duration = 430.0, elapsed = 100.0, updatedAt = 1000.0)),
            radioSongTimings(listOf(player("radio", songTiming)), listOf(queue)),
        )
    }

    @Test
    fun `a radio song reported for another queue item does not time the queue`() {
        assertEquals(
            mapOf("q1" to null),
            radioSongTimings(listOf(player("radio", songTiming, queueItemId = "item-0")), listOf(queue)),
        )
    }

    @Test
    fun `the song position moves with the clock and stops at the song's end`() {
        val timing = SongTiming(duration = 430.0, elapsed = 100.0, updatedAt = 1000.0)

        assertEquals(104.0, timing.positionAt(1004.0))
        assertEquals(430.0, timing.positionAt(5000.0))
    }
}
