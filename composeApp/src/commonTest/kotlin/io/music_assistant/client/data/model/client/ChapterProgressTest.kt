package io.music_assistant.client.data.model.client

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Mirrors the web frontend's tests for `resolveCurrentChapter`
 * (frontend tests/helpers/chapters.test.ts) so both clients agree on
 * chapter-boundary semantics.
 */
class ChapterProgressTest {
    private val chapters = listOf(
        Chapter(position = 1, name = "Intro", start = 0.0, end = 60.0),
        Chapter(position = 2, name = "Middle", start = 60.0, end = 120.0),
        Chapter(position = 3, name = "End", start = 120.0, end = null),
    )

    @Test
    fun resolvesChapterRangesAsHalfOpenIntervals() {
        assertEquals("Intro", resolveCurrentChapter(chapters, 0.0, 180.0)?.chapter?.name)
        assertEquals("Middle", resolveCurrentChapter(chapters, 60.0, 180.0)?.chapter?.name)
    }

    @Test
    fun usesMediaDurationForFinalOpenEndedChapter() {
        val resolved = resolveCurrentChapter(chapters, 150.0, 180.0)
        assertEquals(120.0, resolved?.start)
        assertEquals(180.0, resolved?.end)
        assertEquals(60.0, resolved?.duration)
    }

    @Test
    fun keepsOpenEndedFinalChapterActiveWithoutMediaDuration() {
        val resolved = resolveCurrentChapter(chapters, 150.0, null)
        assertEquals(120.0, resolved?.start)
        assertEquals(Double.POSITIVE_INFINITY, resolved?.end)
    }

    @Test
    fun keepsFinalChapterActiveAtExactMediaCompletion() {
        val resolved = resolveCurrentChapter(chapters, 180.0, 180.0)
        assertEquals(chapters[2], resolved?.chapter)
        assertEquals(120.0, resolved?.start)
        assertEquals(180.0, resolved?.end)
    }

    @Test
    fun returnsNullOutsideChapterRanges() {
        assertNull(resolveCurrentChapter(chapters, -1.0, 180.0))
        assertNull(resolveCurrentChapter(chapters, null, 180.0))
    }

    @Test
    fun returnsNullForMissingOrEmptyChapters() {
        assertNull(resolveCurrentChapter(null, 10.0, 180.0))
        assertNull(resolveCurrentChapter(emptyList(), 10.0, 180.0))
    }

    @Test
    fun resolvesChaptersByStartTimeWhenMetadataIsUnsorted() {
        val unsorted = listOf(chapters[2], chapters[0], chapters[1])
        assertEquals("Middle", resolveCurrentChapter(unsorted, 75.0, 180.0)?.chapter?.name)
    }

    @Test
    fun derivesEndFromNextChapterStartWhenChapterHasNoEnd() {
        val openEnded = listOf(
            Chapter(position = 1, name = "One", start = 0.0, end = null),
            Chapter(position = 2, name = "Two", start = 90.0, end = null),
        )
        val resolved = resolveCurrentChapter(openEnded, 30.0, 300.0)
        assertEquals("One", resolved?.chapter?.name)
        assertEquals(90.0, resolved?.end)
    }

    @Test
    fun skipsZeroLengthChapters() {
        val degenerate = listOf(
            Chapter(position = 1, name = "Empty", start = 60.0, end = 60.0),
            Chapter(position = 2, name = "Real", start = 60.0, end = 120.0),
        )
        assertEquals("Real", resolveCurrentChapter(degenerate, 60.0, 180.0)?.chapter?.name)
    }

    @Test
    fun mapsBetweenAbsoluteAndRelativePositions() {
        val resolved = resolveCurrentChapter(chapters, 75.0, 180.0)!!
        assertEquals(15.0, resolved.relativeSec(75.0))
        assertEquals(75.0, resolved.absoluteSec(15.0))
        // Clamped to the chapter bounds.
        assertEquals(0.0, resolved.relativeSec(30.0))
        assertEquals(120.0, resolved.absoluteSec(999.0))
    }
}
