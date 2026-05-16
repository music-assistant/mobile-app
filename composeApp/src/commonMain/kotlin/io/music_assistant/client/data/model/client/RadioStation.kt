package io.music_assistant.client.data.model.client

import io.music_assistant.client.data.model.server.MediaType
import io.music_assistant.client.data.model.server.ProviderMapping
import io.music_assistant.client.ui.compose.common.icons.RadioIcon

class RadioStation(
    itemId: String,
    provider: String,
    name: String,
    providerMappings: List<ProviderMapping>?,
    metadata: Metadata?,
    favorite: Boolean?,
    sortName: String? = null,
    uri: String?,
    imageInfo: ImageInfo?,
    override val version: String?,
) : AppMediaItem(
    itemId = itemId,
    provider = provider,
    name = name,
    providerMappings = providerMappings,
    metadata = metadata,
    favorite = favorite,
    mediaType = MediaType.RADIO,
    sortName = sortName,
    uri = uri,
    imageInfo = imageInfo,
),
    PlayableItem {
    override val duration: Double? = null
    override val subtitle: String = "Radio"
    override val parentName: String? = null
    override val defaultIcon = RadioIcon
}
