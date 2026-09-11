package io.music_assistant.client.data.model.server

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Pins the radio-favorite schema gate against the server's `players/add_currently_playing_to_favorites`. */
class FavoriteCurrentlyPlayingSupportTest {
    @Test
    fun unknownSchemaIsUnsupported() {
        assertFalse(supportsFavoriteCurrentlyPlaying(null))
    }

    @Test
    fun schemaBelowThresholdIsUnsupported() {
        assertFalse(supportsFavoriteCurrentlyPlaying(20))
        assertFalse(supportsFavoriteCurrentlyPlaying(26))
    }

    @Test
    fun schemaAtOrAboveThresholdIsSupported() {
        assertTrue(supportsFavoriteCurrentlyPlaying(27))
        assertTrue(supportsFavoriteCurrentlyPlaying(LOCAL_SCHEMA_VERSION))
    }
}
