package io.music_assistant.client.ui.compose.home

import io.music_assistant.client.data.model.client.Chapter
import io.music_assistant.client.data.model.client.QueueTrack
import io.music_assistant.client.data.model.client.items.Audiobook

/**
 * A single visual row in the queue list. Either a real queue item or a
 * display-only chapter seek target shown under the current audiobook.
 *
 * Chapters are never queue items; they carry no reorder/remove semantics.
 */
sealed interface QueueDisplayRow {
    val key: String

    data class QueueItem(
        val queueIndex: Int,
        val item: QueueTrack,
    ) : QueueDisplayRow {
        override val key: String = "queue:${item.id}"
    }

    data class ChapterItem(
        val parentQueueIndex: Int,
        val parentQueueItemId: String,
        val chapterIndex: Int,
        val chapter: Chapter,
    ) : QueueDisplayRow {
        // Keyed on the chapter's list index, not chapter.position: position is
        // server-supplied and not guaranteed unique, and a collision would crash
        // the LazyColumn with duplicate keys. The index is unique and stable.
        override val key: String = "chapter:$parentQueueItemId:$chapterIndex"
    }
}

/**
 * Flattens queue items into display rows, inserting chapter rows only under the
 * current item when it is an audiobook with chapters. Position-independent: does
 * not encode active state, so the result is stable across playback ticks and
 * safe to `remember` keyed only on the receiver and [currentItemId].
 */
fun List<QueueTrack>.buildDisplayRows(currentItemId: String?): List<QueueDisplayRow> =
    flatMapIndexed { queueIndex, item ->
        buildList {
            add(QueueDisplayRow.QueueItem(queueIndex, item))
            if (item.id == currentItemId) {
                (item.track as? Audiobook)?.chapters.orEmpty()
                    .forEachIndexed { chapterIndex, chapter ->
                        add(
                            QueueDisplayRow.ChapterItem(
                                parentQueueIndex = queueIndex,
                                parentQueueItemId = item.id,
                                chapterIndex = chapterIndex,
                                chapter = chapter,
                            ),
                        )
                    }
            }
        }
    }

/** Last chapter whose start is at or before [position]; null if [position] precedes all. */
internal fun List<Chapter>.activeChapter(position: Double): Chapter? =
    lastOrNull { it.start <= position }

/** Maps a LazyColumn visual index to a real queue index, or null for chapter rows. */
internal fun List<QueueDisplayRow>.queueIndexAt(visualIndex: Int): Int? =
    (getOrNull(visualIndex) as? QueueDisplayRow.QueueItem)?.queueIndex
