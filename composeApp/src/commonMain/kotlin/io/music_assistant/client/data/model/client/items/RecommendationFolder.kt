package io.music_assistant.client.data.model.client.items

import io.music_assistant.client.data.model.client.ImageInfo
import io.music_assistant.client.data.model.client.ImageType
import io.music_assistant.client.data.model.client.MediaType
import io.music_assistant.client.data.model.client.Metadata
import io.music_assistant.client.data.model.server.ProviderMapping

data class RecommendationFolder(
    override val itemId: String,
    override val provider: String,
    override val name: String,
    override val sortName: String? = null,
    override val uri: String?,
    override val images: Map<ImageType, ImageInfo>,
    val items: List<AppMediaItem>? = null,
) : AppMediaItem() {
    override val providerMappings: List<ProviderMapping>? = null
    override val metadata: Metadata? = null
    override val favorite: Boolean? = null
    override val mediaType: MediaType = MediaType.FOLDER

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
