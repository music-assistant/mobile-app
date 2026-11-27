package io.music_assistant.client.data.model.server

const val SHARE_LINKS_DOMAIN = "share_provider"
private const val SHARE_LINKS_MIN_SCHEMA = 122

/** First schema that exposes share link commands when the Share Links plugin is loaded. */
fun supportsShareLinks(schemaVersion: Int?): Boolean =
    schemaVersion != null && schemaVersion >= SHARE_LINKS_MIN_SCHEMA
