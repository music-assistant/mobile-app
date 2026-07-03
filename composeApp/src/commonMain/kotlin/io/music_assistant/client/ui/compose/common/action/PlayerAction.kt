package io.music_assistant.client.ui.compose.common.action

import io.music_assistant.client.data.model.client.RepeatMode

sealed interface PlayerAction {
    data object TogglePlayPause : PlayerAction
    data object Play : PlayerAction
    data object Pause : PlayerAction
    data object Next : PlayerAction
    data object Previous : PlayerAction
    data object VolumeUp : PlayerAction
    data class SetPower(val powered: Boolean) : PlayerAction
    data object VolumeDown : PlayerAction
    data class VolumeSet(val level: Double) : PlayerAction
    data class ToggleMute(val isMutedNow: Boolean) : PlayerAction
    data object GroupVolumeUp : PlayerAction
    data object GroupVolumeDown : PlayerAction
    data class GroupVolumeSet(val level: Double) : PlayerAction

    data class GroupToggleMute(val isMutedNow: Boolean) : PlayerAction

    data class GroupManage(val toAdd: List<String>? = null, val toRemove: List<String>? = null) :
        PlayerAction
    data class ToggleShuffle(val current: Boolean) : PlayerAction
    data class ToggleRepeatMode(val current: RepeatMode) : PlayerAction

    data class ToggleDontStopTheMusic(val current: Boolean) : PlayerAction
    data class SeekTo(val position: Long) : PlayerAction
    data class SeekBy(val offsetSeconds: Long) : PlayerAction
    data class SetPlaybackSpeed(val speed: Double) : PlayerAction
}
