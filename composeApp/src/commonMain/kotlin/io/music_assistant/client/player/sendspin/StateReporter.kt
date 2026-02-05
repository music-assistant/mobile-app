package io.music_assistant.client.player.sendspin

import co.touchlab.kermit.Logger
import io.music_assistant.client.player.sendspin.model.PlayerStateObject
import io.music_assistant.client.player.sendspin.model.PlayerStateValue
import io.music_assistant.client.player.sendspin.protocol.MessageDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

/**
 * Handles periodic state reporting to the Sendspin server.
 * Reports player state (SYNCHRONIZED), volume, and mute status every 2 seconds.
 *
 * Separated from SendspinClient to follow Single Responsibility Principle.
 */
class StateReporter(
    private val messageDispatcher: MessageDispatcher,
    private val volumeProvider: () -> Int,
    private val mutedProvider: () -> Boolean,
    private val playbackStateProvider: () -> SendspinPlaybackState
) : CoroutineScope {

    private val logger = Logger.withTag("StateReporter")
    private val supervisorJob = SupervisorJob()

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Default + supervisorJob

    private var reportingJob: Job? = null

    /**
     * Start periodic state reporting (every 2 seconds).
     * Reports SYNCHRONIZED state with current volume/mute when streaming.
     */
    fun start() {
        logger.i { "Starting periodic state reporting" }
        reportingJob?.cancel()
        reportingJob = launch {
            while (isActive) {
                try {
                    // Wait before reporting (report every 2 seconds)
                    delay(2000)

                    // Only report if we're still streaming and synchronized
                    when (val state = playbackStateProvider()) {
                        SendspinPlaybackState.Synchronized,
                        is SendspinPlaybackState.Playing -> {
                            logger.d { "Periodic state report: SYNCHRONIZED" }
                            reportNow(PlayerStateValue.SYNCHRONIZED)
                        }
                        SendspinPlaybackState.Buffering -> {
                            logger.d { "Periodic state report: SYNCHRONIZED (buffering)" }
                            reportNow(PlayerStateValue.SYNCHRONIZED)
                        }
                        else -> {
                            // Idle or Error - don't report
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.e(e) { "Error in state reporting" }
                }
            }
        }
    }

    /**
     * Stop periodic state reporting.
     */
    fun stop() {
        logger.i { "Stopping periodic state reporting" }
        reportingJob?.cancel()
        reportingJob = null
    }

    /**
     * Send immediate state report to server (event-driven).
     * Used for volume/mute changes or initial sync.
     */
    suspend fun reportNow(state: PlayerStateValue) {
        val volume = volumeProvider()
        val muted = mutedProvider()

        val playerState = PlayerStateObject(
            state = state,
            volume = volume,
            muted = muted
        )

        logger.d { "Reporting state: state=$state, volume=$volume, muted=$muted" }
        messageDispatcher.sendState(playerState)
    }

    /**
     * Clean up resources.
     */
    fun close() {
        stop()
        supervisorJob.cancel()
    }
}
