package io.music_assistant.client.data.model.server

/** First server API schema with `players/add_currently_playing_to_favorites`. */
const val FAVORITE_CURRENTLY_PLAYING_MIN_SCHEMA = 27

fun supportsFavoriteCurrentlyPlaying(schemaVersion: Int?): Boolean =
    schemaVersion != null && schemaVersion >= FAVORITE_CURRENTLY_PLAYING_MIN_SCHEMA
