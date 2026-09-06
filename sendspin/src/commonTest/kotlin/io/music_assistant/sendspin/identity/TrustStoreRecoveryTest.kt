package io.music_assistant.sendspin.identity

import io.music_assistant.sendspin.noise.PskCategory
import io.music_assistant.sendspin.noise.SendspinPsk
import io.music_assistant.sendspin.noise.crypto.CryptographyKotlinNoiseCrypto
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class TrustStoreRecoveryTest {
    private val crypto = CryptographyKotlinNoiseCrypto()

    @Test
    fun corruptTrustBlobResetsTrustButKeepsIdentity() = runTest {
        val keyStore = FakeSendspinKeyStore()
        val first = SendspinTrustStore.load(keyStore, crypto)
        first.recordLongTermPsk(ByteArray(32) { 5 }, serverId = "server-a")

        keyStore.corrupt("sendspin.trust")
        val second = SendspinTrustStore.load(keyStore, crypto)

        assertEquals(first.clientId, second.clientId)
        assertTrue(second.records().none { it.serverId == "server-a" })
        assertFalse(first.pairingPsk.contentEquals(second.pairingPsk))
    }

    @Test
    fun freshStoreHasOnePreProvisionedSharedRecord() = runTest {
        val store = SendspinTrustStore.load(FakeSendspinKeyStore(), crypto)
        val records = store.records()
        assertEquals(1, records.size)
        assertEquals(null, records[0].serverId)
        assertFalse(records[0].used)
    }

    @Test
    fun candidatesContainSentinelPairingAndRecords() = runTest {
        val store = SendspinTrustStore.load(FakeSendspinKeyStore(), crypto)
        val longTerm = ByteArray(32) { 9 }
        store.recordLongTermPsk(longTerm, serverId = "server-a")

        val candidates = store.pskCandidates()
        assertContentEquals(
            SendspinPsk.SENTINEL_PSK,
            candidates.single { it.category == PskCategory.SENTINEL }.psk,
        )
        assertContentEquals(
            store.pairingPsk,
            candidates.single { it.category == PskCategory.PAIRING }.psk,
        )
        val stored = candidates.single { it.category == PskCategory.LONG_TERM_STORED }
        assertContentEquals(longTerm, stored.psk)
        assertEquals("server-a", stored.serverId)
        assertEquals(1, candidates.count { it.category == PskCategory.LONG_TERM_SHARED })
    }

    @Test
    fun disablingPairingMethodRemovesCandidateImmediately() = runTest {
        val store = SendspinTrustStore.load(FakeSendspinKeyStore(), crypto)
        assertTrue(store.pskCandidates().any { it.category == PskCategory.PAIRING })
        store.setPairingPskEnabled(false)
        assertTrue(store.pskCandidates().none { it.category == PskCategory.PAIRING })
        store.setPairingPskEnabled(true)
        assertTrue(store.pskCandidates().any { it.category == PskCategory.PAIRING })
    }

    @Test
    fun mutationsPersistAcrossReload() = runTest {
        val keyStore = FakeSendspinKeyStore()
        val first = SendspinTrustStore.load(keyStore, crypto)
        val longTerm = ByteArray(32) { 3 }
        first.recordLongTermPsk(longTerm, serverId = "server-b")
        first.markRecordUsed(longTerm)
        first.setUnpairedAccessEnabled(true)

        val second = SendspinTrustStore.load(keyStore, crypto)
        val record = second.records().single { it.serverId == "server-b" }
        assertContentEquals(longTerm, record.psk)
        assertTrue(record.used)
        assertTrue(second.unpairedAccessEnabled)
    }

    @Test
    fun recordForSameServerIsReplacedNotDuplicated() = runTest {
        val store = SendspinTrustStore.load(FakeSendspinKeyStore(), crypto)
        store.recordLongTermPsk(ByteArray(32) { 1 }, serverId = "server-a")
        store.recordLongTermPsk(ByteArray(32) { 2 }, serverId = "server-a")

        val records = store.records().filter { it.serverId == "server-a" }
        assertEquals(1, records.size)
        assertContentEquals(ByteArray(32) { 2 }, records[0].psk)
    }

    @Test
    fun removeRecordDeletesOnlyMatchingPsk() = runTest {
        val store = SendspinTrustStore.load(FakeSendspinKeyStore(), crypto)
        val pskA = ByteArray(32) { 1 }
        store.recordLongTermPsk(pskA, serverId = "server-a")
        store.recordLongTermPsk(ByteArray(32) { 2 }, serverId = "server-b")

        assertTrue(store.removeRecord(pskA))
        assertFalse(store.removeRecord(pskA))
        assertTrue(store.records().any { it.serverId == "server-b" })
        assertTrue(store.records().none { it.serverId == "server-a" })
    }

    @Test
    fun everyMutationRewritesWholeBlobOnce() = runTest {
        val keyStore = FakeSendspinKeyStore()
        val store = SendspinTrustStore.load(keyStore, crypto)
        val writesAfterLoad = keyStore.writeCount
        store.setUnpairedAccessEnabled(true)
        assertEquals(writesAfterLoad + 1, keyStore.writeCount)
    }

    @Test
    fun pairingPskRotationChangesToken() = runTest {
        val store = SendspinTrustStore.load(FakeSendspinKeyStore(), crypto)
        val before = store.pairingToken()
        store.setPairingPsk(ByteArray(32) { 7 })
        assertNotEquals(before, store.pairingToken())
    }
}
