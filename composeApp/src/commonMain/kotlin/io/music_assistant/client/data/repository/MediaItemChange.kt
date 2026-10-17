package io.music_assistant.client.data.repository

import io.music_assistant.client.data.model.client.items.AppMediaItem

/**
 * Client-side projection of a server-side media-item lifecycle event.
 * Published by [MediaItemRepository.itemChanges] so ViewModels can react
 * to live library changes without touching `MediaItem*Event` DTOs.
 */
sealed class MediaItemChange {
    abstract val item: AppMediaItem

    /**
     * False when [item] has no favorite state of the user. From server 2.11, item events have
     * none, and [FavoriteUpdated] carries the state.
     */
    open val carriesFavorite: Boolean get() = true

    data class Added(
        override val item: AppMediaItem,
        override val carriesFavorite: Boolean = true,
    ) : MediaItemChange()

    data class Updated(
        override val item: AppMediaItem,
        override val carriesFavorite: Boolean = true,
    ) : MediaItemChange()

    data class Deleted(override val item: AppMediaItem) : MediaItemChange()

    /** The favorite state of the user changed. [item] is the library item with the new state. */
    data class FavoriteUpdated(override val item: AppMediaItem) : MediaItemChange()
}

/** The favorite of a row of [MediaItemChange.item] after this change, from its [current] favorite. */
fun MediaItemChange.favoriteAfter(current: Boolean?): Boolean? =
    if (carriesFavorite) item.favorite else current
