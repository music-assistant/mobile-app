package io.music_assistant.client.data.model.server

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ServerUser(
    @SerialName("preferences") val preferences: ServerUserPreferences? = null,
)

@Serializable
data class ServerUserPreferences(
    @SerialName("sidebar.shortcuts") val shortcuts: List<String>? = null,
    // Chapter-based progress/navigation for audiobooks & podcasts; the web
    // frontend owns the settings toggle. Absent means the default (true).
    @SerialName("audiobook_chapter_progress") val audiobookChapterProgress: Boolean? = null,
) {
    /** Resolved chapter gate; an absent field means the server default. */
    val chapterProgressEnabled: Boolean get() = audiobookChapterProgress != false
}
