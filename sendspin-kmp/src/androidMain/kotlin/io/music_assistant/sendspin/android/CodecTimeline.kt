package io.music_assistant.sendspin.android

/**
 * Presentation times for a codec that counts samples, and the way back to capture times.
 * [queue] gives each input the time of its position in a gapless count of samples, and keeps
 * where that position was captured. [captureTimeOf] turns an output presentation time into
 * the capture time of that sample, so a gap in the capture stays a gap in the output times.
 * Single-threaded, like the encoder.
 */
internal class CodecTimeline(private val sampleRate: Int) {
    private class Segment(val firstSample: Long, val captureMicros: Long)

    private val segments = ArrayDeque<Segment>()
    private var queuedSamples = 0L

    /** The presentation time for [samples] captured from [captureMicros] on. */
    fun queue(captureMicros: Long, samples: Int): Long {
        val first = queuedSamples
        val last = segments.lastOrNull()
        // A contiguous input extends the last segment; only a gap or a jump needs its own.
        if (last == null || last.captureMicros + micros(first - last.firstSample) != captureMicros) {
            segments.addLast(Segment(first, captureMicros))
        }
        queuedSamples += samples
        return micros(first)
    }

    /** The capture time of the sample at [presentationMicros]. Older segments are let go. */
    fun captureTimeOf(presentationMicros: Long): Long {
        val sample = (presentationMicros * sampleRate + MICROS_PER_SECOND / 2) / MICROS_PER_SECOND
        while (segments.size > 1 && segments[1].firstSample <= sample) segments.removeFirst()
        val segment = segments.firstOrNull() ?: return presentationMicros
        return segment.captureMicros + micros(sample - segment.firstSample)
    }

    private fun micros(samples: Long): Long = samples * MICROS_PER_SECOND / sampleRate

    private companion object {
        const val MICROS_PER_SECOND = 1_000_000L
    }
}
