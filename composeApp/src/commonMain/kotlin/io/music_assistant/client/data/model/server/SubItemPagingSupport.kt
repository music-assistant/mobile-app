package io.music_assistant.client.data.model.server

/**
 * From server 2.11, `music/albums/album_tracks`, `music/playlists/playlist_tracks` and
 * `music/podcasts/podcast_episodes` answer one `limit`/`offset` page (default 500) in one message.
 * An older server has neither argument, ignores both, and answers the whole list (streamed in
 * `partial` batches that `RpcEngine` reassembles), so paging it would repeat the whole list on
 * every page.
 */
fun pagesSubItemLists(schemaVersion: Int?): Boolean =
    schemaVersion != null && schemaVersion >= SERVER_2_11_SCHEMA
