package io.music_assistant.client.data.repository

import co.touchlab.kermit.Logger
import io.music_assistant.client.api.Request
import io.music_assistant.client.data.model.client.items.AppMediaItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The single sender of `music/refresh_item`.
 *
 * Requests run in the app [scope], not the page's: for a library item the server answers only after
 * it has matched the other providers and reloaded the metadata, which can take minutes, and leaving
 * the page must not abandon the request or let a reopened page send a second one. The server
 * reports no progress, so the only state is whether a refresh of an item is on the server.
 */
class ItemRefresher(
    private val mediaItemRepository: MediaItemRepository,
    private val scope: CoroutineScope,
) {
    private val log = Logger.withTag("ItemRefresher")

    /** A refreshed [item] in answer to the refresh of [uri]; a substitute has another uri. */
    data class Refreshed(val uri: String, val item: AppMediaItem)

    private val _refreshingUris = MutableStateFlow<Set<String>>(emptySet())

    /** Uris with a refresh on the server. */
    val refreshingUris: StateFlow<Set<String>> = _refreshingUris.asStateFlow()

    private val _refreshed = MutableSharedFlow<Refreshed>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** Every successful refresh, after its [MediaItemChange.Updated]. */
    val refreshed: SharedFlow<Refreshed> = _refreshed

    /** Ignored while a refresh of [uri] is on the server. A failure reaches the user as the server error toast. */
    fun refresh(uri: String) {
        var started = false
        _refreshingUris.update { current ->
            started = uri !in current
            current + uri
        }
        if (!started) return
        scope.launch {
            try {
                mediaItemRepository.fetchMediaItem(Request.Library.refreshItem(uri))
                    .onSuccess { item ->
                        item ?: return@onSuccess
                        // A refresh keeps the favorite, and the answer of server 2.11 has none.
                        mediaItemRepository.publishLocalChange(MediaItemChange.Updated(item, carriesFavorite = false))
                        _refreshed.tryEmit(Refreshed(uri, item))
                    }
                    .onFailure { log.w(it) { "Failed to refresh $uri" } }
            } finally {
                _refreshingUris.update { it - uri }
            }
        }
    }
}
