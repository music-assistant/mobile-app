package io.music_assistant.sendspin.audio

import io.music_assistant.sendspin.wire.AudioChunk
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlin.concurrent.Volatile

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
    private val queue = ArrayDeque<AudioChunk>()
    private var lastConsumedTs = Long.MIN_VALUE
    private var bytes = 0

    var evicted = 0L
        private set

    fun offer(chunk: AudioChunk): Offer = synchronized(lock) {
        if (chunk.timestampMicros <= lastConsumedTs) return Offer.Stale
        val at = queue.binarySearchBy(chunk.timestampMicros) { it.timestampMicros }
        if (at >= 0) return Offer.Duplicate
        val index = -(at + 1)
        if (index == queue.size) queue.addLast(chunk) else queue.add(index, chunk)
        bytes += chunk.length
        var evictedNow = false
        while (bytes > capacityBytes && queue.size > 1) {
            bytes -= queue.removeLast().length
            evicted++
            evictedNow = true
        }
        if (evictedNow) Offer.Evicted else Offer.Queued
    }

    fun peek(): AudioChunk? = synchronized(lock) { queue.firstOrNull() }

    /** Removes and returns the head; it becomes the last consumed timestamp. */
    fun poll(): AudioChunk? = synchronized(lock) {
        queue.removeFirstOrNull()?.also {
            bytes -= it.length
            lastConsumedTs = it.timestampMicros
        }
    }

    /** Drops everything. [resetDedup] forgets the consumed timestamp too (discontinuity). */
    fun clear(resetDedup: Boolean) = synchronized(lock) {
        queue.clear()
        bytes = 0
        if (resetDedup) lastConsumedTs = Long.MIN_VALUE
    }

    val isEmpty: Boolean get() = synchronized(lock) { queue.isEmpty() }

    val size: Int get() = synchronized(lock) { queue.size }

    val byteCount: Int get() = synchronized(lock) { bytes }

    /** Server-time span queued ahead of the last consumed chunk, in microseconds. */
    val spanMicros: Long
        get() = synchronized(lock) {
            val last = queue.lastOrNull() ?: return 0L
            val head = if (lastConsumedTs != Long.MIN_VALUE) lastConsumedTs else queue.first().timestampMicros
            (last.timestampMicros - head).coerceAtLeast(0L)
        }
}
