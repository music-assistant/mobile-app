package io.music_assistant.client.player.sendspin.noise

import io.music_assistant.client.player.sendspin.noise.crypto.CryptographyKotlinNoiseCrypto
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class PskIdDerivationTest {
    private val crypto = CryptographyKotlinNoiseCrypto()

    @Test
    fun sentinelPskIsHashOfPublishedLabel() = runTest {
        assertContentEquals(
            SendspinPsk.SENTINEL_PSK,
            crypto.sha256(SendspinPsk.SENTINEL_LABEL),
        )
    }

    @Test
    fun sentinelPskIdMatchesPublishedConstant() = runTest {
        assertEquals(
            SendspinPsk.SENTINEL_PSK_ID,
            SendspinPsk.pskId(crypto, SendspinPsk.SENTINEL_PSK),
        )
    }

    @Test
    fun pskIdIs43CharacterBase64Url() = runTest {
        val id = SendspinPsk.pskId(crypto, ByteArray(32) { it.toByte() })
        assertEquals(43, id.length)
        assertEquals(32, SendspinBase64.decode(id).size)
    }
}
