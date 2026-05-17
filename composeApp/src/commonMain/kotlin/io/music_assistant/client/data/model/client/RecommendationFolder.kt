package io.music_assistant.client.data.model.client

import io.music_assistant.client.data.model.client.MediaType
import io.music_assistant.client.data.model.server.ProviderMapping

class RecommendationFolder(
    itemId: String,
    provider: String,
    name: String,
    providerMappings: List<ProviderMapping>?,
    sortName: String? = null,
    uri: String?,
    imageInfo: ImageInfo?,
    val items: List<AppMediaItem>? = null,
) : AppMediaItem(
    itemId = itemId,
    provider = provider,
    name = name,
    providerMappings = null,
    metadata = null,
    favorite = null,
    mediaType = MediaType.ARTIST,
    sortName = sortName,
    uri = uri,
    imageInfo = imageInfo,
) {
    val rowItemType = when (itemId) {
        "recently_added_tracks", "recent_favorite_tracks" -> MediaType.TRACK
        "recently_added_albums", "random_albums" -> MediaType.ALBUM
        "random_artists" -> MediaType.ARTIST
        "favorite_playlists" -> MediaType.PLAYLIST
        else -> null
    }

    override fun equals(other: Any?): Boolean {
        return other is RecommendationFolder &&
                super.equals(other) &&
                items == other.items
    }

    override fun hashCode(): Int {
        return super.hashCode() + items.hashCode()
    }
}
