package io.music_assistant.sendspin.api

/**
 * Extends a device frame counter that wraps at 32 bits into a monotonic count,
 * for [SinkHandle.position]. Feed every raw reading through [extend]; call
 * [reset] whenever the device counter restarts (flush, rebuild). Not thread-safe:
 * one owner, the sink's audio-thread caller.
 *
 * Readings may come from more than one device source (a timestamp can lag the
 * playback head by a few milliseconds), so they arrive slightly out of order
 * around a wrap. Each reading is placed in the wrap epoch nearest to the highest
 * position seen so far, and an older reading never moves that anchor. This
 * assumes the counter is read far more often than once per half range (about
 * 12 hours at 48 kHz).
 */
class MonotonicFrameCounter {
    /** Highest extended position returned so far: the epoch anchor. */
    private var latest = 0L

    fun extend(raw: Long): Long {
        val low = raw and MASK
        var candidate = (latest and MASK.inv()) + low
        if (candidate - latest > HALF && candidate >= RANGE) {
            candidate -= RANGE // an older reading from the previous epoch (there is none before the first)
        } else if (latest - candidate > HALF) {
            candidate += RANGE // the counter wrapped since the anchor
        }
        if (candidate > latest) latest = candidate
        return candidate
    }

    fun reset() {
        latest = 0L
    }

    private companion object {
        const val MASK = 0xFFFF_FFFFL
        const val RANGE = 0x1_0000_0000L
        const val HALF = 0x8000_0000L
    }
}
