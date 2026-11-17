package io.music_assistant.client.ui.compose.home

import io.music_assistant.client.data.model.client.AppMediaItemFixtures
import io.music_assistant.client.data.model.client.Chapter
import io.music_assistant.client.data.model.client.PlayerDataFixtures
import io.music_assistant.client.data.model.client.QueueTrack
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the pure, non-Compose core of the inline-audiobook-chapters feature:
 * row flattening, active-chapter selection, and visual->queue index mapping.
 * These are the bug-prone parts, so they are tested directly rather than
 * through Compose.
 */
class QueueDisplayRowsTest {
    private val chapterNames = listOf("Ch 1", "Ch 2", "Ch 3")

    /** Wraps a [PlayableItem][io.music_assistant.client.data.model.client.items.PlayableItem]
     * fixture as a queue track with a deterministic id. */
    private fun queueTrackOf(
        item: io.music_assistant.client.data.model.client.items.PlayableItem,
        id: String,
    ): QueueTrack = with(PlayerDataFixtures) { item.toQueueTrack(id = id) }

    private fun audiobookTrack(id: String, chapters: List<String> = chapterNames): QueueTrack =
        queueTrackOf(AppMediaItemFixtures.audiobook(chapters = chapters), id)

    private fun songTrack(id: String): QueueTrack =
        queueTrackOf(AppMediaItemFixtures.track(name = "Song $id"), id)

    // --- buildDisplayRows -------------------------------------------------

    @Test
    fun currentAudiobookEmitsChapterRowsCarryingParentMetadata() {
        val song = songTrack("s0")
        val book = audiobookTrack("b1")
        val items = listOf(song, book)

        val rows = items.buildDisplayRows(currentItemId = book.id)

        // One row per queue item plus one row per chapter of the current book.
        assertEquals(items.size + chapterNames.size, rows.size)

        val chapterRows = rows.filterIsInstance<QueueDisplayRow.ChapterItem>()
        assertEquals(chapterNames.size, chapterRows.size)

        val bookQueueIndex = items.indexOf(book)
        chapterRows.forEach { row ->
            assertEquals(bookQueueIndex, row.parentQueueIndex)
            assertEquals(book.id, row.parentQueueItemId)
        }
    }

    @Test
    fun currentAudiobookWithNullChaptersEmitsNoChapterRows() {
        val withNull = AppMediaItemFixtures.audiobook(chapters = emptyList())
            .copy(chapters = null)
        val book = queueTrackOf(withNull, id = "b1")

        val rows = listOf(book).buildDisplayRows(currentItemId = book.id)

        assertEquals(1, rows.size)
        assertTrue(rows.single() is QueueDisplayRow.QueueItem)
    }

    @Test
    fun currentAudiobookWithEmptyChaptersEmitsNoChapterRows() {
        val book = audiobookTrack("b1", chapters = emptyList())

        val rows = listOf(book).buildDisplayRows(currentItemId = book.id)

        assertEquals(1, rows.size)
        assertTrue(rows.single() is QueueDisplayRow.QueueItem)
    }

    @Test
    fun audiobookThatIsNotCurrentEmitsNoChapterRows() {
        val book = audiobookTrack("b1")
        val current = songTrack("s0")

        val rows = listOf(current, book).buildDisplayRows(currentItemId = current.id)

        assertTrue(rows.none { it is QueueDisplayRow.ChapterItem })
        assertEquals(2, rows.size)
    }

    @Test
    fun nonAudiobookCurrentItemEmitsSingleRow() {
        val song = songTrack("s0")

        val rows = listOf(song).buildDisplayRows(currentItemId = song.id)

        assertEquals(1, rows.size)
        assertTrue(rows.single() is QueueDisplayRow.QueueItem)
    }

    // --- activeChapter ----------------------------------------------------

    private val chapters =
        AppMediaItemFixtures.audiobook(chapters = chapterNames).chapters!!

    @Test
    fun activeChapterAtExactStartBoundaryReturnsThatChapter() {
        val target = chapters[1]

        assertEquals(target, chapters.activeChapter(target.start))
    }

    @Test
    fun activeChapterBeforeFirstStartReturnsNull() {
        val beforeAll = chapters.first().start - 1.0

        assertNull(chapters.activeChapter(beforeAll))
    }

    @Test
    fun activeChapterPastLastStartReturnsLastChapter() {
        val pastEnd = chapters.last().start + 1.0

        assertEquals(chapters.last(), chapters.activeChapter(pastEnd))
    }

    // --- queueIndexAt -----------------------------------------------------

    @Test
    fun queueIndexAtMapsQueueRowsAndRejectsChapterRows() {
        val items = listOf(songTrack("s0"), audiobookTrack("b1"), songTrack("s2"))
        val rows = items.buildDisplayRows(currentItemId = items[1].id)

        rows.forEachIndexed { visualIndex, row ->
            when (row) {
                is QueueDisplayRow.QueueItem ->
                    assertEquals(row.queueIndex, rows.queueIndexAt(visualIndex))
                is QueueDisplayRow.ChapterItem ->
                    assertNull(rows.queueIndexAt(visualIndex))
            }
        }
    }

    // --- upNext -----------------------------------------------------------

    @Test
    fun upNextStartsAtTheCurrentItemAndKeepsItsChapterRows() {
        val items = listOf(songTrack("s0"), audiobookTrack("b1"), songTrack("s2"))
        val rows = items.buildDisplayRows(currentItemId = "b1")

        val upNext = rows.upNext(currentItemId = "b1", limit = 100)

        assertEquals(rows.drop(1), upNext)
        assertEquals("b1", (upNext.first() as QueueDisplayRow.QueueItem).item.id)
        assertEquals(chapterNames.size, upNext.count { it is QueueDisplayRow.ChapterItem })
    }

    @Test
    fun upNextIsCappedAtTheLimit() {
        val items = (0 until 5).map { songTrack("s$it") }
        val rows = items.buildDisplayRows(currentItemId = "s1")

        val upNext = rows.upNext(currentItemId = "s1", limit = 2)

        assertEquals(listOf("s1", "s2"), upNext.map { (it as QueueDisplayRow.QueueItem).item.id })
    }

    @Test
    fun upNextWithoutACurrentItemStartsAtTheFirstRow() {
        val rows = listOf(songTrack("s0"), songTrack("s1")).buildDisplayRows(currentItemId = null)

        assertEquals(rows, rows.upNext(currentItemId = null, limit = 10))
        assertEquals(rows, rows.upNext(currentItemId = "gone", limit = 10))
    }

    @Test
    fun upNextWithANonPositiveLimitIsEmpty() {
        val rows = listOf(songTrack("s0")).buildDisplayRows(currentItemId = "s0")

        assertTrue(rows.upNext(currentItemId = "s0", limit = 0).isEmpty())
        assertTrue(rows.upNext(currentItemId = "s0", limit = -1).isEmpty())
    }

    // --- robustness -------------------------------------------------------

    @Test
    fun activeChapterWithNoChaptersReturnsNull() {
        assertNull(emptyList<Chapter>().activeChapter(position = 123.0))
    }

    @Test
    fun chapterRowKeysStayUniqueWhenServerPositionsCollide() {
        val collidingChapters = listOf(
            Chapter(position = 0, name = "A", start = 0.0, end = 10.0),
            Chapter(position = 0, name = "B", start = 10.0, end = 20.0),
        )
        val book = queueTrackOf(
            AppMediaItemFixtures.audiobook(chapters = listOf("A", "B"))
                .copy(chapters = collidingChapters),
            id = "b1",
        )

        val keys = listOf(book).buildDisplayRows(currentItemId = book.id).map { it.key }

        assertEquals(keys.size, keys.toSet().size, "Display row keys must be unique")
    }
}
