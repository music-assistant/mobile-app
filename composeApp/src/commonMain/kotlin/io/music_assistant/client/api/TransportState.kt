package io.music_assistant.client.api

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonObject

sealed class TransportState {
    data object Disconnected : TransportState()
    data object Connecting : TransportState()
    data object Connected : TransportState()
    data class Reconnecting(val attempt: Int) : TransportState()
    data class Failed(val error: Exception) : TransportState()
}

interface Transport {
    val state: StateFlow<TransportState>
    val messages: SharedFlow<JsonObject>
    suspend fun send(message: JsonObject)
    fun connect()
    fun disconnect()

    /**
     * Probe connection liveness. Sends a WebSocket ping; if no activity within
     * [timeoutMs], transitions to [TransportState.Reconnecting] and reconnects.
     * Default no-op for transports with built-in keepalive (e.g. WebRTC ICE).
     */
    fun verifyConnection(timeoutMs: Long = 1000) {}
}
