package io.music_assistant.client.data.repository

import io.music_assistant.client.api.Request
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.data.model.client.M3U_MIME_TYPE
import io.music_assistant.client.data.model.client.items.Playlist
import io.music_assistant.client.data.model.client.m3uExportFileName
import io.music_assistant.client.data.model.server.PlaylistMatchPolicy
import io.music_assistant.client.data.model.server.ServerMediaItem
import io.music_assistant.client.data.model.server.supportsPlaylistMatchPolicy
import io.music_assistant.client.utils.FileSharer
import io.music_assistant.client.utils.HasConnectionData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The M3U playlist import and export (`music/playlists/import_playlist`, `export_playlist`),
 * like the web frontend. The server parses and writes the M3U text; the app moves it between
 * the server and a file.
 */
class PlaylistTransfer(
    private val apiClient: ServiceClient,
    private val fileSharer: FileSharer,
) {
    /** True if the server takes a [PlaylistMatchPolicy] for an import (server 2.11). */
    val supportsMatchPolicy: Boolean
        get() = supportsPlaylistMatchPolicy(
            (apiClient.sessionState.value as? HasConnectionData)?.serverInfo?.schemaVersion,
        )

    /** Opens the share sheet with the M3U8 file of [playlist]. The server exports a library playlist only. */
    suspend fun export(playlist: Playlist, chooserTitle: String): Result<Unit> {
        val text = apiClient.sendRequest(Request.Playlist.export(playlist.itemId))
            .getOrElse { return Result.failure(it) }
            .resultAs<String>()
            ?: return Result.failure(IllegalStateException("No export for ${playlist.uri}"))
        return runCatching {
            withContext(Dispatchers.Default) { fileSharer.writeCacheFile(m3uExportFileName(playlist.name), text) }
        }.map { path -> fileSharer.share(path, M3U_MIME_TYPE, chooserTitle) }
    }

    /**
     * Creates a library playlist from [m3uData] and returns its name. The server matches the tracks
     * in a background task after the answer. A null [matchProviders] searches every music source.
     * A null [matchPolicy] is the server default. An older server does not get it.
     */
    suspend fun import(
        m3uData: String,
        matchProviders: List<String>?,
        matchPolicy: PlaylistMatchPolicy?,
    ): Result<String?> =
        apiClient.sendRequest(
            Request.Playlist.import(m3uData, matchProviders, matchPolicy?.takeIf { supportsMatchPolicy }),
        ).map { it.resultAs<ServerMediaItem>()?.name }
}
