package io.music_assistant.sendspin.wire

import kotlinx.serialization.json.Json

/**
 * The single JSON instance for the Sendspin wire format.
 *
 * Tolerant on input: unknown keys and unknown enum variants are accepted so a
 * newer server never breaks parsing. Defaults are encoded so the server sees
 * every field the spec expects. A null field is omitted, never written as
 * `null`: spec 1.0.0-rc1 rejects `null` in optional fields, and a 9.x server
 * reads an absent field as its default anyway. This also lets one model carry
 * the fields of both wires, with the other wire's fields left null.
 */
internal val SendspinJson: Json = Json {
    encodeDefaults = true
    explicitNulls = false
    ignoreUnknownKeys = true
    coerceInputValues = true
}
