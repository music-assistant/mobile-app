package io.music_assistant.client.ui.compose.library

import io.music_assistant.client.data.model.server.MediaType
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Bridges "open Library tab N" requests from MainNavRoot to LibraryViewModel,
 * decoupled from the NavKey route. Needed because LibraryViewModel is shared
 * across all Library visits (no per-NavEntry ViewModelStore), so route-arg
 * application via `applyInitialTabIfNeeded` only fires on first VM creation.
 */
class LibraryNavCoordinator {
    private val channel = Channel<MediaType>(Channel.CONFLATED)
    val tabRequests: Flow<MediaType> = channel.receiveAsFlow()

    fun requestTab(type: MediaType) {
        channel.trySend(type)
    }
}
