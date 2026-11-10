package io.music_assistant.client.data.model.client

/**
 * The host settings of the Party plugin that the app edits. [guestAccess] is the party on/off
 * switch: off ends the party and revokes the guest links. [fields] are the other settings, in
 * the order that the sheet shows them, with the labels that the server translated.
 */
data class PartySettings(
    val guestAccess: PartySetting,
    val fields: List<PartySetting>,
) {
    /** True when [field] applies with the [values] that the host sees, the edits included. */
    fun isShown(field: PartySetting, values: Map<String, String>): Boolean {
        val dependency = field.dependsOn ?: return true
        val actual = values[dependency.key] ?: (fields + guestAccess).firstOrNull { it.key == dependency.key }?.value
        return dependency.value?.let { it == actual } ?: (!actual.isNullOrEmpty() && actual != false.toString())
    }
}

/** One setting. [value] is its effective value as text: "true" or "false" for a switch. */
data class PartySetting(
    val key: String,
    val label: String,
    val description: String?,
    val input: PartySettingInput,
    val value: String,
    val dependsOn: Dependency? = null,
) {
    /** False for a text that the setting cannot store, like a number out of its range. */
    fun accepts(text: String): Boolean = (input as? PartySettingInput.Number)?.let { it.parse(text) != null } ?: true
}

/**
 * The setting [key] that a setting needs, like the player that needs venue mode. A null [value]
 * means any value that is set and not false, like the server's `depends_on` without a value.
 */
data class Dependency(val key: String, val value: String?)

sealed interface PartySettingInput {
    data object Switch : PartySettingInput

    data object Text : PartySettingInput

    data class Number(val range: IntRange?) : PartySettingInput {
        /** The number in [text], or null when it is not a whole number in [range]. */
        fun parse(text: String): Int? = text.trim().toIntOrNull()?.takeIf { range == null || it in range }
    }

    data class Choice(val options: List<Option>) : PartySettingInput

    data class Option(val value: String, val title: String)
}
