package io.music_assistant.client.settings

import io.music_assistant.client.data.model.server.DspConfigPreset
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * What to do to the local player's DSP when the phone (dis)connects from the car. One value per
 * direction (connect / disconnect). Serialized polymorphically by `myJson` (default `"type"`
 * discriminator) straight into settings — no separate storage DTO needed.
 */
@Serializable
sealed interface CarDspAction {
    /** Leave DSP untouched. Default for both directions. */
    @Serializable
    @SerialName("nothing")
    data object Nothing : CarDspAction

    /** Keep the current DSP config but turn it off (`enabled = false`). */
    @Serializable
    @SerialName("disable")
    data object Disable : CarDspAction

    /**
     * Load [name]'s preset (overwriting the current DSP config, `enabled = true`).
     * [presetId] is the server key when present; [name] is the fallback identity/label
     * for presets the server exposes without an id.
     */
    @Serializable
    @SerialName("preset")
    data class Preset(val presetId: String?, val name: String) : CarDspAction
}

/** True when this server preset is the one [action] refers to (id first, name as fallback). */
fun DspConfigPreset.matches(action: CarDspAction.Preset): Boolean =
    if (action.presetId != null) presetId == action.presetId else name == action.name
