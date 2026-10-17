package io.music_assistant.client.data.model.server

import io.music_assistant.sendspin.api.WireVersion

/** First MA schema guaranteeing complete encrypted Sendspin support. */
const val ENCRYPTED_SENDSPIN_MIN_SCHEMA = 45

fun supportsEncryptedSendspin(schemaVersion: Int?): Boolean =
    schemaVersion != null && schemaVersion >= ENCRYPTED_SENDSPIN_MIN_SCHEMA

/**
 * Server 2.11 ships aiosendspin 10.x (spec 1.0.0-rc1); everything older, and a
 * server whose schema is unknown, gets the 9.x wire that has always worked.
 */
fun sendspinWireFor(schemaVersion: Int?): WireVersion =
    if (schemaVersion != null && schemaVersion >= SERVER_2_11_SCHEMA) WireVersion.SPEC_1_0 else WireVersion.LEGACY_9
