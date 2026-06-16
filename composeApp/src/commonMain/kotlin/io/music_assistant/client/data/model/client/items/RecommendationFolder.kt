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
    // BrowseFolder path used to descend one level; null for recommendation folders.
    val path: String? = null,
) : AppMediaItem() {
    override val providerMappings: List<ProviderMapping>? = null
    override val metadata: Metadata? = null
    override val favorite: Boolean? = null
    override val mediaType: MediaType = MediaType.FOLDER

    // Browse folders can arrive with a blank name; fall back to the path's last (capitalized) segment.
    override val displayName: String
        get() = name.ifBlank {
            path?.trimEnd('/')?.substringAfterLast('/')
                ?.replaceFirstChar { it.uppercase() }
                .orEmpty()
        }

    // The server prepends a ".." entry that points at the parent level. We have our own back
    // navigation, so a tap on it should pop the stack rather than descend into a new level.
    val isParentLink: Boolean get() = name == PARENT_LINK_NAME

    private companion object {
        const val PARENT_LINK_NAME = ".."
    }

    val rowItemType = when (itemId) {
        "recently_added_tracks", "recent_favorite_tracks" -> MediaType.TRACK
        "recently_added_albums", "random_albums" -> MediaType.ALBUM
        "random_artists" -> MediaType.ARTIST
        "favorite_playlists" -> MediaType.PLAYLIST
        else -> items?.map { item -> item.mediaType }?.toSet()?.takeIf { it.size == 1 }?.first()
    }

    override fun equals(other: Any?): Boolean {
        return other is RecommendationFolder &&
                super.equals(other) &&
                items == other.items &&
                path == other.path
    }

    override fun hashCode(): Int {
        return super.hashCode() + items.hashCode() + path.hashCode()
    }
}
