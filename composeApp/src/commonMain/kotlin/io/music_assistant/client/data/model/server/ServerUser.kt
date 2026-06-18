package io.music_assistant.client.data.model.server

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ServerUser(
    @SerialName("preferences") val preferences: ServerUserPreferences? = null,
)

@Serializable
data class ServerUserPreferences(
    @SerialName("sidebar.shortcuts") val shortcuts: List<String>,
)
