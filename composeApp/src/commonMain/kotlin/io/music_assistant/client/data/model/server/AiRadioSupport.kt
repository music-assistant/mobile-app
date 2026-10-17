package io.music_assistant.client.data.model.server

/** Provider domain of the AI Radio plugin, as reported by the `providers` command. */
const val AI_RADIO_DOMAIN = "ai_radio"

/**
 * Scopes the station list needs: `ai_radio/stations/list` demands `library.read`, and playing a
 * show is a `player_queues/play_media`, which demands `queues.control`.
 */
val AI_RADIO_STATIONS_SCOPES = listOf(Scope.LIBRARY_READ, Scope.QUEUES_CONTROL)

/**
 * Scopes the queue DJ menu needs. `ai_radio/hosts/list` demands `config.providers.read`, and
 * `ai_radio/queue_dj/status` and `/set` demand `queues.control`. A role can hold the station
 * scopes without `config.providers.read`, so the DJ has its own gate.
 */
val AI_RADIO_QUEUE_DJ_SCOPES = listOf(Scope.CONFIG_PROVIDERS_READ, Scope.QUEUES_CONTROL)

/**
 * First server API schema whose AI Radio shows play as Radio items (server #6372). It removed
 * `ai_radio/start`, `/stop` and `/status`, and changed the `ai_radio/queue_dj/status` entries
 * to objects. The client supports only this API, so older servers show no AI Radio.
 */
const val AI_RADIO_MIN_SCHEMA = 88

fun supportsAiRadio(schemaVersion: Int?): Boolean =
    schemaVersion != null && schemaVersion >= AI_RADIO_MIN_SCHEMA
