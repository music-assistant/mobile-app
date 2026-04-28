package io.music_assistant.client.ui.compose.common

import io.music_assistant.client.utils.AppError

sealed class DataState<T> {
    class Loading<T> : DataState<T>()
    data class Error<T>(val error: AppError? = null) : DataState<T>()
    class NoData<T> : DataState<T>()
    data class Data<T>(val data: T) : DataState<T>()

    // NEW: Stale data - preserving last known good state during connection issues
    data class Stale<T>(
        val data: T,
        val disconnectedAt: Long,  // Timestamp when first entered stale state
        val reason: StaleReason,
    ) : DataState<T>()
}

enum class StaleReason {
    RECONNECTING,          // Auto-reconnect in progress (transient)
    PERSISTENT_ERROR,      // Max attempts exhausted (manual action needed)
}
