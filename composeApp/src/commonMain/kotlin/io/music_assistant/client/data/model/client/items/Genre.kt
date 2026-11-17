package io.music_assistant.client.data.model.client.items

import io.music_assistant.client.data.model.client.ImageInfo
import io.music_assistant.client.data.model.client.ImageType
import io.music_assistant.client.data.model.client.MediaType
import io.music_assistant.client.data.model.client.Metadata
import io.music_assistant.client.data.model.server.ProviderMapping
import io.music_assistant.client.ui.compose.common.icons.GenreIcon

data class Genre(
    override val itemId: String,
    override val provider: String,
    override val name: String,
    override val providerMappings: List<ProviderMapping>?,
    override val metadata: Metadata?,
    override val favorite: Boolean?,
    override val sortName: String? = null,
    override val uri: String?,
    override val images: Map<ImageType, ImageInfo>,
) : AppMediaItem() {
    override fun withFavorite(favorite: Boolean?) = copy(favorite = favorite)
    override val mediaType: MediaType = MediaType.GENRE
    override val defaultIcon = GenreIcon
    override val mediaUri: String
        get() = uri ?: "$provider://genre/$itemId"
}
