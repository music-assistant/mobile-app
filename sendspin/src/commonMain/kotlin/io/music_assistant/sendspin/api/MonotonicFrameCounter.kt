package io.music_assistant.sendspin.api

/**
 * Extends a device frame counter that wraps at 32 bits into a monotonic count,
 * for [SinkHandle.position]. Feed every raw reading through [extend]; call
 * [reset] whenever the device counter restarts (flush, rebuild). Not thread-safe:
 * one owner, the sink's audio-thread caller.
 */
class MonotonicFrameCounter {
    private var last = 0L
    private var base = 0L

    fun extend(raw: Long): Long {
        val low = raw and MASK
        // A jump backwards by more than half the range is a wrap, not a rewind.
        if (low < last && last - low > HALF) base += RANGE
        last = low
        return base + low
    }

    fun reset() {
        last = 0L
        base = 0L
    }

    private companion object {
        const val MASK = 0xFFFF_FFFFL
        const val RANGE = 0x1_0000_0000L
        const val HALF = 0x8000_0000L
    }
}
