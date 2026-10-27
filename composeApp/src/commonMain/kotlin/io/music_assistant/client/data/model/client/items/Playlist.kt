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
    /** The display name of the owner, as the provider reports it; null or blank when unknown. */
    val owner: String? = null,
    /** Server 2.11: who the playlist serves. Null on an older server, and for "everyone" on 2.11. */
    val access: PlaylistAccess? = null,
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
        return isMusicAssistantPlaylist || supportedMediaTypes?.contains(type) != false
    }

    /** A playlist that Music Assistant keeps itself; only those carry an [access] record. */
    val isMusicAssistantPlaylist: Boolean
        get() = providerMappings.orEmpty().any { it.providerDomain == BUILTIN_PROVIDER }

    private companion object {
        const val BUILTIN_PROVIDER = "builtin"
    }
}
