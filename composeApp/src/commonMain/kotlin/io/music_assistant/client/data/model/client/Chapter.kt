package io.music_assistant.client.data.model.client

/**
 * Client-side chapter view of [io.music_assistant.client.data.model.server.ServerMediaItemChapter].
 *
 * Wire types stay server-side; UI binds against this.
 */
data class Chapter(
    val position: Int,
    val name: String,
    val start: Double,
    val end: Double?,
) {
    val duration: Double get() = if (end != null) end - start else 0.0
}
