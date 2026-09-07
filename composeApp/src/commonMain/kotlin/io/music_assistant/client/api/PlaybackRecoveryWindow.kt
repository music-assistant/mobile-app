package io.music_assistant.client.api

import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/** A short grace period for explicit Play while confirmed playback is still inactive. */
internal class PlaybackRecoveryWindow(private val clock: TimeSource = TimeSource.Monotonic) {
    private val requestedAt = MutableStateFlow<TimeMark?>(null)

    val isActive: Boolean get() = requestedAt.value?.elapsedNow()?.let { it < DURATION } == true

    fun request() {
        requestedAt.value = clock.markNow()
    }

    fun clear() {
        requestedAt.value = null
    }

    private companion object {
        val DURATION = 10.seconds
    }
}
