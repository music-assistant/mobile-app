package io.music_assistant.client.data.model.server

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProviderManifest(
    @SerialName("type") val type: String,
    @SerialName("domain") val domain: String,
    @SerialName("name") val name: String,
    @SerialName("icon") val icon: String?,
    @SerialName("icon_svg") val iconSvg: String?,
    @SerialName("icon_svg_dark") val iconSvgDark: String?,
)
