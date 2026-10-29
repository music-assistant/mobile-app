package io.music_assistant.client.data.model.server

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlaylistAccessSupportTest {
    @Test
    fun recordsFromTheServer211Schema() {
        assertFalse(supportsPlaylistAccess(null))
        assertFalse(supportsPlaylistAccess(SERVER_2_11_SCHEMA - 1))
        assertTrue(supportsPlaylistAccess(SERVER_2_11_SCHEMA))
    }
}
