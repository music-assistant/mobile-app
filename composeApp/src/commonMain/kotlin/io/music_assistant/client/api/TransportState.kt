package io.music_assistant.client.api

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    /** Call [disconnect] first; unusable after. */
    fun close() {}
}

/**
 * The state of a transport. Each write calls [onChange] at once, on the thread that writes. A
 * collector of a `StateFlow` runs later on its own coroutine and can skip a value, so the session
 * could see a message before the `Connected` that came first, or miss a short drop. [onChange]
 * reads [value] itself, so concurrent writes still end on the latest value.
 */
internal class TransportStateFlow(private val onChange: () -> Unit) {
    private val flow = MutableStateFlow<TransportState>(TransportState.Disconnected)

    var value: TransportState
        get() = flow.value
        set(new) {
            flow.value = new
            onChange()
        }

    fun asStateFlow(): StateFlow<TransportState> = flow.asStateFlow()
}
