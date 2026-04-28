package io.music_assistant.client.player.sendspin.audio

import co.touchlab.kermit.Logger
import io.music_assistant.client.player.sendspin.ClockSynchronizer
import io.music_assistant.client.player.sendspin.SyncQuality
import kotlinx.coroutines.sync.withLock
import kotlin.math.sqrt

/**
 * Manages adaptive buffering strategy based on network conditions.
 *
 * Uses network metrics (RTT, jitter, sync quality) to dynamically adjust buffer
 * thresholds, minimizing latency during good conditions while preventing underruns
 * during network degradation.
 *
 * Based on industry best practices from WebRTC NetEQ and RTP/VoIP adaptive playout algorithms.
 */
class AdaptiveBufferManager(
    private val clockSynchronizer: ClockSynchronizer,
) {
    private val logger = Logger.withTag("AdaptiveBufferManager")

    // Network statistics tracking
    private val rttHistory = CircularBuffer<Long>(capacity = 60) // Last 60 samples (~1 min)
    private val jitterEstimator = JitterEstimator()
    private var smoothedRTT: Double = 0.0
    private val rttSmoothingFactor = 0.1 // EWMA alpha

    // Drop rate tracking (last 100 chunks)
    private val dropWindow = CircularBuffer<Boolean>(capacity = 100)

    // Underrun tracking
    private val underrunTimestamps = CircularBuffer<Long>(capacity = 10)

    // Adaptation state
    private var adaptationState = AdaptationState(
        lastAdjustmentTime = 0L,
        lastDirection = Direction.NONE,
        consecutiveSameDirection = 0,
        cooldownUntil = 0L,
    )

    // Current thresholds
    private var _currentPrebufferThreshold = DEFAULT_PREBUFFER_THRESHOLD
    private var _currentEarlyThreshold = DEFAULT_EARLY_THRESHOLD
    private var _targetBufferDuration = IDEAL_BUFFER

    val currentPrebufferThreshold: Long get() = _currentPrebufferThreshold
    val currentLateThreshold: Long get() = LATE_THRESHOLD // Always static
    val currentEarlyThreshold: Long get() = _currentEarlyThreshold
    val targetBufferDuration: Long get() = _targetBufferDuration

    // Expose metrics for BufferState
    val currentSmoothedRTT: Double get() = smoothedRTT
    val currentJitter: Double get() = jitterEstimator.getStdDev()
    suspend fun getCurrentDropRate(): Double = if (dropWindow.size() > 0) {
        dropWindow.count { it } / dropWindow.size().toDouble()
    } else {
        0.0
    }

    /**
     * Updates network statistics with new RTT and quality measurements.
     * Call this on every sync message (approximately every 1 second).
     */
    // Use monotonic time for adaptation timing
    private val startMark = kotlin.time.TimeSource.Monotonic.markNow()

    private fun getCurrentTimeMicros(): Long {
        return startMark.elapsedNow().inWholeMicroseconds
    }

    /**
     * Updates network statistics with new RTT and quality measurements.
     * Call this on every sync message (approximately every 1 second).
     *
     * NOTE: `quality` is currently informational only — sync quality is consumed
     * directly from `clockSynchronizer.currentQuality` in [calculateNetworkStats].
     */
    @Suppress("UnusedParameter")
    suspend fun updateNetworkStats(rtt: Long, quality: SyncQuality) {
        // Update RTT history
        rttHistory.add(rtt)
        jitterEstimator.addSample(rtt)

        // Update smoothed RTT (EWMA)
        if (smoothedRTT == 0.0) {
            smoothedRTT = rtt.toDouble()
        } else {
            smoothedRTT = rttSmoothingFactor * rtt + (1 - rttSmoothingFactor) * smoothedRTT
        }
    }

    /**
     * Records that a chunk was dropped due to being too late.
     */
    suspend fun recordChunkDropped() {
        dropWindow.add(true)
    }

    /**
     * Records that a chunk was successfully played.
     */
    suspend fun recordChunkPlayed() {
        dropWindow.add(false)
    }

    /**
     * Records an underrun event with its timestamp.
     */
    suspend fun recordUnderrun(timestamp: Long) {
        underrunTimestamps.add(timestamp)
    }

    /**
     * Main adaptation logic - updates thresholds based on network conditions.
     * Call this periodically (e.g., every 5 seconds).
     */
    suspend fun updateThresholds(currentTime: Long) {
        // Check if we're in cooldown
        if (currentTime < adaptationState.cooldownUntil) {
            return
        }

        val stats = calculateNetworkStats()
        val shouldIncrease = shouldIncreaseBuffer(stats, currentTime)
        val shouldDecrease = shouldDecreaseBuffer(stats, currentTime)

        when {
            shouldIncrease -> increaseBuffer(stats, currentTime)
            shouldDecrease -> decreaseBuffer(stats, currentTime)
        }
    }

    private suspend fun calculateNetworkStats(): NetworkStats {
        val dropRate = if (dropWindow.size() > 0) {
            dropWindow.count { it } / dropWindow.size().toDouble()
        } else {
            0.0
        }

        // Count underruns in last 60 seconds
        val sixtySecondsAgo = getCurrentTimeMicros() - 60_000_000L
        val recentUnderruns = underrunTimestamps.count { timestamp ->
            timestamp > sixtySecondsAgo
        }

        return NetworkStats(
            smoothedRTT = smoothedRTT,
            rttStdDev = jitterEstimator.getStdDev(),
            syncQuality = clockSynchronizer.currentQuality,
            dropRate = dropRate,
            recentUnderruns = recentUnderruns,
        )
    }

    @Suppress("UnusedParameter") // currentTime kept symmetric with shouldDecreaseBuffer; reserved for time-based gating
    private suspend fun shouldIncreaseBuffer(stats: NetworkStats, currentTime: Long): Boolean {
        // Immediate increase conditions
        if (stats.recentUnderruns > 0) return true
        if (stats.dropRate > DROP_RATE_THRESHOLD) return true
        if (stats.syncQuality == SyncQuality.LOST) return true

        // Gradual increase conditions
        val rttIncreaseRatio = if (rttHistory.size() >= 10) {
            val recentAvg = rttHistory.takeLast(10).average()
            val olderAvg = rttHistory.takeFirst(10).average()
            if (olderAvg > 0) recentAvg / olderAvg else 1.0
        } else {
            1.0
        }

        if (rttIncreaseRatio > RTT_INCREASE_RATIO) return true
        if (stats.rttStdDev > HIGH_JITTER_THRESHOLD) return true

        return false
    }

    private fun shouldDecreaseBuffer(stats: NetworkStats, currentTime: Long): Boolean {
        // Never decrease if recent problems
        if (stats.recentUnderruns > 0) return false
        if (stats.dropRate > 0.0) return false
        if (stats.syncQuality != SyncQuality.GOOD) return false

        // Check sustained good conditions (rest unchanged)
        val timeSinceLastIncrease = currentTime - adaptationState.lastAdjustmentTime
        if (adaptationState.lastDirection == Direction.INCREASE &&
            timeSinceLastIncrease < MIN_INTERVAL_AFTER_INCREASE_US
        ) {
            return false
        }

        // Only decrease if conditions have been good and we're over-buffering
        if (smoothedRTT < LOW_RTT_THRESHOLD_US && stats.rttStdDev < LOW_JITTER_THRESHOLD_US) {
            if (_targetBufferDuration > IDEAL_BUFFER * OVER_BUFFER_FACTOR) {
                return true
            }
        }

        return false
    }

    private suspend fun increaseBuffer(stats: NetworkStats, currentTime: Long) {
        val newTarget = calculateTargetBuffer(stats)
        val increaseMagnitude = if (stats.recentUnderruns > 0) {
            // Aggressive increase on underrun
            (newTarget - _targetBufferDuration).coerceAtLeast(100_000L)
        } else {
            // Gradual increase otherwise
            (newTarget - _targetBufferDuration).coerceAtLeast(50_000L)
        }

        _targetBufferDuration = (_targetBufferDuration + increaseMagnitude)
            .coerceIn(MIN_BUFFER, MAX_BUFFER)

        updateDerivedThresholds()

        adaptationState = AdaptationState(
            lastAdjustmentTime = currentTime,
            lastDirection = Direction.INCREASE,
            consecutiveSameDirection = if (adaptationState.lastDirection == Direction.INCREASE) {
                adaptationState.consecutiveSameDirection + 1
            } else {
                1
            },
            cooldownUntil = currentTime + INCREASE_COOLDOWN,
        )

        logger.i {
            "Buffer increased: target=${_targetBufferDuration / 1000}ms, " +
                    "prebuffer=${_currentPrebufferThreshold / 1000}ms " +
                    "(RTT=${smoothedRTT / 1000}ms, jitter=${stats.rttStdDev / 1000}ms, " +
                    "dropRate=${(stats.dropRate * 100).toInt()}%)"
        }
    }

    @Suppress("UnusedParameter") // stats kept symmetric with increaseBuffer; reserved for stats-driven decrement
    private suspend fun decreaseBuffer(stats: NetworkStats, currentTime: Long) {
        // Decrease gradually toward ideal
        val decreaseMagnitude = (_targetBufferDuration - IDEAL_BUFFER) / 4

        _targetBufferDuration = (_targetBufferDuration - decreaseMagnitude)
            .coerceAtLeast(IDEAL_BUFFER)

        updateDerivedThresholds()

        adaptationState = AdaptationState(
            lastAdjustmentTime = currentTime,
            lastDirection = Direction.DECREASE,
            consecutiveSameDirection = if (adaptationState.lastDirection == Direction.DECREASE) {
                adaptationState.consecutiveSameDirection + 1
            } else {
                1
            },
            cooldownUntil = currentTime + DECREASE_COOLDOWN,
        )

        logger.i {
            "Buffer decreased: target=${_targetBufferDuration / 1000}ms, " +
                    "prebuffer=${_currentPrebufferThreshold / 1000}ms"
        }
    }

    private suspend fun calculateTargetBuffer(stats: NetworkStats): Long {
        // Base component: cover round-trip time
        val rttComponent = stats.smoothedRTT * 2.0

        // Jitter component: cover variance (4 standard deviations = 99.99% coverage)
        val jitterComponent = stats.rttStdDev * 4.0

        // Quality multiplier
        val qualityMultiplier = when (stats.syncQuality) {
            SyncQuality.GOOD -> 1.0
            SyncQuality.DEGRADED -> 1.5
            SyncQuality.LOST -> 2.5
        }

        // Drop rate penalty (exponential backoff on drops)
        val dropRatePenalty = if (stats.dropRate > 0) {
            50_000L * (1 shl ((stats.dropRate * 10).toInt().coerceAtMost(5)))
        } else {
            0L
        }

        val targetBuffer =
            ((rttComponent + jitterComponent) * qualityMultiplier).toLong() + dropRatePenalty

        return targetBuffer.coerceIn(MIN_BUFFER, MAX_BUFFER)
    }

    private fun updateDerivedThresholds() {
        // Update prebuffer based on target buffer (start playing at 50% of target)
        val basePrebuffer = _targetBufferDuration / 2
        _currentPrebufferThreshold = basePrebuffer.coerceIn(MIN_PREBUFFER, MAX_PREBUFFER)

        // Update early threshold (target buffer + 100ms headroom, was +500ms)
        _currentEarlyThreshold = (_targetBufferDuration + EARLY_THRESHOLD_HEADROOM_US)
            .coerceIn(MIN_EARLY_THRESHOLD, MAX_EARLY_THRESHOLD)
    }

    suspend fun reset() {
        logger.i { "Resetting adaptive buffer manager" }
        rttHistory.clear()
        jitterEstimator.reset()
        smoothedRTT = 0.0
        dropWindow.clear()
        underrunTimestamps.clear()
        _currentPrebufferThreshold = DEFAULT_PREBUFFER_THRESHOLD
        _currentEarlyThreshold = DEFAULT_EARLY_THRESHOLD
        _targetBufferDuration = IDEAL_BUFFER
        adaptationState = AdaptationState(0L, Direction.NONE, 0, 0L)
    }

    companion object {
        // Static late threshold (correctness requirement for multi-room sync)
        const val LATE_THRESHOLD = 100_000L // 100ms

        // Default thresholds (used before adaptation kicks in)
        const val DEFAULT_PREBUFFER_THRESHOLD =
            75_000L // 75ms (was 200ms - optimized for lower latency)
        const val DEFAULT_EARLY_THRESHOLD = 200_000L // 200ms (was 1s - optimized for lower latency)

        // Bounds
        const val MIN_PREBUFFER = 50_000L // 50ms (was 100ms - optimized for lower latency)
        const val MAX_PREBUFFER = 400_000L // 400ms (was 800ms - optimized for lower latency)
        const val MIN_EARLY_THRESHOLD = 100_000L // 100ms (was 500ms - optimized for lower latency)
        const val MAX_EARLY_THRESHOLD = 1_000_000L // 1s (was 3s - optimized for lower latency)
        const val MIN_BUFFER = 200_000L // 200ms
        const val MAX_BUFFER = 2_000_000L // 2s
        const val IDEAL_BUFFER = 300_000L // 300ms

        // Cooldown periods
        const val INCREASE_COOLDOWN = 2_000_000L // 2s
        const val DECREASE_COOLDOWN = 30_000_000L // 30s

        // Trigger thresholds
        const val DROP_RATE_THRESHOLD = 0.05 // 5%
        const val RTT_INCREASE_RATIO = 1.5 // 50% increase
        const val HIGH_JITTER_THRESHOLD = 50_000.0 // 50ms std dev

        // Decrease guards
        const val MIN_INTERVAL_AFTER_INCREASE_US = 60_000_000L // 60s — wait this long after an increase before decreasing
        const val LOW_RTT_THRESHOLD_US = 30_000L // 30ms — RTT must be below this to consider decrease
        const val LOW_JITTER_THRESHOLD_US = 10_000L // 10ms — jitter must be below this too
        const val OVER_BUFFER_FACTOR = 1.5 // decrease only when target > IDEAL * this

        // Derived-threshold tuning
        const val EARLY_THRESHOLD_HEADROOM_US = 100_000L // 100ms headroom over target buffer
    }
}

/**
 * Circular buffer with fixed capacity.
 * When full, oldest elements are overwritten.
 * Thread-safe implementation to prevent ConcurrentModificationException.
 */
/**
 * Circular buffer with fixed capacity.
 * When full, oldest elements are overwritten.
 * Thread-safe implementation using Mutex.
 */
class CircularBuffer<T>(private val capacity: Int) {
    private val buffer = ArrayList<T>(capacity)
    private var writeIndex = 0
    private val mutex = kotlinx.coroutines.sync.Mutex()

    suspend fun add(item: T) = mutex.withLock {
        if (buffer.size < capacity) {
            buffer.add(item)
        } else {
            buffer[writeIndex] = item
        }
        writeIndex = (writeIndex + 1) % capacity
    }

    suspend fun size(): Int = mutex.withLock { buffer.size }

    suspend fun takeLast(n: Int): List<T> = mutex.withLock {
        val count = n.coerceAtMost(buffer.size)
        val result = mutableListOf<T>()
        for (i in 0 until count) {
            val index = (writeIndex - 1 - i + buffer.size * 2) % buffer.size
            result.add(0, buffer[index])
        }
        result
    }

    suspend fun takeFirst(n: Int): List<T> = mutex.withLock {
        val count = n.coerceAtMost(buffer.size)
        val startIndex = if (buffer.size < capacity) 0 else writeIndex
        val result = mutableListOf<T>()
        for (i in 0 until count) {
            val index = (startIndex + i) % buffer.size
            result.add(buffer[index])
        }
        result
    }

    suspend fun count(predicate: (T) -> Boolean): Int = mutex.withLock {
        buffer.count(predicate)
    }

    suspend fun clear() = mutex.withLock {
        buffer.clear()
        writeIndex = 0
    }
}

/**
 * Estimates jitter (RTT variance) using Welford's online algorithm.
 * Computes running mean and standard deviation without storing all samples.
 */
class JitterEstimator {
    private var count = 0
    private var mean = 0.0
    private var m2 = 0.0 // Sum of squared differences from mean

    fun addSample(rtt: Long) {
        count++
        val delta = rtt.toDouble() - mean
        mean += delta / count
        val delta2 = rtt.toDouble() - mean
        m2 += delta * delta2
    }

    fun getStdDev(): Double {
        if (count < 2) return 0.0
        return sqrt(m2 / (count - 1))
    }

    fun getMean(): Double = mean

    fun reset() {
        count = 0
        mean = 0.0
        m2 = 0.0
    }
}

/**
 * Network statistics aggregated for adaptation decisions.
 */
data class NetworkStats(
    val smoothedRTT: Double,        // EWMA of RTT in microseconds
    val rttStdDev: Double,          // Std dev of RTT (jitter estimate) in microseconds
    val syncQuality: SyncQuality,   // Current sync quality state
    val dropRate: Double,           // Proportion of dropped chunks [0.0, 1.0]
    val recentUnderruns: Int,        // Count in last 60 seconds
)

/**
 * Tracks adaptation state for hysteresis and cooldown management.
 */
data class AdaptationState(
    val lastAdjustmentTime: Long,      // Microseconds since start
    val lastDirection: Direction,       // INCREASE, DECREASE, NONE
    val consecutiveSameDirection: Int,  // Count of same-direction adjustments
    val cooldownUntil: Long,            // Don't adjust before this time
)

/**
 * Direction of last buffer adjustment.
 */
enum class Direction {
    INCREASE,
    DECREASE,
    NONE,
}
