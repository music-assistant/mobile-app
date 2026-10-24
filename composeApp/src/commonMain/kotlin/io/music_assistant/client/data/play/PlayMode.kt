package io.music_assistant.client.data.play

import io.music_assistant.client.data.model.client.QueueOption

/**
 * How a user action asks to play one item. A screen turns it into a [PlayRequest] once it knows
 * the queue and the media uri. Every flag maps to one `player_queues/play_media` argument, except
 * [fromHereInParent], which only the item detail screen can resolve.
 */
data class PlayMode(
    val option: QueueOption,
    /** `radio_mode`: an endless mix seeded by the item. */
    val endlessMix: Boolean = false,
    /** `shuffle=true`: the server shuffles the container before it plays. */
    val shuffle: Boolean = false,
    /** `start_from_beginning`: the server ignores the saved resume position of the episode. */
    val startFromBeginning: Boolean = false,
    /** Play the parent container and start at this item (`start_item`). */
    val fromHereInParent: Boolean = false,
)
