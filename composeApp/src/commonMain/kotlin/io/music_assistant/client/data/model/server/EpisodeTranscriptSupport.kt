package io.music_assistant.client.data.model.server

/**
 * First server API schema that has `music/podcasts/podcast_episode_transcript`. The commit that
 * added it left `API_SCHEMA_VERSION` at 80, so 81 is the first version that guarantees it.
 */
const val EPISODE_TRANSCRIPT_MIN_SCHEMA = 81

/** Whether the server answers `music/podcasts/podcast_episode_transcript` (server 2.11). */
fun supportsEpisodeTranscript(schemaVersion: Int?): Boolean =
    schemaVersion != null && schemaVersion >= EPISODE_TRANSCRIPT_MIN_SCHEMA
