package io.music_assistant.client.services

import android.support.v4.media.session.PlaybackStateCompat
import io.music_assistant.client.data.model.client.QueueInfo
import io.music_assistant.client.data.model.client.RepeatMode
import io.music_assistant.client.ui.compose.common.action.PlayerAction
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Pins the Bluetooth (AVRCP) shuffle and repeat path: session modes to queue commands and back. */
class SessionPlayerSettingsTest {
    // The cycle that PlayerRequestFactory and LocalPlayerAdapter apply to ToggleRepeatMode.current.
    private fun next(mode: RepeatMode) = when (mode) {
        RepeatMode.OFF -> RepeatMode.ALL
        RepeatMode.ALL -> RepeatMode.ONE
        RepeatMode.ONE -> RepeatMode.OFF
    }

    private fun queue(shuffle: Boolean = false, repeat: RepeatMode? = RepeatMode.OFF, dynamic: Boolean = false) =
        QueueInfo(
            id = "queue",
            available = true,
            currentIndex = 0,
            shuffleEnabled = shuffle,
            repeatMode = repeat,
            autoPlayEnabled = null,
            elapsedTime = null,
            elapsedTimeLastUpdated = null,
            currentItem = null,
            radioSource = emptyList(),
            isDynamicPlaylist = dynamic,
        )

    @Test
    fun `repeat command sets every target in one request from every mode`() {
        val sessionModes = mapOf(
            PlaybackStateCompat.REPEAT_MODE_NONE to RepeatMode.OFF,
            PlaybackStateCompat.REPEAT_MODE_ONE to RepeatMode.ONE,
            PlaybackStateCompat.REPEAT_MODE_ALL to RepeatMode.ALL,
            PlaybackStateCompat.REPEAT_MODE_GROUP to RepeatMode.ALL,
        )
        RepeatMode.entries.forEach { current ->
            sessionModes.forEach { (sessionMode, target) ->
                val command = repeatCommandFor(sessionMode, queue(repeat = current))
                if (target == current) {
                    assertNull(command, "$current -> $target")
                } else {
                    assertEquals(target, command?.current?.let(::next), "$current -> $target")
                }
            }
        }
    }

    @Test
    fun `shuffle command toggles only on a change`() {
        assertEquals(
            PlayerAction.ToggleShuffle(current = false),
            shuffleCommandFor(PlaybackStateCompat.SHUFFLE_MODE_ALL, queue(shuffle = false)),
        )
        assertEquals(
            PlayerAction.ToggleShuffle(current = true),
            shuffleCommandFor(PlaybackStateCompat.SHUFFLE_MODE_NONE, queue(shuffle = true)),
        )
        assertNull(shuffleCommandFor(PlaybackStateCompat.SHUFFLE_MODE_GROUP, queue(shuffle = true)))
        assertNull(shuffleCommandFor(PlaybackStateCompat.SHUFFLE_MODE_NONE, queue(shuffle = false)))
    }

    @Test
    fun `no command without a queue, on a dynamic playlist, or for an unknown mode`() {
        assertNull(shuffleCommandFor(PlaybackStateCompat.SHUFFLE_MODE_ALL, null))
        assertNull(repeatCommandFor(PlaybackStateCompat.REPEAT_MODE_ALL, null))
        assertNull(shuffleCommandFor(PlaybackStateCompat.SHUFFLE_MODE_ALL, queue(dynamic = true)))
        assertNull(repeatCommandFor(PlaybackStateCompat.REPEAT_MODE_ALL, queue(dynamic = true)))
        assertNull(repeatCommandFor(PlaybackStateCompat.REPEAT_MODE_ALL, queue(repeat = null)))
        assertNull(repeatCommandFor(PlaybackStateCompat.REPEAT_MODE_INVALID, queue()))
    }

    @Test
    fun `session modes mirror the queue`() {
        assertEquals(PlaybackStateCompat.SHUFFLE_MODE_ALL, sessionShuffleMode(true))
        assertEquals(PlaybackStateCompat.SHUFFLE_MODE_NONE, sessionShuffleMode(false))
        assertEquals(PlaybackStateCompat.SHUFFLE_MODE_NONE, sessionShuffleMode(null))
        assertEquals(PlaybackStateCompat.REPEAT_MODE_ONE, sessionRepeatMode(RepeatMode.ONE))
        assertEquals(PlaybackStateCompat.REPEAT_MODE_ALL, sessionRepeatMode(RepeatMode.ALL))
        assertEquals(PlaybackStateCompat.REPEAT_MODE_NONE, sessionRepeatMode(RepeatMode.OFF))
        assertEquals(PlaybackStateCompat.REPEAT_MODE_NONE, sessionRepeatMode(null))
    }
}
