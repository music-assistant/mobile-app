package io.music_assistant.client.data.model.client

import io.music_assistant.client.data.model.server.SleepTimerMode

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
    /**
     * The point at which the sleep timer stops playback, null without a timer. For an
     * end-of-content mode, [sleepTimerExpiresAt] is an estimate, and null while paused.
     */
    val sleepTimerMode: SleepTimerMode? = null,
    /** The id that the server names as the active source: a queue id, or the id of one of the [sources]. */
    val activeSource: String? = null,
    /** The sources of the player. The queue of the player is the entry with the player id. */
    val sources: List<PlayerSource> = emptyList(),
) {
    /**
     * The source that plays in place of a queue, or null while a queue plays. Such a player has
     * no queue to show, and its controls follow the flags of the source. A sync child names the
     * queue of its leader, which is not in its own list, so it has no external source.
     */
    val externalSource: PlayerSource? = activeSource?.takeIf { it != id }?.let { active ->
        sources.firstOrNull { it.id == active }
            // A live input that the server does not list: it can only stop.
            ?: active.takeIf { URI_MARK in it }?.let { PlayerSource(id = it, passive = true) }
    }

    /** The key of the position of the external source in `PlayerPositionTracker`. Not a queue id. */
    val sourcePositionKey: String get() = "source:$id"

    /**
     * The sources of the source menu, like the web frontend: the sources that the user can select
     * and the active one, sorted by name. Empty if there is no choice, and for a sync child.
     */
    val sourceChoices: List<PlayerSource>
        get() = sources
            .filter { !it.passive || isActive(it) }
            .takeIf { syncedTo == null && it.size > 1 }
            ?.sortedBy { it.name.uppercase() }
            .orEmpty()

    fun isActive(source: PlayerSource): Boolean = source.id == (activeSource ?: id)

    /**
     * The player is there but asleep. Two server states look identical to the user — a
     * device that is switched off, and one the server cannot reach any more (a speaker that
     * dropped off the network in stand-by) — so both get the same dimmed card and the same
     * power button.
     */
    val isPoweredOff: Boolean get() = !isAvailable || (canPower && !isPowered)

    /**
     * Something in the app can act on the player: the server reaches it, or it waits for setup.
     * The server drops every command, power included, for a player it cannot reach, so such a
     * player is left out of the pager and shown disabled in the player list.
     */
    val isSelectable: Boolean get() = isAvailable || needsSetup

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
            icon = icon,
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

    private companion object {
        // A queue id is a player id. Only an input of the audio source framework is a uri.
        const val URI_MARK = "://"
    }
}
