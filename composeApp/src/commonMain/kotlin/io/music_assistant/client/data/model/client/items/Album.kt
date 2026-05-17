package io.music_assistant.client.data.model.client.items

import io.music_assistant.client.data.model.client.ImageInfo
import io.music_assistant.client.data.model.client.ImageType
import io.music_assistant.client.data.model.client.MediaType
import io.music_assistant.client.data.model.client.Metadata
import io.music_assistant.client.data.model.server.ProviderMapping

class Album(
    itemId: String,
    provider: String,
    name: String,
    providerMappings: List<ProviderMapping>?,
    metadata: Metadata?,
    favorite: Boolean?,
    sortName: String? = null,
    uri: String?,
    images: Map<ImageType, ImageInfo>,
    val version: String?,
    val year: Int?,
    val artists: List<Artist>,
) : AppMediaItem(
    itemId = itemId,
    provider = provider,
    name = name,
    providerMappings = providerMappings,
    metadata = metadata,
    favorite = favorite,
    mediaType = MediaType.ALBUM,
    sortName = sortName,
    uri = uri,
    images = images,
    canStartRadio = true,
) {
    override val displayName =
        "${name}${version?.trim()?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""}"
    override val subtitle = artists.joinToString(separator = ", ") { it.displayName }
}
