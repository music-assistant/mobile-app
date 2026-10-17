package io.music_assistant.client.data.model.server

import io.music_assistant.sendspin.api.WireVersion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EncryptedSendspinSupportTest {
    @Test
    fun requiresSchema45OrNewer() {
        assertFalse(supportsEncryptedSendspin(null))
        assertFalse(supportsEncryptedSendspin(44))
        assertTrue(supportsEncryptedSendspin(45))
        assertTrue(supportsEncryptedSendspin(59))
    }

    @Test
    fun theSchemaOrThe2107VersionPicksTheSpecWire() {
        listOf(
            Triple(null, null, WireVersion.LEGACY_9),
            Triple(65, "2.10.6", WireVersion.LEGACY_9),
            Triple(65, "2.10.7", WireVersion.SPEC_1_0),
            Triple(65, "2.10.12", WireVersion.SPEC_1_0),
            Triple(null, "2.10.7", WireVersion.SPEC_1_0),
            // a source build reports "0.0.0"
            Triple(65, "0.0.0", WireVersion.LEGACY_9),
            // 2.11.0b1-b3 ship aiosendspin 9.x at a schema below SERVER_2_11_SCHEMA
            Triple(91, "2.11.0b3", WireVersion.LEGACY_9),
            Triple(SERVER_2_11_SCHEMA, "2.11.0.dev2026101703", WireVersion.SPEC_1_0),
            Triple(SERVER_2_11_SCHEMA, null, WireVersion.SPEC_1_0),
        ).forEach { (schema, version, wire) ->
            assertEquals(wire, sendspinWireFor(schema, version), "schema $schema, version $version")
        }
    }
}
