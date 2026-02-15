package io.music_assistant.client.services

import io.music_assistant.client.data.model.client.PlayerData
import io.music_assistant.client.data.model.server.RepeatMode

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
    val duration: Long?
) {

    companion object {
        fun from(serverUrl: String?, playerData: PlayerData, multiplePlayers: Boolean) =
            MediaNotificationData(
                multiplePlayers = multiplePlayers,
                longItemId = playerData.queueInfo?.currentItem?.track?.longId,
                name = playerData.queueInfo?.currentItem?.track?.name,
                artist = playerData.queueInfo?.currentItem?.track?.subtitle,
                album = playerData.queueInfo?.currentItem?.track?.parentName,
                repeatMode = playerData.queueInfo?.repeatMode,
                shuffleEnabled = playerData.queueInfo?.shuffleEnabled,
                isPlaying = playerData.player.isPlaying,
                imageUrl = playerData.queueInfo?.currentItem?.track?.imageInfo?.url(serverUrl),
                elapsedTime = playerData.queueInfo?.elapsedTime?.toLong()?.let { it * 1000 },
                playerName = playerData.player.displayName.takeIf { !playerData.isLocal },
                duration = playerData.queueInfo?.currentItem?.track?.duration?.toLong()
                    ?.let { it * 1000 }
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
            if (new.elapsedTime - old.elapsedTime > 10000) {
                return false
            }
            return true
        }
    }
}
