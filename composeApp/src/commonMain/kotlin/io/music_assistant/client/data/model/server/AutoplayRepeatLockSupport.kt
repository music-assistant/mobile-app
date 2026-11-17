package io.music_assistant.client.data.model.server

/**
 * From server 2.11, repeat ONE or ALL masks autoplay: the server reports it off and rejects a
 * request to turn it on. An older server keeps autoplay independent of repeat.
 */
fun supportsAutoplayRepeatLock(schemaVersion: Int?): Boolean =
    schemaVersion != null && schemaVersion >= SERVER_2_11_SCHEMA
