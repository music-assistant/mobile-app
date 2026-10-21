package io.music_assistant.client.data.model.server

/**
 * From server 2.11, favorites are per user. The server sends null `favorite` in every
 * `media_item_added`, `media_item_updated` and `media_item_deleted` event, and sends the state
 * of the user in a `favorite_updated` event.
 */
fun sendsFavoriteUpdates(schemaVersion: Int?): Boolean =
    schemaVersion != null && schemaVersion >= SERVER_2_11_SCHEMA

/**
 * From server 2.11, a favorite has three states: true is a like, false is a dislike, null is no
 * choice. An older server sends false for "not a favorite", so a false is a dislike only here.
 */
fun supportsDislike(schemaVersion: Int?): Boolean =
    schemaVersion != null && schemaVersion >= SERVER_2_11_SCHEMA
