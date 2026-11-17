package io.music_assistant.client.data.model.server

/** How close a substitute must be when the server matches the tracks of an imported playlist. */
enum class PlaylistMatchPolicy(val serverValue: String) {
    EXACT("exact"),
    SAME_RECORDING("same_recording"),
    BEST_EFFORT("best_effort"),
}

/** `music/playlists/import_playlist` takes `match_policy` from server 2.11. */
fun supportsPlaylistMatchPolicy(schemaVersion: Int?): Boolean =
    schemaVersion != null && schemaVersion >= SERVER_2_11_SCHEMA
