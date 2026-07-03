package io.music_assistant.client.data.model.client

data class Player(
    val id: String,
    val name: String,
    val provider: String,
    val type: PlayerType,
    /** Server-provided Material Design Icons name (e.g. "speaker"); null when absent. */
    val icon: String? = null,
    val shouldBeShown: Boolean,
    val canSetVolume: Boolean,
    val canPower: Boolean,
    val isPowered: Boolean,
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
    val groupVolumeMuted: Boolean,
    val currentMedia: PlayerMedia?,
) {
    val isPoweredOff: Boolean get() = canPower && !isPowered

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
    val currentMuteState = if (groupMembers?.isNotEmpty() == true) groupVolumeMuted else volumeMuted

    val isVolumeSliderAccessible = (isGroup || canSetVolume) && currentVolume != null && !isPoweredOff

    val canPlay = when {
        isGroup -> groupMembers?.isNotEmpty() == true
        else -> true
    }

    fun asChildBindFor(other: Player): PlayerData.ChildBind? {
        if (id == other.id) return null
        val isAlreadyGrouped = other.groupMembers?.contains(id) == true
        val canGroupByProvider = other.canGroupWith?.contains(providerType) == true
        val canGroupById = other.canGroupWith?.contains(id) == true
        if (!isAlreadyGrouped && !canGroupByProvider && !canGroupById) return null
        return PlayerData.ChildBind(
            id = id,
            parentId = other.id,
            name = name,
            volume = volumeLevel,
            volumeSliderAccessible = isVolumeSliderAccessible,
            isMuted = currentMuteState.takeIf { canMute },
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
}
