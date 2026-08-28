package io.music_assistant.client.data.model.client.items

import io.music_assistant.client.data.model.client.ImageInfo
import io.music_assistant.client.data.model.client.ImageType
import io.music_assistant.client.data.model.client.MediaType
import io.music_assistant.client.data.model.client.Metadata
import io.music_assistant.client.data.model.server.ProviderMapping
import io.music_assistant.client.data.model.server.ServerMediaItem
import io.music_assistant.client.ui.compose.common.icons.TrackIcon

data class Track(
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
    val artists: List<Artist>,
    val album: Album?,
    val discNumber: Int?,
    val trackNumber: Int?,
    val position: Int?,
    override val version: String?,
    // Retained DTO echoed back for metadata/get_track_lyrics — the server resolves
    // lyrics from the full track it can't reconstruct from our decomposed model.
    // Synthetic default for previews/tests; the factory always supplies the real DTO.
    val source: ServerMediaItem = ServerMediaItem(
        itemId = itemId,
        provider = provider,
        name = name,
        mediaType = MediaType.TRACK.serverValue,
    ),
) : AppMediaItem(), PlayableItem {
    override val mediaType: MediaType = MediaType.TRACK
    override val canStartEndlessMix: Boolean = true
    override val displayName =
        "${name}${version?.trim()?.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()}"
    override val subtitle = artists.joinToString(separator = ", ") { it.displayName }
    override val parentName: String? = album?.displayName
    override val defaultIcon = TrackIcon
    override fun withFavorite(favorite: Boolean?) = copy(favorite = favorite)
}
