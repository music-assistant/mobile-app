@file:OptIn(ExperimentalTime::class)
// RTT/quality thresholds (50ms in microseconds, 5s timeout) inline-documented at use site.
@file:Suppress("MagicNumber")

package io.music_assistant.client.player.sendspin

import kotlin.concurrent.Volatile
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.time.TimeSource

enum class SyncQuality {
    GOOD,
    DEGRADED,
    LOST,
}

data class ClockStats(
    val offset: Long,
    val rtt: Long,
    val quality: SyncQuality,
)

class ClockSynchronizer {
    // Shared monotonic time base — created once so MessageDispatcher and AudioStreamManager
    // both read from the same epoch, keeping currentLocalTime in the same domain used
    // by the Kalman-smoothed offset.
    private val startMark = TimeSource.Monotonic.markNow()

    fun getCurrentTimeMicros(): Long = startMark.elapsedNow().inWholeMicroseconds

    // Clock synchronization state.
    // @Volatile so the audio consumer thread sees Kalman updates from the dispatcher
    // coroutine without synchronization. Long writes on the JVM are already atomic
    // under @Volatile, so no tearing.
    @Volatile
    private var offset: Long = 0 // μs (server - client), Kalman-smoothed after sample 2+
    private var drift: Double = 0.0 // μs/μs
    private var rawOffset: Long = 0
    private var rtt: Long = 0

    @Volatile
    private var quality: SyncQuality = SyncQuality.LOST
    private var lastSyncTime: Instant? = null
    private var lastSyncMicros: Long = 0
    private var sampleCount: Int = 0
    private val smoothingRate: Double = 0.1

    val currentOffset: Long get() = offset
    val currentQuality: SyncQuality get() = quality

    fun getStats() = ClockStats(offset, rtt, quality)

    fun processServerTime(
        clientTransmitted: Long, // t1
        serverReceived: Long, // t2
        serverTransmitted: Long, // t3
        clientReceived: Long, // t4
    ) {
        val (calculatedRtt, measuredOffset) = calculateOffset(
            clientTransmitted,
            serverReceived,
            serverTransmitted,
            clientReceived,
        )

        rtt = calculatedRtt
        rawOffset = measuredOffset
        lastSyncTime = Clock.System.now()

        // Discard invalid samples
        if (calculatedRtt !in 0..100_000) return

        // First sync: initialize
        if (sampleCount == 0) {
            offset = measuredOffset
            lastSyncMicros = clientReceived
            sampleCount++
            quality = SyncQuality.GOOD
            return
        }

        // Second sync: calculate initial drift
        if (sampleCount == 1) {
            val deltaTime = (clientReceived - lastSyncMicros).toDouble()
            if (deltaTime > 0) {
                drift = (measuredOffset - offset).toDouble() / deltaTime
            }
            offset = measuredOffset
            lastSyncMicros = clientReceived
            sampleCount++
            quality = SyncQuality.GOOD
            return
        }

        // Subsequent syncs: Kalman filter update
        val deltaTime = (clientReceived - lastSyncMicros).toDouble()
        if (deltaTime <= 0) return

        val predictedOffset = offset + (drift * deltaTime).toLong()
        val residual = measuredOffset - predictedOffset

        // Reject outliers
        if (kotlin.math.abs(residual) > 50_000) return

        // Update offset and drift
        offset = predictedOffset + (smoothingRate * residual).toLong()
        val driftCorrection = residual.toDouble() / deltaTime
        drift += smoothingRate * driftCorrection

        lastSyncMicros = clientReceived
        sampleCount++

        quality = if (calculatedRtt < 50_000) SyncQuality.GOOD else SyncQuality.DEGRADED
    }

    private fun calculateOffset(
        clientTx: Long,
        serverRx: Long,
        serverTx: Long,
        clientRx: Long,
    ): Pair<Long, Long> {
        val rtt = (clientRx - clientTx) - (serverTx - serverRx)
        val offset = ((serverRx - clientTx) + (serverTx - clientRx)) / 2
        return rtt to offset
    }

    // Server clock is ahead of local by [offset] microseconds (Kalman-smoothed).
    // Mapping is therefore: local = server − offset,  server = local + offset.
    // Using the smoothed [offset] — not per-sample raw values — keeps the audio
    // consumer's wall-clock gate stable across stream starts; each new sync
    // sample no longer throws network-jitter straight into the mapping.
    fun serverTimeToLocal(serverTime: Long): Long {
        if (sampleCount == 0) return 0
        return serverTime - offset
    }

    fun localTimeToServer(localTime: Long): Long {
        if (sampleCount == 0) return 0
        return localTime + offset
    }

    fun checkQuality(): SyncQuality {
        lastSyncTime?.let { lastSync ->
            val elapsed = (Clock.System.now() - lastSync).inWholeMilliseconds
            if (elapsed > 5000) {
                quality = SyncQuality.LOST
            }
        }
        return quality
    }

    fun reset() {
        offset = 0
        drift = 0.0
        rawOffset = 0
        rtt = 0
        quality = SyncQuality.LOST
        lastSyncTime = null
        lastSyncMicros = 0
        sampleCount = 0
    }
}
