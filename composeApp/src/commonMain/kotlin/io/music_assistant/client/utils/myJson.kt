package io.music_assistant.client.utils

import kotlinx.serialization.json.Json

val myJson = Json {
    prettyPrint = true
    encodeDefaults = true
    ignoreUnknownKeys = true
    // New fields added server-side (unknown keys) are already tolerated; with this
    // we also tolerate unknown enum variants by falling back to the field's default
    // (for non-null fields) or null (for nullable fields) instead of throwing.
    coerceInputValues = true
}

/**
 * For echoing a parsed [ServerMediaItem] back to the server (e.g. as the `media_item`
 * arg of `music/mark_played`). [explicitNulls] = false omits our nullable-but-absent
 * fields so the server's dataclass defaults apply instead of receiving `null` for a
 * non-optional field (which would fail deserialization).
 */
val mediaItemEchoJson = Json {
    encodeDefaults = true
    explicitNulls = false
}
