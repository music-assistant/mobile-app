package io.music_assistant.client.services

import android.support.v4.media.session.PlaybackStateCompat
import io.music_assistant.client.data.model.client.QueueInfo
import io.music_assistant.client.data.model.client.RepeatMode
import io.music_assistant.client.ui.compose.common.action.PlayerAction

/*
 * Standard session shuffle and repeat modes. The Bluetooth AVRCP target always offers both to the
 * car, whatever the session advertises, reads them from the session, and sets them through
 * TransportControls. Notification and Android Auto use the custom toggle actions instead.
 */

internal fun sessionShuffleMode(enabled: Boolean?): Int =
    if (enabled == true) PlaybackStateCompat.SHUFFLE_MODE_ALL else PlaybackStateCompat.SHUFFLE_MODE_NONE

internal fun sessionRepeatMode(mode: RepeatMode?): Int = when (mode) {
    RepeatMode.ONE -> PlaybackStateCompat.REPEAT_MODE_ONE
    RepeatMode.ALL -> PlaybackStateCompat.REPEAT_MODE_ALL
    RepeatMode.OFF, null -> PlaybackStateCompat.REPEAT_MODE_NONE
}

/** The server rejects shuffle and repeat on a dynamic playlist, so it gets no command. */
private val QueueInfo.acceptsModeChanges: Boolean get() = !isDynamicPlaylist

/** The toggle that sets shuffle to [sessionMode], or null when nothing changes. */
internal fun shuffleCommandFor(sessionMode: Int, queue: QueueInfo?): PlayerAction.ToggleShuffle? {
    val current = queue?.takeIf { it.acceptsModeChanges }?.shuffleEnabled ?: return null
    val target = sessionMode == PlaybackStateCompat.SHUFFLE_MODE_ALL ||
        sessionMode == PlaybackStateCompat.SHUFFLE_MODE_GROUP
    return PlayerAction.ToggleShuffle(current = current).takeIf { target != current }
}

/**
 * The toggle that sets repeat to [sessionMode], or null when nothing changes. The toggle sets
 * the mode after its `current` in the OFF → ALL → ONE cycle, so `current` is the mode before
 * the target: one request, whatever the real mode is.
 */
internal fun repeatCommandFor(sessionMode: Int, queue: QueueInfo?): PlayerAction.ToggleRepeatMode? {
    val current = queue?.takeIf { it.acceptsModeChanges }?.repeatMode ?: return null
    val target = when (sessionMode) {
        PlaybackStateCompat.REPEAT_MODE_ONE -> RepeatMode.ONE
        PlaybackStateCompat.REPEAT_MODE_ALL, PlaybackStateCompat.REPEAT_MODE_GROUP -> RepeatMode.ALL
        PlaybackStateCompat.REPEAT_MODE_NONE -> RepeatMode.OFF
        else -> return null
    }
    val before = when (target) {
        RepeatMode.ALL -> RepeatMode.OFF
        RepeatMode.ONE -> RepeatMode.ALL
        RepeatMode.OFF -> RepeatMode.ONE
    }
    return PlayerAction.ToggleRepeatMode(current = before).takeIf { target != current }
}
