package io.music_assistant.client.data.model.server

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SearchResult(
    @SerialName("artists") val artists: List<ServerMediaItem> = emptyList(),
    @SerialName("albums") val albums: List<ServerMediaItem> = emptyList(),
    @SerialName("tracks") val tracks: List<ServerMediaItem> = emptyList(),
    @SerialName("playlists") val playlists: List<ServerMediaItem> = emptyList(),
    @SerialName("podcasts") val podcasts: List<ServerMediaItem> = emptyList(),
    @SerialName("audiobooks") val audiobooks: List<ServerMediaItem> = emptyList(),
    @SerialName("radio") val radio: List<ServerMediaItem> = emptyList(),
    @SerialName("genres") val genres: List<ServerMediaItem> = emptyList(),
)
