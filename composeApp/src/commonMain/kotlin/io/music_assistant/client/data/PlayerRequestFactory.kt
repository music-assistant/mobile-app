package io.music_assistant.client.data

import io.music_assistant.client.api.Request
import io.music_assistant.client.data.model.client.Chapter
import io.music_assistant.client.data.model.client.PlayerData
import io.music_assistant.client.data.model.client.RepeatMode
import io.music_assistant.client.data.model.client.chapterSeekSeconds
import io.music_assistant.client.data.model.client.navigationChapters
import io.music_assistant.client.ui.compose.common.action.PlayerAction

/**
 * Pure [PlayerData]/[PlayerAction] to wire-[Request] mapper shared by all players.
 * Reads live position and the shared preference; performs no I/O.
 */
class PlayerRequestFactory(
    private val positionTracker: PlayerPositionTracker,
    private val userPreferences: UserPreferences,
) {
    /**
     * Resolves relative seeks and chapter navigation to absolute [PlayerAction.SeekTo] targets.
     * Callers use the same resolved action for optimistic state and [buildRequest].
     */
    fun resolve(data: PlayerData, action: PlayerAction): PlayerAction =
        when (action) {
            is PlayerAction.SeekBy -> action.toSeekTo(data)
            PlayerAction.Next -> data.nextChapterSeek() ?: action
            PlayerAction.Previous -> data.previousChapterSeek() ?: action
            else -> action
        }

    fun buildRequest(data: PlayerData, action: PlayerAction): Request? {
        return when (action) {
            PlayerAction.TogglePlayPause ->
                Request.Player.simpleCommand(playerId = data.playerId, command = "play_pause")

            PlayerAction.Play ->
                Request.Player.simpleCommand(playerId = data.playerId, command = "play")

            PlayerAction.Pause ->
                Request.Player.simpleCommand(playerId = data.playerId, command = "pause")

            // Audiobook chapter jumps are already turned into SeekTo by resolve(); only the
            // plain track-boundary fallback reaches here.
            PlayerAction.Next ->
                Request.Player.simpleCommand(playerId = data.playerId, command = "next")

            PlayerAction.Previous ->
                Request.Player.simpleCommand(playerId = data.playerId, command = "previous")

            is PlayerAction.SetPower ->
                Request.Player.setPower(playerId = data.playerId, powered = action.powered)

            is PlayerAction.SeekTo -> {
                Request.Player.seek(queueId = data.playerId, position = action.position)
            }

            // Resolved to SeekTo in resolve(); never reaches here.
            is PlayerAction.SeekBy -> null

            is PlayerAction.ToggleRepeatMode -> {
                val queueId = data.queueInfo?.id ?: return null
                Request.Queue.setRepeatMode(
                    queueId = queueId,
                    repeatMode = when (action.current) {
                        RepeatMode.OFF -> RepeatMode.ALL
                        RepeatMode.ALL -> RepeatMode.ONE
                        RepeatMode.ONE -> RepeatMode.OFF
                    },
                )
            }

            is PlayerAction.ToggleShuffle -> {
                val queueId = data.queueInfo?.id ?: return null
                Request.Queue.setShuffle(queueId = queueId, enabled = !action.current)
            }

            is PlayerAction.ToggleDontStopTheMusic -> {
                val queueId = data.queueInfo?.id ?: return null
                Request.Queue.setDontStopTheMusic(queueId = queueId, enabled = !action.current)
            }

            is PlayerAction.ToggleCrossfade -> {
                val queueId = data.queueInfo?.id ?: return null
                Request.Queue.setCrossfade(queueId = queueId, enabled = !action.current)
            }

            is PlayerAction.SetPlaybackSpeed -> {
                val queueId = data.queueInfo?.id ?: return null
                Request.Queue.setPlaybackSpeed(queueId = queueId, speed = action.speed)
            }

            PlayerAction.VolumeDown ->
                Request.Player.simpleCommand(playerId = data.playerId, command = "volume_down")

            PlayerAction.VolumeUp ->
                Request.Player.simpleCommand(playerId = data.playerId, command = "volume_up")

            is PlayerAction.VolumeSet ->
                Request.Player.setVolume(playerId = data.playerId, volumeLevel = action.level)

            is PlayerAction.ToggleMute ->
                Request.Player.setMute(playerId = data.playerId, !action.isMutedNow)

            PlayerAction.GroupVolumeDown ->
                Request.Player.simpleCommand(
                    playerId = data.playerId,
                    command = "group_volume_down",
                )

            PlayerAction.GroupVolumeUp ->
                Request.Player.simpleCommand(playerId = data.playerId, command = "group_volume_up")

            is PlayerAction.GroupVolumeSet ->
                Request.Player.setGroupVolume(playerId = data.playerId, volumeLevel = action.level)

            is PlayerAction.GroupToggleMute ->
                Request.Player.setGroupMute(playerId = data.playerId, !action.isMutedNow)

            is PlayerAction.GroupManage ->
                Request.Player.setGroupMembers(
                    playerId = data.playerId,
                    playersToAdd = action.toAdd,
                    playersToRemove = action.toRemove,
                )

            PlayerAction.LeaveGroup ->
                Request.Player.ungroup(playerId = data.playerId)
        }
    }

    /** Live interpolated position (seconds), falling back to the last server anchor. */
    private fun PlayerData.effectivePositionSec(): Double =
        queueInfo?.id?.let(positionTracker::effectiveSec)
            ?: queueInfo?.elapsedTime ?: 0.0

    /**
     * Preference-gated audiobook/podcast chapters for next/previous navigation.
     * Null selects the plain next/previous command.
     */
    private fun PlayerData.chapterNavigationTargets(): List<Chapter>? =
        if (userPreferences.isChapterProgressEnabled) {
            queueInfo?.currentItem?.track.navigationChapters()
        } else {
            null
        }

    /**
     * Targets the first chapter after the live position, or null for plain `next`.
     */
    private fun PlayerData.nextChapterSeek(): PlayerAction.SeekTo? {
        val currentPos = effectivePositionSec()
        return chapterNavigationTargets()
            ?.map { it.start }?.filter { it > currentPos }?.minOrNull()
            ?.let { PlayerAction.SeekTo(chapterSeekSeconds(it)) }
    }

    /**
     * Within 5 s of a chapter start, targets the prior chapter; otherwise restarts current.
     * Returns null when navigation is disabled, selecting plain `previous`.
     */
    private fun PlayerData.previousChapterSeek(): PlayerAction.SeekTo? {
        val starts = chapterNavigationTargets()
            ?.map { it.start }?.sorted() ?: return null
        val currentPos = effectivePositionSec()
        val currentChapterStart = starts.lastOrNull { it <= currentPos } ?: 0.0
        val prevStart =
            if (currentPos - currentChapterStart > PREVIOUS_RESTART_GRACE_SEC) {
                currentChapterStart
            } else {
                starts.lastOrNull { it < currentChapterStart } ?: 0.0
            }
        // Round up: a truncated fractional start lands in the prior chapter.
        return PlayerAction.SeekTo(chapterSeekSeconds(prevStart))
    }

    /**
     * Resolves a relative [PlayerAction.SeekBy] into an absolute [PlayerAction.SeekTo],
     * clamped to `[0, duration]`.
     */
    private fun PlayerAction.SeekBy.toSeekTo(data: PlayerData): PlayerAction.SeekTo {
        val target = (data.effectivePositionSec() + offsetSeconds).coerceAtLeast(0.0)
            .let { t -> data.player.currentMedia?.duration?.let(t::coerceAtMost) ?: t }
        return PlayerAction.SeekTo(target.toLong())
    }

    private companion object {
        /** Previous targets the prior chapter within this start-time grace period. */
        const val PREVIOUS_RESTART_GRACE_SEC = 5
    }
}
