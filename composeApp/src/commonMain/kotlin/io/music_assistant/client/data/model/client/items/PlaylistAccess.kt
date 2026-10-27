package io.music_assistant.client.data.model.client.items

/** Who, besides the owner, a Music Assistant playlist is shared with (server `ProviderSharing`). */
enum class PlaylistSharing(val serverValue: String) {
    PRIVATE("private"),
    SELECTED("selected"),
    MEMBERS("members"),
    EVERYONE("everyone"),
    ;

    companion object {
        fun fromServer(raw: String): PlaylistSharing? = entries.firstOrNull { it.serverValue == raw }
    }
}

/**
 * The access record of a Music Assistant playlist, server 2.11. [owner] is a user id, unrelated to
 * the display name in [Playlist.owner]; null means that administrators manage the playlist.
 * [sharing] is null when the server sent a value that the app does not know.
 */
data class PlaylistAccess(
    val owner: String?,
    val sharing: PlaylistSharing?,
    val sharedUsers: List<String>,
    val collaborative: Boolean,
)

/** One part of the owner line of a Music Assistant playlist. The screen maps each to a string. */
sealed interface PlaylistAccessPart {
    /** The viewer owns the playlist. */
    data object Personal : PlaylistAccessPart

    /** Somebody else, or nobody, owns the playlist and the viewer cannot name them. */
    data object Shared : PlaylistAccessPart
    data class Owner(val name: String) : PlaylistAccessPart
    data object OnlyMe : PlaylistAccessPart
    data object NotShared : PlaylistAccessPart
    data class SharedWith(val count: Int) : PlaylistAccessPart
    data object Members : PlaylistAccessPart
    data object Everyone : PlaylistAccessPart
    data object Collaborative : PlaylistAccessPart
}

/**
 * The owner line of a Music Assistant playlist, like the web frontend: who owns it, who sees it,
 * and whether the users it is shared with may also edit it. A null record means everyone.
 * [viewerId] is the signed-in user. [ownerName] is the name of another owner, when known.
 */
fun PlaylistAccess?.summaryParts(viewerId: String?, ownerName: String?): List<PlaylistAccessPart> {
    val access = this ?: return listOf(PlaylistAccessPart.Shared, PlaylistAccessPart.Everyone)
    val ownedByViewer = access.owner != null && access.owner == viewerId
    val owner = when {
        ownedByViewer -> PlaylistAccessPart.Personal
        access.owner != null && ownerName != null -> PlaylistAccessPart.Owner(ownerName)
        else -> PlaylistAccessPart.Shared
    }
    val sharing = when (access.sharing) {
        PlaylistSharing.PRIVATE -> if (ownedByViewer) PlaylistAccessPart.OnlyMe else PlaylistAccessPart.NotShared
        PlaylistSharing.SELECTED -> PlaylistAccessPart.SharedWith(access.sharedUsers.size)
        PlaylistSharing.MEMBERS -> PlaylistAccessPart.Members
        PlaylistSharing.EVERYONE -> PlaylistAccessPart.Everyone
        null -> null
    }
    val collaborative = PlaylistAccessPart.Collaborative.takeIf { access.collaborative }
    return listOfNotNull(owner, sharing, collaborative)
}

/** What the item details page shows about the owner of a playlist. */
sealed interface PlaylistOwnerLine {
    /** The owner name that the provider reports. */
    data class Owner(val name: String) : PlaylistOwnerLine

    /** The access record of a Music Assistant playlist. */
    data class Access(val parts: List<PlaylistAccessPart>) : PlaylistOwnerLine
}

/**
 * The owner line of [this] playlist. A Music Assistant playlist gets its access record on a server
 * that sends one ([accessSupported], 2.11): there a null record means everyone. Every other
 * playlist, and every playlist on an older server, shows the [Playlist.owner] name.
 */
fun Playlist.ownerLine(accessSupported: Boolean, viewerId: String?, ownerName: String?): PlaylistOwnerLine? =
    when {
        accessSupported && isMusicAssistantPlaylist ->
            PlaylistOwnerLine.Access(access.summaryParts(viewerId, ownerName))

        else -> owner?.takeIf { it.isNotBlank() }?.let(PlaylistOwnerLine::Owner)
    }
