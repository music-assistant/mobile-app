package io.music_assistant.client.data.model.client.items

import io.music_assistant.client.data.model.client.ImageInfo
import io.music_assistant.client.data.model.client.ImageType
import io.music_assistant.client.data.model.client.MediaType
import io.music_assistant.client.data.model.client.Metadata
import io.music_assistant.client.data.model.server.ProviderMapping

data class Playlist(
    override val itemId: String,
    override val provider: String,
    override val name: String,
    override val providerMappings: List<ProviderMapping>?,
    override val metadata: Metadata?,
    override val favorite: Boolean?,
    override val sortName: String? = null,
    override val uri: String?,
    override val images: Map<ImageType, ImageInfo>,
    val isEditable: Boolean,
    val isDynamic: Boolean,
    /** Null when the server does not report it; then no type is excluded. */
    val supportedMediaTypes: Set<MediaType>? = null,
) : AppMediaItem() {
    override fun withFavorite(favorite: Boolean?) = copy(favorite = favorite)
    override val mediaType: MediaType = MediaType.PLAYLIST
    override val canStartEndlessMix: Boolean get() = !isDynamic

    /**
     * Mirrors the server's add rule: a builtin playlist stores any item by uri and ignores
     * [supportedMediaTypes]; other providers silently drop items of an unsupported type.
     */
    fun accepts(item: AppMediaItem): Boolean {
        // The server unwraps an album into its tracks.
        val type = when (item) {
            is Album -> MediaType.TRACK
            else -> item.mediaType
        }
        return providerMappings.orEmpty().any { it.providerDomain == BUILTIN_PROVIDER } ||
            supportedMediaTypes?.contains(type) != false
    }

    private companion object {
        const val BUILTIN_PROVIDER = "builtin"
    }
}
