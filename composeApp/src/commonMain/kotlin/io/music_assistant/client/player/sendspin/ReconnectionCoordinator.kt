package io.music_assistant.client.player.sendspin

import co.touchlab.kermit.Logger
import io.music_assistant.client.player.sendspin.audio.AudioPipeline
import io.music_assistant.client.player.sendspin.connection.SendspinWsHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

/**
 * Stream recovery state machine for WebSocket reconnection coordination.
 * Replaces the boolean wasStreamingBeforeDisconnect flag with proper state management.
 */
sealed class StreamRecoveryState {
    /** No recovery needed - stream is idle or connection is stable */
    object Idle : StreamRecoveryState()

    /** Disconnect detected while streaming - waiting for reconnect */
    data class AwaitingReconnect(val wasStreaming: Boolean) : StreamRecoveryState()

    /** Reconnection in progress - attempting to restore stream */
    data class RecoveryInProgress(val attempt: Int) : StreamRecoveryState()

    /** Stream successfully restored after reconnection */
    object RecoverySuccess : StreamRecoveryState()

    /** Recovery failed - stream could not be restored within timeout */
    object RecoveryFailed : StreamRecoveryState()
}

/**
 * Coordinates WebSocket reconnection and stream restoration.
 * Manages recovery state machine and preserves audio buffer during brief disconnects.
 *
 * Separated from SendspinClient to follow Single Responsibility Principle.
 */
class ReconnectionCoordinator(
    private val wsHandler: SendspinWsHandler,
    private val audioPipeline: AudioPipeline,
    private val playbackStateProvider: () -> SendspinPlaybackState
) : CoroutineScope {

    private val logger = Logger.withTag("ReconnectionCoordinator")
    private val supervisorJob = SupervisorJob()

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Default + supervisorJob

    private val _recoveryState = MutableStateFlow<StreamRecoveryState>(StreamRecoveryState.Idle)
    val recoveryState: StateFlow<StreamRecoveryState> = _recoveryState.asStateFlow()

    private var recoveryTimeoutJob: Job? = null

    /**
     * Start monitoring WebSocket state for reconnection coordination.
     */
    fun start() {
        logger.i { "Starting reconnection coordinator" }
        launch {
            wsHandler.connectionState.collect { wsState ->
                handleWebSocketStateChange(wsState)
            }
        }
    }

    private suspend fun handleWebSocketStateChange(wsState: WebSocketState) {
        logger.d { "WebSocket state: $wsState, Recovery state: ${_recoveryState.value}" }

        when (wsState) {
            is WebSocketState.Reconnecting -> {
                val currentPlaybackState = playbackStateProvider()
                val isStreaming = isCurrentlyStreaming(currentPlaybackState)

                if (isStreaming) {
                    logger.i { "🔄 WS RECONNECTING: attempt=${wsState.attempt}, playbackState=$currentPlaybackState, preserving buffer" }
                    // DON'T call stopStream()! AudioPipeline will keep playing from buffer
                    _recoveryState.value = StreamRecoveryState.AwaitingReconnect(wasStreaming = true)
                } else {
                    logger.i { "🔄 WS RECONNECTING: attempt=${wsState.attempt}, NOT streaming, nothing to preserve" }
                    _recoveryState.value = StreamRecoveryState.AwaitingReconnect(wasStreaming = false)
                }
            }

            WebSocketState.Connected -> {
                val currentState = _recoveryState.value

                if (currentState is StreamRecoveryState.AwaitingReconnect && currentState.wasStreaming) {
                    logger.i { "Reconnected while streaming was active - waiting for server to resume" }
                    _recoveryState.value = StreamRecoveryState.RecoveryInProgress(attempt = 1)

                    // Server should auto-send StreamStart when player reconnects
                    // If it doesn't within 5 seconds, we'll timeout
                    startRecoveryTimeout()
                } else {
                    // Normal connection, no recovery needed
                    _recoveryState.value = StreamRecoveryState.Idle
                }
            }

            is WebSocketState.Error -> {
                // Check if this is a permanent error (max reconnect attempts exceeded)
                if (wsState.error.message?.contains("Failed to reconnect") == true) {
                    logger.e { "Connection failed permanently after max attempts" }
                    val currentState = _recoveryState.value
                    if (currentState is StreamRecoveryState.AwaitingReconnect ||
                        currentState is StreamRecoveryState.RecoveryInProgress
                    ) {
                        _recoveryState.value = StreamRecoveryState.RecoveryFailed
                        audioPipeline.stopStream()
                    }
                    cancelRecoveryTimeout()
                }
            }

            WebSocketState.Disconnected -> {
                // Only handle if this is NOT during reconnection
                val currentState = _recoveryState.value
                if (currentState !is StreamRecoveryState.AwaitingReconnect &&
                    currentState !is StreamRecoveryState.RecoveryInProgress
                ) {
                    logger.i { "WebSocket disconnected (explicit)" }
                    _recoveryState.value = StreamRecoveryState.Idle
                }
            }

            WebSocketState.Connecting -> {
                logger.d { "WebSocket connecting..." }
                // No state change needed
            }
        }
    }

    /**
     * Called by SendspinClient when stream starts successfully.
     * Marks recovery as successful if we were in recovery mode.
     */
    fun onStreamStarted() {
        val currentState = _recoveryState.value
        if (currentState is StreamRecoveryState.RecoveryInProgress) {
            logger.i { "✅ Stream recovery successful" }
            _recoveryState.value = StreamRecoveryState.RecoverySuccess
            cancelRecoveryTimeout()

            // Reset to Idle after brief delay
            launch {
                delay(1000)
                if (_recoveryState.value is StreamRecoveryState.RecoverySuccess) {
                    _recoveryState.value = StreamRecoveryState.Idle
                }
            }
        }
    }

    /**
     * Called by SendspinClient when stream is explicitly stopped.
     * Clears recovery state since stream is intentionally idle.
     */
    fun onStreamStopped() {
        logger.i { "Stream stopped - clearing recovery state" }
        _recoveryState.value = StreamRecoveryState.Idle
        cancelRecoveryTimeout()
    }

    private fun startRecoveryTimeout() {
        cancelRecoveryTimeout()
        recoveryTimeoutJob = launch {
            delay(5000) // 5 second timeout
            val currentState = _recoveryState.value
            if (currentState is StreamRecoveryState.RecoveryInProgress) {
                logger.w { "Stream restoration timed out - server didn't resume playback" }
                _recoveryState.value = StreamRecoveryState.RecoveryFailed
            }
        }
    }

    private fun cancelRecoveryTimeout() {
        recoveryTimeoutJob?.cancel()
        recoveryTimeoutJob = null
    }

    private fun isCurrentlyStreaming(playbackState: SendspinPlaybackState): Boolean {
        return playbackState is SendspinPlaybackState.Buffering ||
                playbackState is SendspinPlaybackState.Synchronized ||
                playbackState is SendspinPlaybackState.Playing
    }

    /**
     * Stop monitoring and cleanup resources.
     */
    fun stop() {
        logger.i { "Stopping reconnection coordinator" }
        cancelRecoveryTimeout()
        _recoveryState.value = StreamRecoveryState.Idle
        supervisorJob.cancel()
    }
}
