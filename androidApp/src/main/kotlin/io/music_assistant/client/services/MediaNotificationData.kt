package io.music_assistant.client.services

import io.music_assistant.client.data.model.client.PlayerData
import io.music_assistant.client.data.model.server.RepeatMode

// Elapsed time can drift up to 10s before we treat it as a real position change.
private const val ELAPSED_TIME_DRIFT_TOLERANCE_MS = 10_000

data class MediaNotificationData(
    val multiplePlayers: Boolean,
    val longItemId: Long?,
    val name: String?,
    val artist: String?,
    val album: String?,
    val repeatMode: RepeatMode?,
    val shuffleEnabled: Boolean?,
    val isPlaying: Boolean,
    val imageUrl: String?,
    val elapsedTime: Long?,
    val playerName: String?,
    val duration: Long?,
) {
    companion object {
        /**
         * Builds the snapshot pushed to MediaSession. [effectiveElapsedSec]
         * is the freshly-extrapolated position at write-time (computed by the
         * data layer from anchor + elapsed wall clock). We use it directly
         * instead of `playerData.queueInfo.elapsedTime` because the latter is
         * only refreshed on `QueueAdded/UpdatedEvent` — not on the more
         * frequent `QueueTimeUpdatedEvent` — and would freeze the AA /
         * notification progress bar at a stale anchor on pause.
         */
        fun from(
            playerData: PlayerData,
            multiplePlayers: Boolean,
            effectiveElapsedSec: Double?,
        ) = MediaNotificationData(
            multiplePlayers = multiplePlayers,
            longItemId = playerData.player.currentMedia?.hashCode()?.toLong(),
            name = playerData.player.currentMedia?.title,
            artist = playerData.player.currentMedia?.artist,
            album = playerData.player.currentMedia?.album,
            repeatMode = playerData.queueInfo?.repeatMode,
            shuffleEnabled = playerData.queueInfo?.shuffleEnabled,
            isPlaying = playerData.player.isPlaying,
            imageUrl = playerData.player.currentMedia?.imageUrl,
            elapsedTime = effectiveElapsedSec?.toLong()?.let { it * 1000 },
            playerName = playerData.player.nameAndSuffix.takeIf { !playerData.isLocal },
            duration = playerData.player.currentMedia?.duration?.toLong()
                ?.let { it * 1000 },
        )

        fun areTooSimilarToUpdate(old: MediaNotificationData, new: MediaNotificationData): Boolean {
            if (old.copy(elapsedTime = null) != new.copy(elapsedTime = null)) {
                return false
            }
            if (old.elapsedTime == null) {
                return new.elapsedTime == null
            }
            if (new.elapsedTime == null) {
                return false
            }
            if (old.elapsedTime > new.elapsedTime) {
                return false
            }
            if (new.elapsedTime - old.elapsedTime > ELAPSED_TIME_DRIFT_TOLERANCE_MS) {
                return false
            }
            return true
        }
    }
}
