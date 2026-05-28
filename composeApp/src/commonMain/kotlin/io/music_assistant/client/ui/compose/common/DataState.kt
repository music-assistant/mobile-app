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

    val dataOrNull: T?
        get() = when (this) {
            is Data -> data
            is Stale -> data
            else -> null
        }
}

enum class StaleReason {
    RECONNECTING,          // Auto-reconnect in progress (transient)
    PERSISTENT_ERROR,      // Max attempts exhausted (manual action needed)
}

/**
 * Returns the payload for the two payload-carrying states ([DataState.Data], [DataState.Stale])
 * and `null` for [DataState.Loading], [DataState.Error], [DataState.NoData].
 *
 * Use this to avoid duplicating the `is Data → data; is Stale → data` disambiguation at call sites.
 */
val <T> DataState<T>.dataOrNull: T?
    get() = when (this) {
        is DataState.Data -> data
        is DataState.Stale -> data
        is DataState.Loading,
        is DataState.Error,
        is DataState.NoData,
            -> null
    }
