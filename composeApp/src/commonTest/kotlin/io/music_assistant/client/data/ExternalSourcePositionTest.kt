package io.music_assistant.client.data

import io.music_assistant.client.data.factory.PlayerFactory
import io.music_assistant.client.data.model.client.Player
import io.music_assistant.client.data.model.client.PlayerData
import io.music_assistant.client.data.model.client.SongTiming
import io.music_assistant.client.data.model.server.ServerPlayer
import io.music_assistant.client.data.model.server.StubServiceClient
import io.music_assistant.client.ui.compose.common.DataState
import io.music_assistant.client.utils.currentTimeMillis
import io.music_assistant.client.utils.myJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A player on an external source has no queue: its position comes from the media that it reports. */
class ExternalSourcePositionTest {
    private val playerFactory = PlayerFactory(StubServiceClient())

    private fun player(activeSource: String, state: String = "playing"): Player = playerFactory.create(
        myJson.decodeFromString<ServerPlayer>(
            """{"player_id": "kitchen", "active_source": "$activeSource", "state": "$state",
                "source_list": [{"id": "kitchen"}, {"id": "$SPOTIFY", "can_seek": true}],
                "current_media": {"media_type": "track", "title": "Molly", "duration": 430,
                    "elapsed_time": 100, "elapsed_time_last_updated": 1000}}""",
        ),
    )

    private val timing = SongTiming(duration = 430.0, elapsed = 100.0, updatedAt = 1000.0)

    @Test
    fun `a track reports its timing without the song timing of a radio stream`() {
        val media = player(SPOTIFY).currentMedia

        assertEquals(timing, media?.timing)
        assertNull(media?.songTiming)
    }

    @Test
    fun `only a player on an external source gives a source position`() {
        assertEquals(mapOf("source:kitchen" to (timing to true)), externalSourceTimings(listOf(player(SPOTIFY))))
        assertEquals(mapOf("source:kitchen" to null), externalSourceTimings(listOf(player("kitchen"))))
        assertEquals(
            mapOf("source:kitchen" to (timing to false)),
            externalSourceTimings(listOf(player(SPOTIFY, state = "paused"))),
        )
    }

    @Test
    fun `the position key is the queue or the external source`() {
        fun data(player: Player) =
            PlayerData(player = player, queue = DataState.NoData(), parentBind = null, childrenBinds = emptyList())

        assertEquals("source:kitchen", data(player(SPOTIFY)).positionKey)
        assertNull(data(player("kitchen")).positionKey, "a queue that did not load has no position")
    }

    @Test
    fun `a paused source holds the reported position`() {
        val tracker = PlayerPositionTracker()

        tracker.setSource(KEY, timing, isPlaying = false)

        assertEquals(100.0, tracker.effectiveSec(KEY))
    }

    @Test
    fun `a playing source advances from the time of the report`() {
        val tracker = PlayerPositionTracker()
        val nowSec = currentTimeMillis() / 1000.0

        tracker.setSource(KEY, timing.copy(updatedAt = nowSec - 20), isPlaying = true)

        val position = assertNotNull(tracker.effectiveSec(KEY))
        assertTrue(position in 119.0..125.0, "position $position")
    }

    @Test
    fun `the same report keeps a pause and a null report removes the position`() {
        val tracker = PlayerPositionTracker()
        val report = timing.copy(updatedAt = currentTimeMillis() / 1000.0)
        tracker.setSource(KEY, report, isPlaying = true)
        tracker.setPlaying(KEY, false)
        val paused = tracker.effectiveSec(KEY)

        tracker.setSource(KEY, report, isPlaying = false)
        assertEquals(paused, tracker.effectiveSec(KEY))

        tracker.setSource(KEY, null, isPlaying = false)
        assertNull(tracker.effectiveSec(KEY))
    }

    private companion object {
        const val SPOTIFY = "spotify://audio_source/1"
        const val KEY = "source:kitchen"
    }
}
