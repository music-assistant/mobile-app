package io.music_assistant.client.carplay

import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.ui.compose.home.QueueDisplayRow
import io.music_assistant.client.utils.formatDuration
import kotlin.time.DurationUnit

/**
 * One row of the CarPlay Up Next list. Swift sends [key] back to
 * `KmpHelper.playCarPlayQueueRow` on a tap.
 */
sealed interface CarPlayQueueRow {
    val key: String

    /** A queue item. [isCurrent] marks the item that plays now. */
    data class Item(
        override val key: String,
        val item: AppMediaItem,
        val isCurrent: Boolean,
    ) : CarPlayQueueRow

    /** A chapter of the current audiobook. A tap seeks to it. */
    data class Chapter(
        override val key: String,
        val title: String,
        val duration: String?,
    ) : CarPlayQueueRow
}

internal fun QueueDisplayRow.toCarPlayQueueRow(currentItemId: String?): CarPlayQueueRow? = when (this) {
    is QueueDisplayRow.QueueItem -> (item.track as? AppMediaItem)?.let {
        CarPlayQueueRow.Item(key = key, item = it, isCurrent = item.id == currentItemId)
    }

    is QueueDisplayRow.ChapterItem -> CarPlayQueueRow.Chapter(
        key = key,
        title = chapter.name,
        duration = chapter.end?.let { chapter.duration.formatDuration(DurationUnit.SECONDS) },
    )
}
