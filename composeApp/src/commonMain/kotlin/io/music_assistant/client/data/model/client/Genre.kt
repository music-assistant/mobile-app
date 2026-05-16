package io.music_assistant.client.data.model.client

import io.music_assistant.client.data.model.server.MediaType
import io.music_assistant.client.data.model.server.ProviderMapping

class Genre(
    itemId: String,
    provider: String,
    name: String,
    providerMappings: List<ProviderMapping>?,
    metadata: Metadata?,
    favorite: Boolean?,
    sortName: String? = null,
    uri: String?,
    imageInfo: ImageInfo?,
) : AppMediaItem(
    itemId = itemId,
    provider = provider,
    name = name,
    providerMappings = providerMappings,
    metadata = metadata,
    favorite = favorite,
    mediaType = MediaType.GENRE,
    sortName = sortName,
    uri = uri,
    imageInfo = imageInfo,
) {
    override val subtitle = "Genre"
    override val mediaUri: String
        get() = uri ?: "$provider://genre/$itemId"
}
