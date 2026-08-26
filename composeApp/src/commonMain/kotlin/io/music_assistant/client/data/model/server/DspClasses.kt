package io.music_assistant.client.data.model.server

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray

/** First server API schema with `config/players/dsp/apply_preset` and the active `preset_id`. */
const val DSP_APPLY_PRESET_MIN_SCHEMA = 38

fun supportsDspApplyPreset(schemaVersion: Int?): Boolean =
    schemaVersion != null && schemaVersion >= DSP_APPLY_PRESET_MIN_SCHEMA

@Serializable
data class DspConfig(
    @SerialName("enabled") val enabled: Boolean = false,
    @SerialName("input_gain") val inputGain: Double = 0.0,
    @SerialName("output_gain") val outputGain: Double = 0.0,
    @SerialName("filters") val filters: JsonArray = JsonArray(emptyList()),
    @SerialName("preset_id") val presetId: String? = null,
)

@Serializable
data class DspConfigPreset(
    @SerialName("name") val name: String,
    @SerialName("config") val config: DspConfig,
    @SerialName("preset_id") val presetId: String? = null,
)
