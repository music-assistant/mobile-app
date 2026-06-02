package io.music_assistant.client.data.model.client.items

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Podcasts
import io.music_assistant.client.data.model.client.ImageInfo
import io.music_assistant.client.data.model.client.ImageType
import io.music_assistant.client.data.model.client.MediaType
import io.music_assistant.client.data.model.client.Metadata
import io.music_assistant.client.data.model.server.ProviderMapping
import io.music_assistant.client.utils.formatIsoDate

data class PodcastEpisode(
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
    val podcast: Podcast?,
    val fullyPlayed: Boolean?,
    val resumePositionMs: Long?,
    val releaseDate: String? = null,
    override val version: String?,
) : AppMediaItem(), PlayableItem {
    override val mediaType: MediaType = MediaType.PODCAST_EPISODE
    override val subtitle = releaseDate?.let(::formatIsoDate)
    override val parentName: String? = podcast?.displayName
    override val defaultIcon = Icons.Default.Podcasts
    override fun withFavorite(favorite: Boolean?) = copy(favorite = favorite)
}
