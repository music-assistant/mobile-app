package io.music_assistant.sendspin.api

/**
 * Times a device playback head that advances in mixer-period steps, for
 * [SinkHandle.position]. A head read between steps is stale by up to one step
 * (about 20 ms on a normal Android mixer); stamping it with the read time makes
 * the scheduler's queue estimate a sawtooth of that size.
 *
 * [position] stamps a reading with the time the head was seen to move, so the
 * scheduler extrapolates across the step. The extrapolation is capped at the
 * duration of the last step: a head that stays still longer than that is
 * stalled (fresh track, underrun), and it is reported no older than one step.
 * Before the first step there is no extrapolation at all.
 *
 * Feed every extended reading through [position]; call [reset] together with
 * [MonotonicFrameCounter.reset]. Not thread-safe: one owner, the sink's
 * audio-thread caller.
 */
class SteppedHeadTracker(private val sampleRate: Int) {
    private var lastFrames = NONE
    private var movedAtMicros = 0L
    private var stepMicros = 0L

    fun position(framesPlayed: Long, nowMicros: Long): SinkPosition {
        if (framesPlayed != lastFrames) {
            val step = framesPlayed - lastFrames
            stepMicros = if (lastFrames == NONE || step < 0) 0L else step * MICROS_PER_SECOND / sampleRate
            lastFrames = framesPlayed
            movedAtMicros = nowMicros
        }
        return SinkPosition(framesPlayed, maxOf(movedAtMicros, nowMicros - stepMicros))
    }

    fun reset() {
        lastFrames = NONE
        stepMicros = 0L
    }

    private companion object {
        const val NONE = -1L
        const val MICROS_PER_SECOND = 1_000_000L
    }
}
