package io.music_assistant.client.data.model.server

/**
 * From server 2.11 a playlist carries `access` (`PlaylistAccess`), and a Music Assistant playlist
 * without a record serves everyone. An older server sends no field, so a null record there says
 * nothing about the playlist.
 */
fun supportsPlaylistAccess(schemaVersion: Int?): Boolean =
    schemaVersion != null && schemaVersion >= SERVER_2_11_SCHEMA
