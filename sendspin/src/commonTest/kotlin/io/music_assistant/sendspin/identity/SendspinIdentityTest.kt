package io.music_assistant.sendspin.identity

import io.music_assistant.sendspin.noise.SendspinBase64
import io.music_assistant.sendspin.noise.crypto.CryptographyKotlinNoiseCrypto
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class SendspinIdentityTest {
    private val crypto = CryptographyKotlinNoiseCrypto()

    @Test
    fun firstLoadCreatesAndPersistsIdentityAndPairingPsk() = runTest {
        val keyStore = FakeSendspinKeyStore()
        val store = SendspinTrustStore.load(keyStore, crypto)

        assertEquals(43, store.clientId.length)
        assertEquals(32, SendspinBase64.decode(store.clientId).size)
        assertEquals(32, store.pairingPsk.size)
        assertTrue(store.pairingToken().startsWith("SP:0"))
        assertEquals(2, keyStore.entries.size)
    }

    @Test
    fun subsequentLoadsYieldSameClientIdAndPairingToken() = runTest {
        val keyStore = FakeSendspinKeyStore()
        val first = SendspinTrustStore.load(keyStore, crypto)
        val second = SendspinTrustStore.load(keyStore, crypto)

        assertEquals(first.clientId, second.clientId)
        assertEquals(first.pairingToken(), second.pairingToken())
    }

    @Test
    fun missingStorageRegeneratesWithoutCrash() = runTest {
        val keyStore = FakeSendspinKeyStore()
        val first = SendspinTrustStore.load(keyStore, crypto)
        keyStore.entries.clear()

        val second = SendspinTrustStore.load(keyStore, crypto)
        assertNotEquals(first.clientId, second.clientId)
        assertEquals(43, second.clientId.length)
    }

    @Test
    fun corruptIdentityRegeneratesAndResetsTrustRecordsAtomically() = runTest {
        val keyStore = FakeSendspinKeyStore()
        val first = SendspinTrustStore.load(keyStore, crypto)
        first.recordLongTermPsk(ByteArray(32) { 1 }, serverId = "server-a")

        keyStore.corrupt("sendspin.identity")
        val second = SendspinTrustStore.load(keyStore, crypto)

        assertNotEquals(first.clientId, second.clientId)
        // Records belonging to the old identity are gone; only the fresh
        // pre-provisioned shared record remains.
        assertTrue(second.records().none { it.serverId == "server-a" })
        assertNotEquals(first.pairingToken(), second.pairingToken())
    }

    @Test
    fun identityWithMismatchedPublicKeyIsTreatedAsCorrupt() = runTest {
        val keyStore = FakeSendspinKeyStore()
        val first = SendspinTrustStore.load(keyStore, crypto)

        // Overwrite the stored public key half with a different valid key.
        val other = crypto.generateX25519KeyPair()
        val stored = keyStore.entries.getValue("sendspin.identity").decodeToString()
        val tampered = stored.replace(
            first.clientId,
            SendspinBase64.encode(other.publicKey),
        )
        keyStore.write("sendspin.identity", tampered.encodeToByteArray())

        val second = SendspinTrustStore.load(keyStore, crypto)
        assertNotEquals(first.clientId, second.clientId)
    }
}
