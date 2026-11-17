package io.music_assistant.client.data.model.server

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExternalItemSupportTest {
    @Test
    fun externalItemsFromTheServer211Schema() {
        assertFalse(supportsExternalItems(null))
        assertFalse(supportsExternalItems(SERVER_2_11_SCHEMA - 1))
        assertTrue(supportsExternalItems(SERVER_2_11_SCHEMA))
    }
}
