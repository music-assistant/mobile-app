package io.music_assistant.client.services

import io.music_assistant.client.data.MainDataSource
import io.music_assistant.client.utils.mapState
import kotlinx.coroutines.flow.StateFlow

/** A displayed player plays: the media service holds the Wi-Fi lock and survives a task removal. */
val MainDataSource.isAnythingPlaying: StateFlow<Boolean>
    get() = displayedPlayers.mapState { players -> players.any { it.player.isPlaying } }

/** A displayed player has a current queue item: the media service keeps running. */
val MainDataSource.doesAnythingHavePlayableItem: StateFlow<Boolean>
    get() = displayedPlayers.mapState { players -> players.any { it.queueInfo?.currentItem != null } }

/**
 * A player plays an item of its queue: the media service starts on it. A stream reports
 * "playing" before its queue arrives, and a player on an external source plays with no
 * queue item, so [isAnythingPlaying] alone would start a service with nothing to present.
 */
val MainDataSource.isAnyQueueItemPlaying: StateFlow<Boolean>
    get() = displayedPlayers.mapState { players ->
        players.any { it.player.isPlaying && it.queueInfo?.currentItem != null }
    }
