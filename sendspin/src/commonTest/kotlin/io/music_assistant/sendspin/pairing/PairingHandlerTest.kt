package io.music_assistant.sendspin.pairing

import io.music_assistant.sendspin.identity.FakeSendspinKeyStore
import io.music_assistant.sendspin.identity.SendspinTrustStore
import io.music_assistant.sendspin.noise.SendspinBase64
import io.music_assistant.sendspin.noise.crypto.CryptographyKotlinNoiseCrypto
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PairingHandlerTest {
    private val crypto = CryptographyKotlinNoiseCrypto()

    private suspend fun newFixture(): Pair<PairingHandler, SendspinTrustStore> {
        val trustStore = SendspinTrustStore.load(FakeSendspinKeyStore(), crypto)
        return PairingHandler(crypto, trustStore) to trustStore
    }

    @Test
    fun startAttemptSendsPairFinalizeWithFreshPsk() = runTest {
        val (handler, _) = newFixture()
        val sent = mutableListOf<String>()
        handler.startAttempt("srv-1") { sent.add(it) }

        assertEquals(1, sent.size)
        val parsed = Json.parseToJsonElement(sent[0]).jsonObject
        assertEquals("client/pair-finalize", parsed.getValue("type").jsonPrimitive.content)
        val pskB64 = parsed.getValue("payload").jsonObject
            .getValue("long_term_psk").jsonPrimitive.content
        assertEquals(43, pskB64.length)
        assertEquals(32, SendspinBase64.decode(pskB64).size)
    }

    @Test
    fun recordIsPersistedOnlyAfterServerFinalize() = runTest {
        val (handler, trustStore) = newFixture()
        val sent = mutableListOf<String>()
        handler.startAttempt("srv-1") { sent.add(it) }
        assertTrue(trustStore.records().none { it.serverId == "srv-1" }, "nothing persisted yet")

        assertTrue(handler.completeAttempt())
        val record = trustStore.records().single { it.serverId == "srv-1" }
        val sentPsk = Json.parseToJsonElement(sent[0]).jsonObject
            .getValue("payload").jsonObject.getValue("long_term_psk").jsonPrimitive.content
        assertContentEquals(SendspinBase64.decode(sentPsk), record.psk)
        assertNull(handler.pending)
    }

    @Test
    fun cancellingActivateDiscardsAttemptPersistingNothing() = runTest {
        val (handler, trustStore) = newFixture()
        handler.startAttempt("srv-1") { }

        assertTrue(handler.discardAttempt())
        assertFalse(handler.completeAttempt(), "stale finalize after abandonment is ignored")
        assertTrue(trustStore.records().none { it.serverId == "srv-1" })
    }

    @Test
    fun abortSendsReasonAndPersistsNothing() = runTest {
        val (handler, trustStore) = newFixture()
        val attempt = handler.startAttempt("srv-1") { }

        val sent = mutableListOf<String>()
        handler.abortAttempt(attempt, "attempt_timeout") { sent.add(it) }
        val parsed = Json.parseToJsonElement(sent.single()).jsonObject
        assertEquals("pair/abort", parsed.getValue("type").jsonPrimitive.content)
        assertEquals(
            "attempt_timeout",
            parsed.getValue("payload").jsonObject.getValue("reason").jsonPrimitive.content,
        )
        assertTrue(trustStore.records().none { it.serverId == "srv-1" })
        assertNull(handler.pending)
    }

    @Test
    fun abortOfASupersededAttemptSendsNothingAndKeepsTheNewAttempt() = runTest {
        val (handler, _) = newFixture()
        val stale = handler.startAttempt("srv-1") { }
        handler.discardAttempt()
        val fresh = handler.startAttempt("srv-2") { }

        // A stale timeout firing after a newer attempt started must not
        // abort or clear the newer attempt.
        val sent = mutableListOf<String>()
        handler.abortAttempt(stale, "attempt_timeout") { sent.add(it) }
        assertTrue(sent.isEmpty())
        assertEquals(fresh, handler.pending)
    }

    @Test
    fun completeWithoutPendingAttemptIsIgnored() = runTest {
        val (handler, trustStore) = newFixture()
        assertFalse(handler.completeAttempt())
        assertEquals(1, trustStore.records().size, "only the pre-provisioned shared record")
    }
}
