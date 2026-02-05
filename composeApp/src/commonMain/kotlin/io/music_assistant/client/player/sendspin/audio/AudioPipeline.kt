package io.music_assistant.client.player.sendspin.audio

import io.music_assistant.client.player.sendspin.BufferState
import io.music_assistant.client.player.sendspin.model.StreamStartPlayer
import kotlinx.coroutines.flow.StateFlow

/**
 * Abstraction for the audio playback pipeline.
 * Decouples SendspinClient from concrete AudioStreamManager implementation,
 * enabling testing and future alternative audio backends.
 */
interface AudioPipeline {
    /**
     * Start audio streaming with the given configuration.
     * Initializes decoder, configures output, and begins playback loop.
     */
    suspend fun startStream(config: StreamStartPlayer)

    /**
     * Stop audio streaming and release resources.
     * Playback can be restarted with another startStream() call.
     */
    suspend fun stopStream()

    /**
     * Clear buffered audio chunks without stopping the stream.
     * Used when server sends stream/clear command.
     */
    suspend fun clearStream()

    /**
     * Process binary audio message from server.
     * Decodes chunk and adds to playback buffer.
     */
    suspend fun processBinaryMessage(data: ByteArray)

    /**
     * Clean up all resources.
     * Pipeline cannot be reused after close() is called.
     */
    fun close()

    /**
     * Current buffer state (buffered duration, underruns, adaptive metrics).
     * Updated periodically for UI display.
     */
    val bufferState: StateFlow<BufferState>

    /**
     * Current playback position in microseconds.
     * Updated by playback thread.
     */
    val playbackPosition: StateFlow<Long>

    /**
     * Stream errors (e.g., audio output disconnected).
     * Emits non-null when error occurs, then resets to null.
     */
    val streamError: StateFlow<Throwable?>
}
