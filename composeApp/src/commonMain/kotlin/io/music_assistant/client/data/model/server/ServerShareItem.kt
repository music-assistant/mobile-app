package io.music_assistant.client.data.model.server

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ServerShareItem(val title: String, val links: List<ServerShareLink> = emptyList())

@Serializable
data class ServerShareLink(
    @SerialName("provider_domain") val providerDomain: String,
    @SerialName("provider_instance") val providerInstance: String,
    val url: String,
)
