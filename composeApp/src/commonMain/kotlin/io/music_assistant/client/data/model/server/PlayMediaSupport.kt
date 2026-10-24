package io.music_assistant.client.data.model.server

/**
 * First server API schema whose `player_queues/play_media` has `start_from_beginning`. The
 * commit that added it left `API_SCHEMA_VERSION` at 39, so 40 is the first version that
 * guarantees it.
 */
const val START_FROM_BEGINNING_MIN_SCHEMA = 40

/**
 * First server API schema whose `player_queues/play_media` has `shuffle`. The commit that added
 * it left `API_SCHEMA_VERSION` at 51, so 52 is the first version that guarantees it.
 */
const val PLAY_SHUFFLED_MIN_SCHEMA = 52

/**
 * The optional `play_media` flags of the connected server. An older server ignores an unknown
 * argument, so a request with the flag would play as if the flag were absent. The menus offer an
 * action only while its flag is supported. `replace_next` needs no gate: every server 2.x has it.
 */
data class PlayMediaSupport(
    val shuffle: Boolean,
    val startFromBeginning: Boolean,
) {
    companion object {
        val NONE = PlayMediaSupport(shuffle = false, startFromBeginning = false)
        val ALL = PlayMediaSupport(shuffle = true, startFromBeginning = true)
    }
}

fun playMediaSupport(schemaVersion: Int?): PlayMediaSupport = schemaVersion?.let {
    PlayMediaSupport(
        shuffle = it >= PLAY_SHUFFLED_MIN_SCHEMA,
        startFromBeginning = it >= START_FROM_BEGINNING_MIN_SCHEMA,
    )
} ?: PlayMediaSupport.NONE
