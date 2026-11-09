package io.music_assistant.client.data.model.server

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArtistReleaseListingSupportTest {
    @Test
    fun `server 2_11 lists appears on and the discography`() {
        assertTrue(supportsArtistAppearsOnAndDiscography(SERVER_2_11_SCHEMA))
    }

    @Test
    fun `server 2_10 and an unknown server do not`() {
        assertFalse(supportsArtistAppearsOnAndDiscography(65))
        assertFalse(supportsArtistAppearsOnAndDiscography(null))
    }
}
