package io.music_assistant.client.wear.data

/**
 * The Music Assistant commands the watch sends. The names match the phone app's
 * `io.music_assistant.client.api.APICommands`; keep them in step when the server renames one.
 */
object APICommands {
    const val AUTH = "auth"

    const val PLAYERS_ALL = "players/all"
    const val PLAYERS_CMD_VOLUME_SET = "players/cmd/volume_set"
    const val PLAYERS_CMD_GROUP_VOLUME = "players/cmd/group_volume"

    const val PLAYER_QUEUES_ALL = "player_queues/all"
    const val PLAYER_QUEUES_ITEMS = "player_queues/items"
    const val PLAYER_QUEUES_PLAY_INDEX = "player_queues/play_index"
    const val PLAYER_QUEUES_DELETE_ITEM = "player_queues/delete_item"

    /** `players/cmd/<command>`: play_pause, next, previous. */
    fun playersCmd(command: String): String = "players/cmd/$command"

    const val CMD_PLAY_PAUSE = "play_pause"
    const val CMD_PLAY = "play"
    const val CMD_PAUSE = "pause"
    const val CMD_NEXT = "next"
    const val CMD_PREVIOUS = "previous"
}

/** Server event names the watch reacts to. Every other event is ignored. */
object EventTypes {
    const val PLAYER_ADDED = "player_added"
    const val PLAYER_UPDATED = "player_updated"
    const val PLAYER_REMOVED = "player_removed"
    const val QUEUE_ADDED = "queue_added"
    const val QUEUE_UPDATED = "queue_updated"
    const val QUEUE_ITEMS_UPDATED = "queue_items_updated"
    const val QUEUE_TIME_UPDATED = "queue_time_updated"
}

/** Player features the watch gates a control on. */
object PlayerFeatures {
    const val VOLUME_SET = "volume_set"
    const val NEXT_PREVIOUS = "next_previous"
    const val PAUSE = "pause"
}
