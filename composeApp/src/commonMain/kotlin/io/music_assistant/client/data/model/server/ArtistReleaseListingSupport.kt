package io.music_assistant.client.data.model.server

/**
 * Whether the server answers `music/artists/artist_appears_on` and `music/artists/discography`
 * (server 2.11). Server 2.10 has neither. Both list a library artist only.
 */
fun supportsArtistAppearsOnAndDiscography(schemaVersion: Int?): Boolean =
    schemaVersion != null && schemaVersion >= SERVER_2_11_SCHEMA
