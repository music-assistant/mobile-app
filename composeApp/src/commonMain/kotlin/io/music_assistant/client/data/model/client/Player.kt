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
    val activeGroup: String?,
    val syncedTo: String?,
    val groupVolume: Float?,
) {
    val isGroup = type == PlayerType.GROUP
    val isGrouped = !isGroup && groupMembers?.isNotEmpty() == true

    val suffix = when {
        isGroup -> " (${groupMembers?.size ?: 0})"
        isGrouped && (groupMembers?.size ?: 0) > 1 -> " +${groupMembers?.size?.minus(1)}"
        else -> null
    }

    val nameAndSuffix: String = name + (suffix?.let { " $it" } ?: "")

    val providerType = provider.substringBefore("--")

    val currentVolume = if (groupMembers?.isNotEmpty() == true) groupVolume else volumeLevel

    val isVolumeSliderAccessible = (isGroup || canSetVolume) && currentVolume != null

    val canPlay = when {
        isGroup -> groupMembers?.isNotEmpty() == true
        else -> true
    }

    fun asChildBindFor(other: Player): PlayerData.ChildBind? {
        if (id == other.id) return null
        if (other.canGroupWith?.contains(providerType) != true && other.canGroupWith?.contains(id) != true) return null
        return PlayerData.ChildBind(
            id = id,
            parentId = other.id,
            name = name,
            volume = volumeLevel,
            volumeSliderAccessible = isVolumeSliderAccessible,
            isMuted = volumeMuted.takeIf { canMute },
            isBound = other.groupMembers?.contains(id) == true,
            isManageable = other.staticGroupMembers?.contains(id) != true,
        )
    }

    fun asParentBind(): PlayerData.ParentBind {
        return PlayerData.ParentBind(
            id = id,
            name = name,
            isPlaying = isPlaying,
            isGroup = isGroup,
        )
    }

    companion object {
        private const val PLAYER_CONTROL_NONE = "none"

        fun ServerPlayer.toPlayer() = Player(
            id = playerId,
            name = displayName,
            provider = provider,
            // Unknown/new server-side player types (coerced to null by myJson) are
            // treated as regular players so they still show up and aren't mistaken
            // for a group. If the server adds a genuinely new group-like type we
            // can surface it explicitly once the mobile app learns about it.
            type = type ?: PlayerType.PLAYER,
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
            activeGroup = activeGroup,
            syncedTo = syncedTo,
            groupVolume = groupVolume,
        )
    }
}
