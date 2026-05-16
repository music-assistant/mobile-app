package io.music_assistant.client.data.model.client

import io.music_assistant.client.data.model.server.MediaType
import io.music_assistant.client.data.model.server.ProviderMapping
import io.music_assistant.client.ui.compose.common.icons.TrackIcon

class Track(
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
    val artists: List<Artist>,
    val album: Album?,
    val discNumber: Int?,
    val trackNumber: Int?,
    val position: Int?,
    override val version: String?,
) : AppMediaItem(
    itemId = itemId,
    provider = provider,
    name = name,
    providerMappings = providerMappings,
    metadata = metadata,
    favorite = favorite,
    mediaType = MediaType.TRACK,
    sortName = sortName,
    uri = uri,
    imageInfo = imageInfo,
    canStartRadio = true,
),
    PlayableItem {
    override val displayName =
        "${name}${version?.trim()?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""}"
    override val subtitle = artists.joinToString(separator = ", ") { it.displayName }
    override val parentName: String? = album?.displayName
    override val defaultIcon = TrackIcon
}
