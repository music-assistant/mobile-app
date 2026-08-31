package io.music_assistant.client.data.model.server

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A station profile from `ai_radio/stations/list`.
 *
 * The server normalizes every station to a flat record (`id`, `name`, `source_playlist_id`,
 * `source_playlist_provider`, `default_player_id`, `max_duration_minutes`,
 * `shuffle_source_tracks`, `host_id`). We bind only what the picker shows and sends; the
 * rest is authoring detail owned by the web frontend.
 */
@Serializable
data class ServerAiRadioStation(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String,
)

/** One run of a station. `ai_radio/start`, `stop` and `status` all report this shape. */
@Serializable
data class ServerAiRadioSession(
    @SerialName("session_id") val sessionId: String,
    @SerialName("station_id") val stationId: String,
    // "running" | "stopped" | "finished" | "failed"
    @SerialName("status") val status: String? = null,
    @SerialName("error") val error: String? = null,
) {
    val isRunning: Boolean get() = status == STATUS_RUNNING

    companion object {
        const val STATUS_RUNNING = "running"
    }
}

/** Envelope of `ai_radio/status`, which wraps its sessions rather than returning a bare list. */
@Serializable
data class ServerAiRadioStatus(
    @SerialName("sessions") val sessions: List<ServerAiRadioSession> = emptyList(),
)
