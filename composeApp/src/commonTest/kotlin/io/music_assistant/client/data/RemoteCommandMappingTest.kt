package io.music_assistant.client.data

import io.music_assistant.client.data.model.client.QueueInfo
import io.music_assistant.client.data.model.client.RepeatMode
import io.music_assistant.client.ui.compose.common.action.PlayerAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
    fun unknownCommandsReturnNull() {
        assertNull(remoteCommandToPlayerAction("warp_speed", null))
        assertNull(remoteCommandToPlayerAction("", null))
    }
}
