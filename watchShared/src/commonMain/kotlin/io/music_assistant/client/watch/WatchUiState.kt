package io.music_assistant.client.watch

import io.music_assistant.client.watch.data.WatchNowPlaying
import io.music_assistant.client.watch.data.WatchPlayer
import io.music_assistant.client.watch.data.WatchQueueState

/**
 * The link to the server, flattened to the six cases the watch can say something about.
 *
 * A Kotlin sealed hierarchy crosses into Swift as a set of classes that only an `is` cascade
 * can read; an enum crosses as a Swift enum a `switch` is exhaustive over. The one case that
 * carries detail puts it in [WatchUiState.statusMessage].
 */
enum class WatchSessionStatus {
    IDLE,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    UNAUTHORIZED,
    NOT_CONFIGURED,
}

/** Everything the watch screens render, in one snapshot. */
data class WatchUiState(
    val status: WatchSessionStatus = WatchSessionStatus.IDLE,
    /** The reason behind [status], for the states a user can act on. Null otherwise. */
    val statusMessage: String? = null,
    val players: List<WatchPlayer> = emptyList(),
    val selected: WatchPlayer? = null,
    val nowPlaying: WatchNowPlaying? = null,
    val queue: WatchQueueState = WatchQueueState(),
    /** The queue row playing now, which the queue screen marks. */
    val activeQueueItemId: String? = null,
) {
    /** Players the user can pick: reachable, not hidden by the server. */
    val selectablePlayers: List<WatchPlayer> = players.filter { it.isSelectable }

    val isConnected: Boolean = status == WatchSessionStatus.CONNECTED
}
