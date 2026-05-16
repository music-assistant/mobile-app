package io.music_assistant.client.data.model.client

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Podcasts
import io.music_assistant.client.data.model.server.MediaType
import io.music_assistant.client.data.model.server.ProviderMapping
import io.music_assistant.client.utils.formatIsoDate

class PodcastEpisode(
    itemId: String,
    provider: String,
    name: String,
    providerMappings: List<ProviderMapping>?,
    metadata: Metadata?,
    favorite: Boolean?,
    sortName: String? = null,
    uri: String?,
    imageInfo: ImageInfo?,
    override val duration: Double?,
    val podcast: Podcast?,
    val fullyPlayed: Boolean?,
    val resumePositionMs: Long?,
    val releaseDate: String? = null,
    override val version: String?,
) : AppMediaItem(
    itemId = itemId,
    provider = provider,
    name = name,
    providerMappings = providerMappings,
    metadata = metadata,
    favorite = favorite,
    mediaType = MediaType.PODCAST_EPISODE,
    sortName = sortName,
    uri = uri,
    imageInfo = imageInfo,
),
    PlayableItem {
    override val subtitle = releaseDate?.let(::formatIsoDate)
    override val parentName: String? = podcast?.displayName
    override val defaultIcon = Icons.Default.Podcasts
}
