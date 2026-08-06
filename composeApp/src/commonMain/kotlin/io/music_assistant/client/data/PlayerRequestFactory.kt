package io.music_assistant.client.data

import io.music_assistant.client.api.Request
import io.music_assistant.client.data.model.client.PlayerData
import io.music_assistant.client.data.model.client.RepeatMode
import io.music_assistant.client.data.model.client.items.Audiobook
import io.music_assistant.client.ui.compose.common.action.PlayerAction

/**
 * Pure mapper from a ([PlayerData], [PlayerAction]) pair to the wire [Request].
 * Shared by [MainDataSource] (server players) and [LocalPlayerController] (the
 * local Sendspin player) so the command table — and its audiobook-chapter and
 * relative-seek nuances — lives in exactly one place. No I/O: it only reads the
 * shared [positionTracker] for live position.
 */
class PlayerRequestFactory(
    private val positionTracker: PlayerPositionTracker,
) {
    /**
     * Normalises relative/logical actions into the absolute [PlayerAction.SeekTo] they
     * ultimately mean, reading the live position *now*:
     *  - [PlayerAction.SeekBy] → absolute seek (callers — UI/notification — don't hold position).
     *  - Audiobook [PlayerAction.Next]/[PlayerAction.Previous] → seek to the adjacent chapter
     *    start; a bare Next/Previous otherwise.
     * Every other action passes through unchanged. Callers resolve once, then feed the result
     * to both the optimistic update and [buildRequest] — so the optimistic update freezes to the
     * true target instead of a track-boundary 0.0 that would clobber the position read here.
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
        }
    }

    /** Live interpolated position (seconds), falling back to the last server anchor. */
    private fun PlayerData.effectivePositionSec(): Double =
        queueInfo?.id?.let(positionTracker::effectiveSec)
            ?: queueInfo?.elapsedTime ?: 0.0

    /**
     * Next-chapter target for an audiobook: the first chapter starting after the live
     * position. Null when the track isn't an audiobook or there's no later chapter — the
     * caller then falls back to a plain `next`.
     */
    private fun PlayerData.nextChapterSeek(): PlayerAction.SeekTo? {
        val currentPos = effectivePositionSec()
        return (queueInfo?.currentItem?.track as? Audiobook)
            ?.chapters?.firstOrNull { it.start > currentPos }?.start
            ?.let { PlayerAction.SeekTo(it.toLong()) }
    }

    /**
     * Previous-chapter target for an audiobook: within a 5 s grace of the current chapter's
     * start, jump to the prior chapter; otherwise restart the current chapter (clamped to
     * the book start). Null when the track isn't an audiobook or has no chapters — the caller
     * then falls back to a plain `previous`.
     */
    private fun PlayerData.previousChapterSeek(): PlayerAction.SeekTo? {
        val chapters = (queueInfo?.currentItem?.track as? Audiobook)
            ?.chapters?.takeIf { it.isNotEmpty() } ?: return null
        val currentPos = effectivePositionSec()
        val currentChapterStart = chapters.lastOrNull { it.start <= currentPos }?.start ?: 0.0
        val prevStart =
            if (currentPos - currentChapterStart > 5) {
                currentChapterStart
            } else {
                chapters.lastOrNull { it.start < currentChapterStart }?.start ?: 0.0
            }
        return PlayerAction.SeekTo(prevStart.toLong())
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
}
