package io.music_assistant.client.utils

import io.music_assistant.sendspin.api.MonotonicClock

/**
 * Sendspin's time base, shared by the player and the sink. Sleep-inclusive: a clock offset
 * learned before a doze still holds after it.
 */
val sendspinClock: MonotonicClock = MonotonicClock { monotonicMicros() }
