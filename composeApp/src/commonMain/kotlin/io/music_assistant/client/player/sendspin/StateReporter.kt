// Reporting interval inline-documented at use site.
@file:Suppress("MagicNumber")

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
 */
class StateReporter(
    private val messageDispatcher: MessageDispatcher,
    private val stateProvider: () -> SendspinState,
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

                    // Only report if we're still streaming
                    when (stateProvider()) {
                        is SendspinState.Synchronized,
                        is SendspinState.Buffering,
                        -> {
                            logger.d { "Periodic state report: SYNCHRONIZED" }
                            reportNow(PlayerStateValue.SYNCHRONIZED)
                        }

                        else -> {
                            // Not streaming — don't report
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
     *
     * Best-effort, like the periodic path above: a report racing a transport that is
     * going down must not escape into the caller's collector, which typically runs in
     * a supervised scope with no exception handler.
     */
    suspend fun reportNow(state: PlayerStateValue) {
        logger.d { "Reporting state: state=$state" }
        try {
            messageDispatcher.sendState(PlayerStateObject(state = state))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.w { "Dropped state report ($state): ${e.message}" }
        }
    }

    /**
     * Clean up resources.
     */
    fun close() {
        stop()
        supervisorJob.cancel()
    }
}
