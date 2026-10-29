package io.music_assistant.client.data.model.client.items

import io.music_assistant.client.data.model.server.RefreshItemAccess
import io.music_assistant.client.data.model.server.ServerProviderInstance

/** The `music/refresh_item` entry of the item details menu. Both send the same command. */
enum class RefreshItemAction {
    REFRESH,

    /** The item is unavailable: the server searches the music services for a substitute. */
    FIND_ON_MUSIC_SERVICES,
}

/**
 * The refresh entry for this item, like the web frontend. Null when [access] does not let the
 * user run it, and while [providers] is not loaded, because availability cannot be known then.
 */
fun AppMediaItem.refreshItemAction(
    access: RefreshItemAccess,
    providers: List<ServerProviderInstance>?,
): RefreshItemAction? {
    if (access == RefreshItemAccess.NONE || !isRefreshable) return null
    val instances = providers ?: return null
    return when {
        !isAvailableOn(instances) -> RefreshItemAction.FIND_ON_MUSIC_SERVICES
        access == RefreshItemAccess.ALL -> RefreshItemAction.REFRESH
        else -> null
    }
}

// The server refreshes nothing for a genre, and a collection has no stored copy. A MusicBrainz
// item exists only to link an artist.
private val AppMediaItem.isRefreshable: Boolean
    get() = provider != MUSICBRAINZ_PROVIDER && when (this) {
        is Artist, is Album, is Track, is Playlist, is Podcast, is Audiobook -> true
        else -> false
    }

/** Mirrors the web frontend: a mapping counts when it and its provider instance are available. */
private fun AppMediaItem.isAvailableOn(providers: List<ServerProviderInstance>): Boolean =
    providerMappings.orEmpty().any { mapping ->
        mapping.available && providers.any { it.instanceId == mapping.providerInstance && it.available }
    }

private const val MUSICBRAINZ_PROVIDER = "musicbrainz"
