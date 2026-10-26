package io.music_assistant.client.data.model.server

/**
 * First server API schema whose `music/search` takes `providers`, the 2.10.0 release. An older
 * server ignores the argument and searches everything, so the source picker would not work there.
 */
const val SEARCH_PROVIDERS_MIN_SCHEMA = 63

fun supportsSearchSources(schemaVersion: Int?): Boolean =
    schemaVersion != null && schemaVersion >= SEARCH_PROVIDERS_MIN_SCHEMA
