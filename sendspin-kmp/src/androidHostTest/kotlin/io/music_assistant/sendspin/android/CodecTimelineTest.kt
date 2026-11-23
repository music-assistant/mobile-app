package io.music_assistant.sendspin.android

import kotlin.test.Test
import kotlin.test.assertEquals

class CodecTimelineTest {
    private val timeline = CodecTimeline(sampleRate = 48_000)

    @Test
    fun presentationTimesCountSamplesWithoutGaps() {
        assertEquals(0L, timeline.queue(captureMicros = 5_000_000, samples = 960))
        // One second later on the capture clock, after a stall: the codec still sees 20 ms.
        assertEquals(20_000L, timeline.queue(captureMicros = 6_020_000, samples = 960))
    }

    @Test
    fun outputTimesKeepTheCaptureGap() {
        timeline.queue(captureMicros = 5_000_000, samples = 960)
        timeline.queue(captureMicros = 5_020_000, samples = 960)
        timeline.queue(captureMicros = 6_040_000, samples = 960)

        assertEquals(5_000_000L, timeline.captureTimeOf(0))
        assertEquals(5_030_000L, timeline.captureTimeOf(30_000))
        assertEquals(6_040_000L, timeline.captureTimeOf(40_000))
        assertEquals(6_050_000L, timeline.captureTimeOf(50_000))
    }

    @Test
    fun aBlockThatStartsBeforeTheGapTakesTheTimeOfItsFirstSample() {
        timeline.queue(captureMicros = 1_000_000, samples = 4_800)
        timeline.queue(captureMicros = 3_000_000, samples = 4_800)

        assertEquals(1_050_000L, timeline.captureTimeOf(50_000))
        assertEquals(3_000_000L, timeline.captureTimeOf(100_000))
    }

    @Test
    fun timesStayRightAfterManyGaps() {
        var capture = 0L
        repeat(1_000) { i ->
            timeline.queue(capture, samples = 960)
            capture += if (i % 100 == 99) 1_020_000 else 20_000
        }
        // Block 999 was captured after nine stalls of one second each.
        assertEquals(999 * 20_000L + 9 * 1_000_000L, timeline.captureTimeOf(999 * 20_000L))
    }
}
