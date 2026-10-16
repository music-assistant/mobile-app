package io.music_assistant.client.data.model.server

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A station profile from `ai_radio/stations/list`. The server also exposes each station as a
 * Radio item ([uri]); that item is what plays.
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
) {
    val uri: String get() = aiRadioStationUri(id)
}

/**
 * The show [stationId] as a finite Radio item, played with `player_queues/play_media` like any
 * other radio. The provider is single-instance, so its instance id is the domain.
 */
fun aiRadioStationUri(stationId: String): String = "$AI_RADIO_DOMAIN://radio/$stationId"

/**
 * One entry of `ai_radio/queue_dj/status`, keyed by queue id. [stationId] is empty for a DJ the
 * user picked by hand, and names the show for a DJ that playing a show armed.
 */
@Serializable
data class ServerAiRadioQueueDj(
    @SerialName("host_id") val hostId: String,
    @SerialName("station_id") val stationId: String = "",
)

/**
 * A host from `ai_radio/hosts/list`: the persona that speaks between tracks. Only the fields
 * the queue DJ menu shows are bound; prompts, voice and sections are authored in the web
 * frontend.
 */
@Serializable
data class ServerAiRadioHost(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String,
)
