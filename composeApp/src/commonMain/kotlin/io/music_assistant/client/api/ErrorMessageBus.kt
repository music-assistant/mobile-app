package io.music_assistant.client.api

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * App-wide bus for user-visible error messages emitted from non-UI layers
 * (currently: RPC error responses from the server).
 *
 * Backed by a buffered [Channel] rather than a `SharedFlow`, because
 * `MutableSharedFlow.tryEmit` with `replay = 0` drops values when no
 * subscriber is active — and the UI collector only exists while
 * `MainNavigationRoot` is composed. Channels buffer until consumed,
 * so an error emitted before the collector attaches still gets delivered.
 *
 * Single-consumer by construction (only `MainNavigationRoot` collects).
 */
class ErrorMessageBus {
    private val channel = Channel<String>(capacity = Channel.BUFFERED)
    val messages = channel.receiveAsFlow()

    /** Non-suspending — safe to call from any context. */
    fun emit(message: String) {
        channel.trySend(message)
    }
}
