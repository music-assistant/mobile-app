package io.music_assistant.client.data.model.server

/**
 * From server 2.11, the server lists items that it knows only by an external id (provider
 * `external`), and `music/item` answers the item that it found for one.
 */
fun supportsExternalItems(schemaVersion: Int?): Boolean =
    schemaVersion != null && schemaVersion >= SERVER_2_11_SCHEMA
