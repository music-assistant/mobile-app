package io.music_assistant.sendspin.android

import android.os.SystemClock
import io.music_assistant.sendspin.api.MonotonicClock

/**
 * [MonotonicClock] over [SystemClock.elapsedRealtimeNanos], which ticks through deep
 * sleep. [io.music_assistant.sendspin.api.SystemMonotonicClock] is not guaranteed to.
 */
public object ElapsedRealtimeClock : MonotonicClock {
    private const val NANOS_PER_MICRO = 1_000L

    override fun nowMicros(): Long = SystemClock.elapsedRealtimeNanos() / NANOS_PER_MICRO
}
