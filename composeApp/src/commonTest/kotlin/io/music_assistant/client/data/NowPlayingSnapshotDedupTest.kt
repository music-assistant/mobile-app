package io.music_assistant.client.data

import io.music_assistant.client.data.MainDataSource.NowPlayingSnapshot
import io.music_assistant.client.data.MainDataSource.NowPlayingSnapshot.Companion.ELAPSED_ANCHOR_EPSILON_S
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the dedup contract used to gate `MPNowPlayingInfoCenter` writes.
 *
 * The lock-screen bar is interpolated locally by iOS from the last
 * `(elapsed, timestamp, rate)` anchor. Writing the dict at our position-
 * tracker tick (500 ms) is fighting iOS rather than working with it; the
 * `distinctUntilChanged` gate in `MainDataSource` collapses sub-anchor
 * drift to a single anchor write per actually-visible change.
 *
 * This test pins the boundary cases so a future "I'll just simplify this"
 * doesn't accidentally restore the 2 Hz write-loop.
 */
class NowPlayingSnapshotDedupTest {
    private val sample = NowPlayingSnapshot.Active(
        title = "About Farewell",
        artist = "Alela Diane",
        album = "About Farewell",
        artworkUrl = "https://example.invalid/cover.jpg",
        duration = 240.0,
        elapsedTime = 30.0,
        isPlaying = true,
    )

    @Test
    fun identicalSnapshotsDedupe() {
        assertTrue(NowPlayingSnapshot.sameDictWriteWouldBe(sample, sample))
    }

    @Test
    fun titleChangeEmits() {
        assertFalse(
            NowPlayingSnapshot.sameDictWriteWouldBe(sample, sample.copy(title = "Different")),
        )
    }

    @Test
    fun artistChangeEmits() {
        assertFalse(
            NowPlayingSnapshot.sameDictWriteWouldBe(sample, sample.copy(artist = "Different")),
        )
    }

    @Test
    fun albumChangeEmits() {
        // Same title + artist with a different album is a legitimately different
        // item (single vs. album cut, remaster vs. original); the dict has to
        // refresh artwork.
        assertFalse(
            NowPlayingSnapshot.sameDictWriteWouldBe(sample, sample.copy(album = "Different")),
        )
    }

    @Test
    fun artworkUrlChangeEmits() {
        assertFalse(
            NowPlayingSnapshot.sameDictWriteWouldBe(
                sample,
                sample.copy(artworkUrl = "https://example.invalid/other.jpg"),
            ),
        )
    }

    @Test
    fun durationChangeEmits() {
        assertFalse(
            NowPlayingSnapshot.sameDictWriteWouldBe(sample, sample.copy(duration = 999.0)),
        )
    }

    @Test
    fun rateChangeEmits() {
        // Pause/play transitions are exactly the anchor iOS needs to stop or
        // start interpolating; never dedupe these.
        assertFalse(
            NowPlayingSnapshot.sameDictWriteWouldBe(sample, sample.copy(isPlaying = false)),
        )
    }

    @Test
    fun subSecondElapsedDriftDedupes() {
        // Position-tracker tick (500 ms) plus dispatch jitter — well under the
        // anchor epsilon. iOS's own interpolator covers this.
        assertTrue(
            NowPlayingSnapshot.sameDictWriteWouldBe(
                sample,
                sample.copy(elapsedTime = sample.elapsedTime!! + 0.7),
            ),
        )
    }

    @Test
    fun elapsedJumpAtThresholdEmits() {
        // A jump >= the epsilon is treated as a re-anchor (e.g. server-side
        // seek). Has to flush so iOS picks up the new base.
        assertFalse(
            NowPlayingSnapshot.sameDictWriteWouldBe(
                sample,
                sample.copy(elapsedTime = sample.elapsedTime!! + ELAPSED_ANCHOR_EPSILON_S),
            ),
        )
    }

    @Test
    fun elapsedJustUnderThresholdDedupes() {
        // Strict less-than on the comparison: just-under-threshold is dedup,
        // at-or-over-threshold is emit. Pin the boundary explicitly.
        assertTrue(
            NowPlayingSnapshot.sameDictWriteWouldBe(
                sample,
                sample.copy(elapsedTime = sample.elapsedTime!! + (ELAPSED_ANCHOR_EPSILON_S - 0.001)),
            ),
        )
    }

    @Test
    fun nullToValueElapsedEmits() {
        // Mid-pause-transition events arrive with `elapsed = null`; the next
        // event with a real value is a re-anchor. Has to flush.
        val nullElapsed = sample.copy(elapsedTime = null)
        assertFalse(NowPlayingSnapshot.sameDictWriteWouldBe(nullElapsed, sample))
    }

    @Test
    fun bothNullElapsedDedupes() {
        val nullElapsed = sample.copy(elapsedTime = null)
        assertTrue(NowPlayingSnapshot.sameDictWriteWouldBe(nullElapsed, nullElapsed))
    }

    @Test
    fun clearedDedupesWithItself() {
        assertTrue(NowPlayingSnapshot.sameDictWriteWouldBe(NowPlayingSnapshot.Cleared, NowPlayingSnapshot.Cleared))
    }

    @Test
    fun clearedToActiveEmits() {
        // Going from "no track" to "track": iOS needs the metadata anchor
        // before it can render anything.
        assertFalse(NowPlayingSnapshot.sameDictWriteWouldBe(NowPlayingSnapshot.Cleared, sample))
    }

    @Test
    fun activeToClearedEmits() {
        assertFalse(NowPlayingSnapshot.sameDictWriteWouldBe(sample, NowPlayingSnapshot.Cleared))
    }
}
