package io.music_assistant.client.data.model.client

data class Player(
    val id: String,
    val name: String,
    val provider: String,
    val type: PlayerType,
    /** Server-provided Material Design Icons name (e.g. "speaker"); null when absent. */
    val icon: String? = null,
    /** The player passes the user-facing visibility filters: enabled, not hidden. */
    val isListed: Boolean,
    /** The server can currently reach the device. It drops every command while this is false. */
    val isAvailable: Boolean,
    /** The player waits for an interactive setup step. No power command can help it. */
    val needsSetup: Boolean,
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
    /** Unix (UTC) timestamp in seconds at which the sleep timer stops playback. */
    val sleepTimerExpiresAt: Double? = null,
) {
    /**
     * The player is there but asleep. Two server states look identical to the user — a
     * device that is switched off, and one the server cannot reach any more (a speaker that
     * dropped off the network in stand-by) — so both get the same dimmed card and the same
     * power button.
     */
    val isPoweredOff: Boolean get() = !isAvailable || (canPower && !isPowered)

    val isGroup = type == PlayerType.GROUP
    val isGrouped = !isGroup && groupMembers?.isNotEmpty() == true

    /**
     * True when this player leads an ad-hoc sync group, so ungrouping it hands the
     * session to a surviving member. [PlayerType.GROUP] players are excluded: for them
     * the server's `ungroup` releases the whole session instead of transferring it.
     * A leader has no parent of its own, and a permanent member is never removable.
     */
    val canLeaveOwnGroup: Boolean
        get() = isGrouped &&
            activeGroup == null &&
            syncedTo == null &&
            staticGroupMembers?.contains(id) != true

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
