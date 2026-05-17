package io.music_assistant.client.data.model.client.items

import io.music_assistant.client.data.model.client.Chapter
import io.music_assistant.client.data.model.client.ImageInfo
import io.music_assistant.client.data.model.client.ImageType
import io.music_assistant.client.data.model.client.MediaType
import io.music_assistant.client.data.model.client.Metadata
import io.music_assistant.client.data.model.server.ProviderMapping
import io.music_assistant.client.ui.compose.common.icons.BookAudioIcon

class Audiobook(
    itemId: String,
    provider: String,
    name: String,
    providerMappings: List<ProviderMapping>?,
    metadata: Metadata?,
    favorite: Boolean?,
    sortName: String? = null,
    uri: String?,
    images: Map<ImageType, ImageInfo>,
    override val duration: Double?,
    val authors: List<String>?,
    val narrators: List<String>?,
    val chapters: List<Chapter>?,
    val fullyPlayed: Boolean?,
    val resumePositionMs: Long?,
    override val version: String?,
) : AppMediaItem(
    itemId = itemId,
    provider = provider,
    name = name,
    providerMappings = providerMappings,
    metadata = metadata,
    favorite = favorite,
    mediaType = MediaType.AUDIOBOOK,
    sortName = sortName,
    uri = uri,
    images = images,
),
    PlayableItem {
    override val subtitle =
        authors?.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: "Audiobook"
    override val parentName: String? = authors?.firstOrNull()
    override val defaultIcon = BookAudioIcon
}
