package io.music_assistant.client.data.model.server

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ServerProviderInstance(
    @SerialName("instance_id") val instanceId: String,
    @SerialName("name") val name: String,
    @SerialName("domain") val domain: String,
    @SerialName("type") val type: String? = null,
    @SerialName("available") val available: Boolean = true,
    // Library capabilities, e.g. "library_tracks" — used to offer a provider only
    // on lists whose media type it actually serves.
    @SerialName("supported_features") val supportedFeatures: List<String> = emptyList(),
)
