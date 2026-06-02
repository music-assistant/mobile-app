package io.music_assistant.client.data.model.client.items

import io.music_assistant.client.data.model.client.Chapter
import io.music_assistant.client.data.model.client.ImageInfo
import io.music_assistant.client.data.model.client.ImageType
import io.music_assistant.client.data.model.client.MediaType
import io.music_assistant.client.data.model.client.Metadata
import io.music_assistant.client.data.model.server.ProviderMapping
import io.music_assistant.client.ui.compose.common.icons.BookAudioIcon

data class Audiobook(
    override val itemId: String,
    override val provider: String,
    override val name: String,
    override val providerMappings: List<ProviderMapping>?,
    override val metadata: Metadata?,
    override val favorite: Boolean?,
    override val sortName: String? = null,
    override val uri: String?,
    override val images: Map<ImageType, ImageInfo>,
    override val duration: Double?,
    override val isPlayable: Boolean,
    val authors: List<String>?,
    val narrators: List<String>?,
    val chapters: List<Chapter>?,
    val fullyPlayed: Boolean?,
    val resumePositionMs: Long?,
    override val version: String?,
) : AppMediaItem(), PlayableItem {
    override val mediaType: MediaType = MediaType.AUDIOBOOK
    override val subtitle =
        authors?.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: "Audiobook"
    override val parentName: String? = authors?.firstOrNull()
    override val defaultIcon = BookAudioIcon
    override fun withFavorite(favorite: Boolean?) = copy(favorite = favorite)
}
