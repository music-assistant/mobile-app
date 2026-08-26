package io.music_assistant.client.data.model.client
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A chapter's fractional start must round UP to whole seconds, so an
 * integer-second seek lands inside the tapped chapter — never a fraction of a
 * second before it, which would briefly highlight the previous chapter.
 */
class ChapterSeekSecondsTest {
    @Test
    fun wholeSecondStartIsUnchanged() {
        assertEquals(100L, chapterSeekSeconds(100.0))
    }

    @Test
    fun fractionJustPastBoundaryRoundsUp() {
        assertEquals(101L, chapterSeekSeconds(100.1))
    }

    @Test
    fun fractionNearNextBoundaryRoundsUp() {
        assertEquals(101L, chapterSeekSeconds(100.9))
    }

    @Test
    fun zeroStartStaysZero() {
        assertEquals(0L, chapterSeekSeconds(0.0))
    }

    @Test
    fun timelineValueWithoutChapterKeepsTheAbsoluteTruncatingTarget() {
        // No chapter: the timeline is already absolute and must keep the
        // truncating target the server and the position tracker agree on.
        assertEquals(100L, (null as ResolvedChapter?).toAbsoluteSeekSeconds(100.9))
    }

    @Test
    fun timelineValueWithChapterMapsBackToAbsoluteAndRoundsUp() {
        val chapter = ResolvedChapter(
            chapter = Chapter(position = 1, name = "Ch2", start = 100.5, end = 200.5),
            start = 100.5,
            end = 200.5,
        )
        assertEquals(151L, chapter.toAbsoluteSeekSeconds(50.0))
    }
}
