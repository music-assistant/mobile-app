package io.music_assistant.client.data.model.server

/** First MA schema guaranteeing complete encrypted Sendspin support. */
const val ENCRYPTED_SENDSPIN_MIN_SCHEMA = 45

fun supportsEncryptedSendspin(schemaVersion: Int?): Boolean =
    schemaVersion != null && schemaVersion >= ENCRYPTED_SENDSPIN_MIN_SCHEMA
