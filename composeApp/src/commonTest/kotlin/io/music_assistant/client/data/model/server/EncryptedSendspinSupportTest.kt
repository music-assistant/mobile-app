package io.music_assistant.client.data.model.server

import kotlin.test.Test
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
}
