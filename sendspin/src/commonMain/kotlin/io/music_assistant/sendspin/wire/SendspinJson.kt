package io.music_assistant.sendspin.wire

import kotlinx.serialization.json.Json

/**
 * The single JSON instance for the Sendspin wire format.
 *
 * Tolerant on input: unknown keys and unknown enum variants are accepted so a
 * newer server never breaks parsing. Defaults are encoded so the server sees
 * every field the spec expects.
 */
val SendspinJson: Json = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
    coerceInputValues = true
}
