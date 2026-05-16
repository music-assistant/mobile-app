package io.music_assistant.client.data.model.client

import io.music_assistant.client.data.model.server.MediaType
import io.music_assistant.client.data.model.server.ProviderMapping

class Playlist(
    itemId: String,
    provider: String,
    name: String,
    providerMappings: List<ProviderMapping>?,
    metadata: Metadata?,
    favorite: Boolean?,
    sortName: String? = null,
    uri: String?,
    imageInfo: ImageInfo?,
    val isEditable: Boolean,
    val isDynamic: Boolean,
) : AppMediaItem(
    itemId = itemId,
    provider = provider,
    name = name,
    providerMappings = providerMappings,
    metadata = metadata,
    favorite = favorite,
    mediaType = MediaType.PLAYLIST,
    sortName = sortName,
    uri = uri,
    imageInfo = imageInfo,
    canStartRadio = !isDynamic,
) {
    override val subtitle = "Playlist"
}
