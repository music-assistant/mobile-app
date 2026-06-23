package io.music_assistant.client.ui.compose.common.viewmodel

import io.music_assistant.client.data.model.client.items.Playlist
import io.music_assistant.client.data.repository.MediaItemChange
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Creates a playlist and resolves to the server-confirmed [Playlist].
 *
 * The create RPC carries no usable payload, so the new playlist is only
 * observable asynchronously via [itemChanges]. We therefore start awaiting
 * the confirming [MediaItemChange.Added] event **before** [sendCreate] runs,
 * so a fast server echo can't slip past us, and bound the wait by [timeoutMs].
 *
 * - request failure → [onError] is invoked, the awaiter is cancelled, returns null.
 * - confirmation not seen within [timeoutMs] → returns null (no error: the create
 *   may well have succeeded; the UI simply forgoes the highlight).
 *
 * Dependency-inverted (flow + lambdas rather than the concrete VM collaborators)
 * so the ordering/timeout/failure semantics are unit-testable in isolation.
 */
internal suspend fun createPlaylistAwaitingConfirmation(
    name: String,
    itemChanges: Flow<MediaItemChange>,
    timeoutMs: Long,
    sendCreate: suspend () -> Result<*>,
    onError: suspend () -> Unit,
): Playlist? = coroutineScope {
    val confirmed = async {
        withTimeoutOrNull(timeoutMs) {
            itemChanges
                .filterIsInstance<MediaItemChange.Added>()
                .map { it.item }
                .filterIsInstance<Playlist>()
                .first { it.displayName == name.trim() }
        }
    }
    if (sendCreate().isSuccess) {
        confirmed.await()
    } else {
        confirmed.cancel()
        onError()
        null
    }
}
