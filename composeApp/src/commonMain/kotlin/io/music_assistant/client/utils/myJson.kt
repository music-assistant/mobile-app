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
