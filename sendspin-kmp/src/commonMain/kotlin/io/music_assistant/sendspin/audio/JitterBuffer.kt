package io.music_assistant.sendspin.audio

import io.music_assistant.sendspin.wire.AudioChunk
import io.music_assistant.sendspin.wire.StreamStartPlayer
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlin.concurrent.Volatile

/**
 * A buffered chunk and the format that was in effect when it arrived. The tag, not the
 * timestamp, decides the format: after a `stream/clear` with `from_timestamp`, the
 * server can resend chunks before a format boundary in the newer format.
 */
internal class QueuedChunk(val chunk: AudioChunk, val format: StreamStartPlayer)

/**
 * Encoded chunks ordered by server timestamp, byte-capped. Shared between the
 * session reader (offer) and the audio thread (poll).
 *
 * [offer] never suspends: a reader blocked on audio could not process the
 * `stream/clear` that would free the buffer. Over the cap, the furthest-future
 * chunk is evicted, which keeps continuity at the head. The server honours
 * the advertised capacity, so eviction is a safety net, not a mechanism.
 *
 * De-dup: chunks at or before the last consumed timestamp (a reconnect replay)
 * and exact-timestamp duplicates are dropped. [reset] clears that memory on a
 * discontinuity, so a legitimate timeline restart is not mistaken for a replay.
 */
internal class JitterBuffer(@Volatile var capacityBytes: Int) {
    enum class Offer { Queued, Stale, Duplicate, Evicted }

    private val lock = SynchronizedObject()
    private val queue = ArrayDeque<QueuedChunk>()
    private var lastConsumedTs = Long.MIN_VALUE
    private var bytes = 0

    /** Written under the lock, read by the audio thread for its stream stats. */
    @Volatile
    var evicted = 0L
        private set

    fun offer(chunk: AudioChunk, format: StreamStartPlayer): Offer = synchronized(lock) {
        if (chunk.timestampMicros <= lastConsumedTs) return Offer.Stale
        val at = queue.binarySearchBy(chunk.timestampMicros) { it.chunk.timestampMicros }
        if (at >= 0) return Offer.Duplicate
        val index = -(at + 1)
        val queued = QueuedChunk(chunk, format)
        if (index == queue.size) queue.addLast(queued) else queue.add(index, queued)
        bytes += chunk.length
        var evictedNow = false
        while (bytes > capacityBytes && queue.size > 1) {
            bytes -= queue.removeLast().chunk.length
            evicted++
            evictedNow = true
        }
        if (evictedNow) Offer.Evicted else Offer.Queued
    }

    fun peek(): QueuedChunk? = synchronized(lock) { queue.firstOrNull() }

    /**
     * Removes the head if it is still [expected]; it becomes the last consumed timestamp.
     * A clear between the caller's [peek] and this call must not cost the new head.
     */
    fun pollIf(expected: QueuedChunk): Boolean = synchronized(lock) {
        if (queue.firstOrNull() !== expected) return false
        queue.removeFirst()
        bytes -= expected.chunk.length
        lastConsumedTs = expected.chunk.timestampMicros
        true
    }

    /** Drops everything. [resetDedup] forgets the consumed timestamp too (discontinuity). */
    fun clear(resetDedup: Boolean) = synchronized(lock) {
        queue.clear()
        bytes = 0
        if (resetDedup) lastConsumedTs = Long.MIN_VALUE
    }

    /**
     * Drops the chunks at or after [fromTimestampMicros] and keeps the rest and the de-dup
     * memory. A resent chunk at or before the last consumed one is already in the sink, so
     * [offer] still rejects it as stale.
     */
    fun clearFrom(fromTimestampMicros: Long): Int = synchronized(lock) {
        var dropped = 0
        while (queue.lastOrNull()?.let { it.chunk.timestampMicros >= fromTimestampMicros } == true) {
            bytes -= queue.removeLast().chunk.length
            dropped++
        }
        dropped
    }

    val isEmpty: Boolean get() = synchronized(lock) { queue.isEmpty() }

    val size: Int get() = synchronized(lock) { queue.size }

    val byteCount: Int get() = synchronized(lock) { bytes }

    /** Server-time span queued ahead of the last consumed chunk, in microseconds. */
    val spanMicros: Long
        get() = synchronized(lock) {
            val last = queue.lastOrNull() ?: return 0L
            val head = if (lastConsumedTs != Long.MIN_VALUE) lastConsumedTs else queue.first().chunk.timestampMicros
            (last.chunk.timestampMicros - head).coerceAtLeast(0L)
        }
}
