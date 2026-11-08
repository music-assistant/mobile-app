package io.music_assistant.client.data.model.server

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/**
 * The `config/players/get` answer, cut to the entries. The server builds [values] from the entries
 * that the player offers in its current state, so a missing key means that the player has no such
 * setting right now.
 *
 * A player with output protocols (a device that the server reaches over more than one protocol)
 * carries the entries of its native output under their bare key, and the entries of each other
 * protocol player under `<protocol player id>||protocol||<key>`. A save on the parent with the
 * prefixed key goes to the protocol player.
 */
@Serializable
data class ServerPlayerConfig(
    val values: Map<String, ServerConfigEntry> = emptyMap(),
) {
    /**
     * The entry [key] of the output that the player prefers, with its full key: the bare key for
     * the native output, the prefixed key for a protocol player. Without a match for the preferred
     * output, the bare key wins, then the first protocol entry.
     */
    fun outputEntry(key: String): Pair<String, ServerConfigEntry>? {
        val preferred = values[PREFERRED_OUTPUT_PROTOCOL]?.stringValue
        val candidates = values.filterKeys { it == key || it.endsWith(PROTOCOL_SPLITTER + key) }
        val chosen = candidates.keys.firstOrNull { it.substringBefore(PROTOCOL_SPLITTER, NATIVE) == preferred }
            ?: candidates.keys.firstOrNull { it == key }
            ?: candidates.keys.firstOrNull()
        return chosen?.let { it to candidates.getValue(it) }
    }

    companion object {
        const val PROTOCOL_SPLITTER = "||protocol||"
        private const val PREFERRED_OUTPUT_PROTOCOL = "preferred_output_protocol"
        private const val NATIVE = "native"
    }
}

/** One entry of a player config. A null [value] means that [defaultValue] applies. */
@Serializable
data class ServerConfigEntry(
    val value: JsonElement? = null,
    @SerialName("default_value") val defaultValue: JsonElement? = null,
    val range: List<Int>? = null,
) {
    private val effective: JsonPrimitive? get() = (value ?: defaultValue) as? JsonPrimitive

    /** The effective integer value, or null for an entry of another type. */
    val intValue: Int? get() = effective?.intOrNull

    /** The effective string value, or null for an entry of another type. */
    val stringValue: String? get() = effective?.takeIf { it.isString }?.content

    /** The `(min, max)` range as a Kotlin range, or null if the entry has none. */
    val intRange: IntRange? get() = range?.takeIf { it.size == 2 }?.let { it[0]..it[1] }
}
