package io.music_assistant.client.ui.compose.home.players

import io.music_assistant.client.data.model.client.AppMediaItemFixtures
import io.music_assistant.client.data.model.client.PlayerData
import io.music_assistant.client.data.model.client.PlayerDataFixtures
import io.music_assistant.client.data.model.client.PlayerDataFixtures.toQueue
import io.music_assistant.client.data.model.client.PlayerDataFixtures.toQueueTrack
import io.music_assistant.client.data.model.client.byId
import io.music_assistant.client.ui.compose.common.DataState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The anchor rule is what stops a hoisted dialog outliving the track it describes, and what
 * stops a stale player id from reviving a dismissed dialog. It is a pure function, so it is
 * checked here rather than through the Compose host.
 */
class PlayerDialogRequestTest {
    private fun playerWith(
        queueItemId: String,
        trackId: String = "track-1",
        playbackSpeed: Double? = null,
    ): PlayerData {
        val track = AppMediaItemFixtures.track(itemId = trackId)
        val queue = listOf(track.toQueueTrack(id = queueItemId)).toQueue()
        val data = PlayerDataFixtures.playerData(queue)
        return data.copy(
            queue = DataState.Data(
                queue.copy(info = queue.info.copy(playbackSpeed = playbackSpeed)),
            ),
        )
    }

    @Test
    fun `player-scoped requests hold while the player exists`() {
        val player = playerWith(queueItemId = "q1")
        val id = player.playerId
        val item = AppMediaItemFixtures.track()

        assertTrue(PlayerDialogRequest.Select(id).hasAnchor(player))
        assertTrue(PlayerDialogRequest.Group(id).hasAnchor(player))
        assertTrue(PlayerDialogRequest.Dsp(id).hasAnchor(player))
        assertTrue(PlayerDialogRequest.SleepTimer(id).hasAnchor(player))
        assertTrue(PlayerDialogRequest.AddToPlaylist(id, item).hasAnchor(player))
    }

    @Test
    fun `lyrics request drops when the current track changes`() {
        val player = playerWith(queueItemId = "q1", trackId = "track-1")
        val request = PlayerDialogRequest.Lyrics(player.playerId, trackId = "track-1")

        assertTrue(request.hasAnchor(player))
        assertFalse(request.hasAnchor(playerWith(queueItemId = "q2", trackId = "track-2")))
    }

    @Test
    fun `audio chain request drops when the queue item changes`() {
        val player = playerWith(queueItemId = "q1")
        val request = PlayerDialogRequest.AudioChain(player.playerId, queueItemId = "q1")

        assertTrue(request.hasAnchor(player))
        assertFalse(request.hasAnchor(playerWith(queueItemId = "q2")))
    }

    @Test
    fun `playback speed request needs both the queue item and a speed`() {
        val player = playerWith(queueItemId = "q1", playbackSpeed = 1.5)
        val request = PlayerDialogRequest.PlaybackSpeed(player.playerId, queueItemId = "q1")

        assertTrue(request.hasAnchor(player))
        // Track advanced.
        assertFalse(request.hasAnchor(playerWith(queueItemId = "q2", playbackSpeed = 1.5)))
        // Server stopped reporting variable speed for the queue.
        assertFalse(request.hasAnchor(playerWith(queueItemId = "q1", playbackSpeed = null)))
    }

    @Test
    fun `byId finds the player the request names and misses once it is gone`() {
        val first = playerWith(queueItemId = "q1")
        val second = playerWith(queueItemId = "q2")

        assertEquals(first, listOf(first, second).byId(first.playerId))
        assertNull(listOf(second).byId(first.playerId))
    }
}
