package io.music_assistant.client.player.sendspin

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the version gate: the encrypted protocol is selected purely by the
 * authenticated MA session's schema version — at or above the threshold it is
 * encrypted with no fallback; below it (or unknown) legacy, unless the user
 * requires encryption, in which case no connection may be made at all.
 */
class EncryptionModeGateTest {
    @Test
    fun schemaAtThresholdSelectsEncrypted() {
        assertEquals(
            SendspinEncryptionMode.ENCRYPTED,
            SendspinEncryptionMode.resolve(schemaVersion = 45, requireEncryption = false),
        )
        assertEquals(
            SendspinEncryptionMode.ENCRYPTED,
            SendspinEncryptionMode.resolve(schemaVersion = 45, requireEncryption = true),
        )
        assertEquals(
            SendspinEncryptionMode.ENCRYPTED,
            SendspinEncryptionMode.resolve(schemaVersion = 99, requireEncryption = false),
        )
    }

    @Test
    fun schemaBelowThresholdSelectsLegacy() {
        assertEquals(
            SendspinEncryptionMode.LEGACY,
            SendspinEncryptionMode.resolve(schemaVersion = 44, requireEncryption = false),
        )
    }

    @Test
    fun unknownSchemaSelectsLegacy() {
        assertEquals(
            SendspinEncryptionMode.LEGACY,
            SendspinEncryptionMode.resolve(schemaVersion = null, requireEncryption = false),
        )
    }

    @Test
    fun requireEncryptionRefusesLegacyFallback() {
        assertEquals(
            SendspinEncryptionMode.ENCRYPTED_REQUIRED,
            SendspinEncryptionMode.resolve(schemaVersion = 44, requireEncryption = true),
        )
        assertEquals(
            SendspinEncryptionMode.ENCRYPTED_REQUIRED,
            SendspinEncryptionMode.resolve(schemaVersion = null, requireEncryption = true),
        )
    }
}
