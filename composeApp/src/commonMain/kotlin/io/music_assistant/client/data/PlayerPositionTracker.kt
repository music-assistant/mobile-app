package io.music_assistant.client.data

import io.music_assistant.client.utils.currentTimeMillis
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive

/**
 * Single source of truth for "what elapsed-time is each queue at, right now".
 *
 * Server events (`QueueTimeUpdatedEvent`, `QueueAddedEvent`, ...) write
 * anchors here. Play/pause transitions snapshot the interpolated position so
 * the pause duration doesn't fold into the next forward step. Consumers
 * (in-app slider, MediaSession writes for AA + notification, iOS NowPlaying,
 * audiobook chapter logic) all read the same source — synchronously via
 * [effectiveSec] or as a smoothly-ticking flow via [observe].
 *
 * No background tick: the cold [observe] flow ticks at 500 ms only while a
 * collector is attached AND the queue is playing. Sync reads are O(1) map
 * lookups — no allocations, no ipc.
 */
class PlayerPositionTracker {
    /** Anchor data for a single queue. */
    data class Anchor(
        val elapsedSec: Double,
        val wallMs: Long,
        val isPlaying: Boolean,
        val durationSec: Double?,
    ) {
        /** Position right now: anchor + wall-time elapsed since anchor (capped at duration). */
        fun effectiveNow(): Double {
            if (!isPlaying) return elapsedSec
            val advanced = elapsedSec + (currentTimeMillis() - wallMs) / 1000.0
            return durationSec?.let { advanced.coerceAtMost(it) } ?: advanced
        }
    }

    private val anchors = MutableStateFlow<Map<String, Anchor>>(emptyMap())

    /**
     * Server-anchored update. Pass `isPlaying`/`durationSec` when known;
     * `null` preserves the existing values (useful for `QueueTimeUpdatedEvent`
     * which only carries the new elapsed value).
     */
    fun setAnchor(
        queueId: String,
        elapsedSec: Double,
        isPlaying: Boolean? = null,
        durationSec: Double? = null,
    ) {
        anchors.update { existing ->
            val current = existing[queueId]
            existing + (
                queueId to Anchor(
                    elapsedSec = elapsedSec,
                    wallMs = currentTimeMillis(),
                    isPlaying = isPlaying ?: current?.isPlaying ?: false,
                    durationSec = durationSec ?: current?.durationSec,
                )
            )
        }
    }

    /**
     * Play/pause transition. Snapshots the current interpolated position as
     * the new anchor so neither the slider nor MediaSession sees a jump:
     * - Pausing: anchor advances to "where we were", isPlaying=false → static.
     * - Resuming: same anchor, wallMs reset to now, isPlaying=true → forward.
     */
    fun setPlaying(queueId: String, isPlaying: Boolean) {
        anchors.update { existing ->
            val current = existing[queueId] ?: return@update existing
            if (current.isPlaying == isPlaying) return@update existing
            existing + (
                queueId to current.copy(
                    elapsedSec = current.effectiveNow(),
                    wallMs = currentTimeMillis(),
                    isPlaying = isPlaying,
                )
            )
        }
    }

    /** O(1) read of latest interpolated position. */
    fun effectiveSec(queueId: String): Double? = anchors.value[queueId]?.effectiveNow()

    /**
     * Cold flow of interpolated position. Emits immediately on subscription,
     * then either:
     * - re-emits on every anchor change (track flip, seek, server scrub,
     *   pause/resume), AND
     * - ticks at 500 ms while the queue is playing.
     *
     * Stops ticking when the queue is paused (waits for the next anchor
     * change). Cancels cleanly when the collector unsubscribes.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe(queueId: String): Flow<Double> = anchors
        .map { it[queueId] }
        .distinctUntilChanged()
        .flatMapLatest { anchor ->
            if (anchor == null) {
                flowOf(0.0)
            } else {
                flow {
                    while (currentCoroutineContext().isActive) {
                        emit(anchor.effectiveNow())
                        if (!anchor.isPlaying) break
                        delay(TICK_MS)
                    }
                }
            }
        }

    /** Drop a queue's anchor (e.g., when the queue is removed). */
    fun remove(queueId: String) {
        anchors.update { it - queueId }
    }

    /** Clear all anchors (disconnect / reset). */
    fun clear() {
        anchors.update { emptyMap() }
    }

    private companion object {
        const val TICK_MS = 500L
    }
}
