package io.music_assistant.client.data.model.client.items

import io.music_assistant.client.data.model.client.ImageInfo
import io.music_assistant.client.data.model.client.ImageType
import io.music_assistant.client.data.model.client.MediaType
import io.music_assistant.client.data.model.client.Metadata
import io.music_assistant.client.data.model.server.ProviderMapping

/**
 * A server-built group of library items, today an audiobook series. It exists only in a
 * `collapse_collections` listing or a `get_collection` answer: no provider mappings, no
 * favorite, no own artwork. [items] keep the server order (the series sequence). Playing
 * the [uri] lets the server pick the first not-fully-played item.
 */
data class MediaCollection(
    override val itemId: String,
    override val provider: String,
    override val name: String,
    override val uri: String?,
    override val images: Map<ImageType, ImageInfo>,
    val items: List<AppMediaItem>,
) : AppMediaItem() {
    // No favorite state, so the copy is the item itself.
    override fun withFavorite(favorite: Boolean?) = this
    override val providerMappings: List<ProviderMapping>? = null
    override val metadata: Metadata? = null
    override val favorite: Boolean? = null
    override val sortName: String? = null
    override val mediaType: MediaType = MediaType.COLLECTION

    override fun equals(other: Any?): Boolean =
        other is MediaCollection && super.equals(other) && items == other.items

    override fun hashCode(): Int = super.hashCode() + items.hashCode()
}
