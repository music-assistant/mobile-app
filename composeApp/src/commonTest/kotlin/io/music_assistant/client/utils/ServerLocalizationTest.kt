package io.music_assistant.client.utils

import kotlin.test.Test
import kotlin.test.assertEquals

class ServerLocalizationTest {
    @Test
    fun schemaAtOrAboveThresholdUsesLocale() {
        assertEquals("nl-NL", serverLocalizationLocale(schemaVersion = 32, locale = "nl-NL"))
        assertEquals("nl-NL", serverLocalizationLocale(schemaVersion = 53, locale = "nl-NL"))
    }

    @Test
    fun schemaBelowThresholdKeepsLegacyPayload() {
        assertEquals(null, serverLocalizationLocale(schemaVersion = 31, locale = "nl-NL"))
    }

    @Test
    fun unknownSchemaKeepsLegacyPayload() {
        assertEquals(null, serverLocalizationLocale(schemaVersion = null, locale = "nl-NL"))
    }

    @Test
    fun blankLocaleKeepsLegacyPayload() {
        assertEquals(null, serverLocalizationLocale(schemaVersion = 32, locale = ""))
        assertEquals(null, serverLocalizationLocale(schemaVersion = 32, locale = "   "))
    }
}
