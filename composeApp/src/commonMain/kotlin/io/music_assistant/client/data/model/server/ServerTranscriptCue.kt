package io.music_assistant.client.data.model.server

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** One timed line of a podcast transcript (`music_assistant_models.MediaItemTranscriptCue`). Times are seconds. */
@Serializable
data class ServerTranscriptCue(
    @SerialName("start") val start: Double,
    @SerialName("end") val end: Double? = null,
    @SerialName("text") val text: String,
    @SerialName("speaker") val speaker: String? = null,
)
