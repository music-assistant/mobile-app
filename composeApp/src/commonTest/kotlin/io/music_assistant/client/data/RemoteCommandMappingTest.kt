package io.music_assistant.client.data

import io.music_assistant.client.data.model.client.AppMediaItemFixtures.track
import io.music_assistant.client.data.model.client.PlayerDataFixtures
import io.music_assistant.client.data.model.client.PlayerDataFixtures.toQueue
import io.music_assistant.client.data.model.client.QueueInfo
import io.music_assistant.client.data.model.client.QueueTrack
import io.music_assistant.client.data.model.client.RepeatMode
import io.music_assistant.client.ui.compose.common.action.PlayerAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the remote-command-string contract shared by the lock screen, Control
 * Center, and CarPlay entry points. Toggle commands must carry the current
 * queue state so the optimistic-update machinery flips from the right side.
 */
class RemoteCommandMappingTest {
    private fun queueInfo(
        shuffleEnabled: Boolean = false,
        repeatMode: RepeatMode? = RepeatMode.OFF,
    ): QueueInfo = QueueInfo(
        id = "queue-1",
        available = true,
        currentIndex = 0,
        shuffleEnabled = shuffleEnabled,
        repeatMode = repeatMode,
        autoPlayEnabled = null,
        elapsedTime = null,
        elapsedTimeLastUpdated = null,
        currentItem = null,
        radioSource = emptyList(),
    )

    @Test
    fun transportCommandsMapDirectly() {
        assertEquals(PlayerAction.Play, remoteCommandToPlayerAction("play", null))
        assertEquals(PlayerAction.Pause, remoteCommandToPlayerAction("pause", null))
        assertEquals(PlayerAction.TogglePlayPause, remoteCommandToPlayerAction("toggle_play_pause", null))
        assertEquals(PlayerAction.Next, remoteCommandToPlayerAction("next", null))
        assertEquals(PlayerAction.Previous, remoteCommandToPlayerAction("previous", null))
    }

    @Test
    fun toggleShuffleCarriesCurrentQueueState() {
        assertEquals(
            PlayerAction.ToggleShuffle(current = true),
            remoteCommandToPlayerAction("toggle_shuffle", queueInfo(shuffleEnabled = true)),
        )
        assertEquals(
            PlayerAction.ToggleShuffle(current = false),
            remoteCommandToPlayerAction("toggle_shuffle", queueInfo(shuffleEnabled = false)),
        )
        assertEquals(
            PlayerAction.ToggleShuffle(current = false),
            remoteCommandToPlayerAction("toggle_shuffle", null),
        )
    }

    @Test
    fun toggleRepeatCarriesCurrentModeAndDefaultsToOff() {
        assertEquals(
            PlayerAction.ToggleRepeatMode(current = RepeatMode.ONE),
            remoteCommandToPlayerAction("toggle_repeat", queueInfo(repeatMode = RepeatMode.ONE)),
        )
        assertEquals(
            PlayerAction.ToggleRepeatMode(current = RepeatMode.OFF),
            remoteCommandToPlayerAction("toggle_repeat", queueInfo(repeatMode = null)),
        )
        assertEquals(
            PlayerAction.ToggleRepeatMode(current = RepeatMode.OFF),
            remoteCommandToPlayerAction("toggle_repeat", null),
        )
    }

    @Test
    fun seekCommandsParseTheirPayload() {
        assertEquals(PlayerAction.SeekTo(42), remoteCommandToPlayerAction("seek:42.7", null))
        assertEquals(PlayerAction.SeekBy(-10), remoteCommandToPlayerAction("seek_by:-10", null))
        assertEquals(PlayerAction.SeekBy(30), remoteCommandToPlayerAction("seek_by:30", null))
        assertNull(remoteCommandToPlayerAction("seek:not-a-number", null))
        assertNull(remoteCommandToPlayerAction("seek_by:not-a-number", null))
    }

    @Test
    fun seekIsSentImmediatelyOnlyDuringActiveOnlinePlayback() {
        val seek = PlayerAction.SeekTo(42)

        assertTrue(shouldSendLocalActionImmediately(seek, isPlaying = true, commandReady = true))
        assertFalse(shouldSendLocalActionImmediately(seek, isPlaying = false, commandReady = true))
        assertFalse(shouldSendLocalActionImmediately(seek, isPlaying = true, commandReady = false))
        assertFalse(shouldSendLocalActionImmediately(seek, isPlaying = false, commandReady = false))
    }

    @Test
    fun deferredPausedSeekIsScopedToItsExactQueueItem() {
        val first = QueueTrack(
            id = "item-1",
            track = track(),
            isPlayable = true,
            format = null,
            dsp = null,
            provider = null,
        )
        val second = first.copy(id = "item-2")
        val firstData = PlayerDataFixtures.playerData(listOf(first).toQueue())
        val firstQueue = (firstData.queue as io.music_assistant.client.ui.compose.common.DataState.Data).data
        val sameQueueOtherItem = firstData.copy(
            queue = io.music_assistant.client.ui.compose.common.DataState.Data(
                firstQueue.copy(info = firstQueue.info.copy(currentItem = second)),
            ),
        )
        val pending = DeferredPausedSeek(firstData.queueInfo!!.id, first.id, position = 42)

        assertTrue(pending.matches(firstData))
        assertFalse(pending.matches(sameQueueOtherItem))
    }

    @Test
    fun absoluteSeekIsNeverPreservedForReplay() {
        assertFalse(shouldQueueLocalAction(PlayerAction.SeekTo(42)))
        assertTrue(shouldQueueLocalAction(PlayerAction.Play))
    }

    @Test
    fun nonSeekActionsKeepTheirExistingOfflineDispatchBehavior() {
        assertTrue(
            shouldSendLocalActionImmediately(
                PlayerAction.Play,
                isPlaying = false,
                commandReady = false,
            ),
        )
    }

    @Test
    fun unknownCommandsReturnNull() {
        assertNull(remoteCommandToPlayerAction("warp_speed", null))
        assertNull(remoteCommandToPlayerAction("", null))
    }
}
