package io.music_assistant.client.data.model.client

import kotlinx.serialization.Serializable

/**
 * A genre taxonomy, the `content_type` filter of `music/genres/library_items`. The server stores
 * the music taxonomy as a null `content_type` and accepts `"music"` for it on the wire.
 */
@Serializable
enum class GenreContentType(val serverValue: String) {
    MUSIC("music"),
    PODCAST("podcast"),
    AUDIOBOOK("audiobook"),
}
