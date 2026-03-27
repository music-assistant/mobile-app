package io.music_assistant.client.data.model.client

import io.music_assistant.client.data.model.server.PlayerFeature
import io.music_assistant.client.data.model.server.PlayerState
import io.music_assistant.client.data.model.server.PlayerType
import io.music_assistant.client.data.model.server.ServerPlayer

data class Player(
    val id: String,
    val name: String,
    val provider: String,
    val type: PlayerType,
    val shouldBeShown: Boolean,
    val canSetVolume: Boolean,
    val volumeLevel: Float?,
    val volumeControl: String?,
    val volumeMuted: Boolean,
    val canMute: Boolean,
    val queueId: String?,
    val isPlaying: Boolean,
    val isAnnouncing: Boolean,
    val canGroupWith: List<String>?,
    val groupMembers: Set<String>?,
    val staticGroupMembers: Set<String>?,
    //val activeGroup: String?,
    val groupVolume: Float?,
) {

    val isGroup = type == PlayerType.GROUP || groupMembers?.isNotEmpty() == true

    val displayName: String = run {
        val counter = groupMembers?.takeIf { isGroup || it.size > 1 }?.size
        val suffix = if (counter != null) {
            if (isGroup) " (${counter})" else " +${counter - 1}"
        } else ""
        "$name$suffix"
    }

    val providerType = provider.substringBefore("--")

    val currentVolume =
        if (groupMembers?.isNotEmpty() == true) groupVolume else volumeLevel

    val canPlay = !isGroup || (groupMembers?.isNotEmpty() == true)

    fun asBindFor(other: Player): PlayerData.Bind? {
        if (id == other.id) return null
        if (other.canGroupWith?.contains(providerType) != true && other.canGroupWith?.contains(id) != true) return null
        return PlayerData.Bind(
            id = id,
            parentId = other.id,
            name = name,
            volume = volumeLevel,
            isMuted = volumeMuted.takeIf { canMute },
            isBound = other.groupMembers?.contains(id) == true,
            isManageable = other.staticGroupMembers?.contains(id) != true,
        )
    }

    companion object {
        private const val PLAYER_CONTROL_NONE = "none"

        fun ServerPlayer.toPlayer() = Player(
            id = playerId,
            name = displayName,
            provider = provider,
            type = type,
            shouldBeShown = available && enabled && (hidden != true),
            canSetVolume = supportedFeatures.contains(PlayerFeature.VOLUME_SET),
            volumeLevel = volumeLevel,
            volumeControl = volumeControl,
            volumeMuted = volumeMuted == true,
            canMute = muteControl != null && muteControl != PLAYER_CONTROL_NONE,
            queueId = currentMedia?.queueId ?: activeSource,
            isPlaying = state == PlayerState.PLAYING,
            isAnnouncing = announcementInProgress == true,
            canGroupWith = canGroupWith,
            groupMembers = groupMembers,
            staticGroupMembers = staticGroupMembers,
            //activeGroup = activeGroup,
            groupVolume = groupVolume,
        )
    }
}