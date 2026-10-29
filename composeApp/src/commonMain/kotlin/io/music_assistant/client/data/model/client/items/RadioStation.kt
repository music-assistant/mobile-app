package io.music_assistant.client.data.model.client.items

import io.music_assistant.client.data.model.client.ImageInfo
import io.music_assistant.client.data.model.client.ImageType
import io.music_assistant.client.data.model.client.MediaType
import io.music_assistant.client.data.model.client.Metadata
import io.music_assistant.client.data.model.server.AI_RADIO_DOMAIN
import io.music_assistant.client.data.model.server.ProviderMapping
import io.music_assistant.client.ui.compose.common.icons.RadioIcon

data class RadioStation(
    override val itemId: String,
    override val provider: String,
    override val name: String,
    override val providerMappings: List<ProviderMapping>?,
    override val metadata: Metadata?,
    override val favorite: Boolean?,
    override val sortName: String? = null,
    override val uri: String?,
    override val images: Map<ImageType, ImageInfo>,
    override val version: String?,
    override val isPlayable: Boolean,
    val isDynamic: Boolean,
    val isEndless: Boolean? = null,
    override val position: Int? = null,
) : AppMediaItem(), PlayableItem {
    override val mediaType: MediaType = MediaType.RADIO
    override val duration: Double? = null
    override val parentName: String? = null
    override val defaultIcon = RadioIcon
    override fun withFavorite(favorite: Boolean?) = copy(favorite = favorite)
}

/**
 * An AI Radio show that the server mirrors into the library as a Radio item. The AI Radio
 * section lists the shows, so the Radio lists hide them. A library row has provider `library`,
 * so only its provider mapping names the plugin.
 */
val AppMediaItem.isAiRadioShow: Boolean
    get() = this is RadioStation && providerMappings.orEmpty().any { it.providerDomain == AI_RADIO_DOMAIN }
