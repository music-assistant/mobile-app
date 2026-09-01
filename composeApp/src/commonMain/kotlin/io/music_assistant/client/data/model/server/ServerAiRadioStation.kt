package io.music_assistant.client.data.model.server

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A station profile from `ai_radio/stations/list`.
 *
 * The server normalizes every station to a flat record (`id`, `name`, `source_playlist_id`,
 * `source_playlist_provider`, `default_player_id`, `max_duration_minutes`,
 * `shuffle_source_tracks`, `host_id`). We bind what the list shows and sends; the rest is
 * authoring detail owned by the web frontend.
 *
 * A station carries no artwork of its own, so the two source-playlist fields are the only way
 * to put an image on its row — see [io.music_assistant.client.data.repository.AiRadioRepository].
 * Both are required server-side, but they are defaulted here so one odd payload cannot fail the
 * decode of the whole list.
 */
@Serializable
data class ServerAiRadioStation(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String,
    @SerialName("source_playlist_id") val sourcePlaylistId: String = "",
    @SerialName("source_playlist_provider") val sourcePlaylistProvider: String = "library",
)

/**
 * One run of a station. `ai_radio/start`, `stop` and `status` all report this shape.
 *
 * [queueId] is the queue the run actually plays on, resolved server-side through
 * `get_active_queue`, so for a grouped player it is the leader's queue and not the player the
 * station was started on. It is the only trustworthy handle on a run's queue — never substitute
 * the currently selected player, which may have moved on since the run began. Null until the
 * run gets far enough to claim a queue.
 */
@Serializable
data class ServerAiRadioSession(
    @SerialName("session_id") val sessionId: String,
    @SerialName("station_id") val stationId: String,
    @SerialName("queue_id") val queueId: String? = null,
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
