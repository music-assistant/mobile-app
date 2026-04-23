package io.music_assistant.client.data.model.server

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray

@Serializable
data class DspConfig(
    @SerialName("enabled") val enabled: Boolean = false,
    @SerialName("input_gain") val inputGain: Double = 0.0,
    @SerialName("output_gain") val outputGain: Double = 0.0,
    @SerialName("filters") val filters: JsonArray = JsonArray(emptyList()),
)

@Serializable
data class DspConfigPreset(
    @SerialName("name") val name: String,
    @SerialName("config") val config: DspConfig,
    @SerialName("preset_id") val presetId: String? = null,
)
