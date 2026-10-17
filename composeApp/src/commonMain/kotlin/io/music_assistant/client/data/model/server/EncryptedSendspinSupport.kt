package io.music_assistant.client.data.model.server

import io.music_assistant.sendspin.api.WireVersion

/** First MA schema guaranteeing complete encrypted Sendspin support. */
const val ENCRYPTED_SENDSPIN_MIN_SCHEMA = 45

fun supportsEncryptedSendspin(schemaVersion: Int?): Boolean =
    schemaVersion != null && schemaVersion >= ENCRYPTED_SENDSPIN_MIN_SCHEMA

/** First 2.10 patch release that ships aiosendspin 10.x; it keeps the 2.10 schema. */
private const val SPEC_1_0_FIRST_2_10_PATCH = 7

private val SERVER_2_10_PATCH = Regex("""^2\.10\.(\d+)""")

/**
 * Server 2.11 and server 2.10.7 or newer ship aiosendspin 10.x (spec 1.0.0-rc1). The schema
 * cannot tell 2.10.7 from 2.10.6, so the server version does. An earlier 2.11 beta (b1-b3 ship
 * 9.x), a source build ("0.0.0"), and an unknown server get the 9.x wire, which a 10.x server
 * still accepts while its `allow_legacy_clients` setting stays on.
 */
fun sendspinWireFor(schemaVersion: Int?, serverVersion: String?): WireVersion = when {
    schemaVersion != null && schemaVersion >= SERVER_2_11_SCHEMA -> WireVersion.SPEC_1_0
    (serverVersion?.server210Patch() ?: 0) >= SPEC_1_0_FIRST_2_10_PATCH -> WireVersion.SPEC_1_0
    else -> WireVersion.LEGACY_9
}

/** The patch number of a 2.10 release ("2.10.7" -> 7), or null for any other version. */
private fun String.server210Patch(): Int? = SERVER_2_10_PATCH.find(this)?.groupValues?.get(1)?.toIntOrNull()
