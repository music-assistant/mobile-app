package io.music_assistant.client.data.repository

import io.music_assistant.client.data.model.client.items.AppMediaItem

/**
 * Client-side projection of a server-side media-item lifecycle event.
 * Published by [MediaItemRepository.itemChanges] so ViewModels can react
 * to live library changes without touching `MediaItem*Event` DTOs.
 */
sealed class MediaItemChange {
    abstract val item: AppMediaItem

    data class Added(override val item: AppMediaItem) : MediaItemChange()
    data class Updated(override val item: AppMediaItem) : MediaItemChange()
    data class Deleted(override val item: AppMediaItem) : MediaItemChange()
}
