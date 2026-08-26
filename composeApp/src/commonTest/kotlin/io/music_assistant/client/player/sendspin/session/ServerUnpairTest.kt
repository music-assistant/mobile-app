package io.music_assistant.client.player.sendspin.session

import io.music_assistant.client.player.sendspin.noise.SendspinPsk
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Covers the three `server/unpair` branches at the session level: a
 * stored-pubkey record is deleted before goodbye+close, a shared-PSK record
 * is retained through goodbye+close, and an unpaired (trust `none`) session
 * ignores the message entirely.
 */
internal class ServerUnpairTest : EncryptedSessionTestHarness() {
    @Test
    fun storedPubkeyRecordIsDeletedThenGoodbyeAndClose() = runRealTime {
        val f = fixture(this)
        val longTermPsk = ByteArray(32) { 0x21 }
        f.trustStore.recordLongTermPsk(longTermPsk, f.serverId)
        val server = FakeServer(f, longTermPsk)
        f.session.start()
        server.establish()
        server.completeHelloExchange()
        server.activate()
        assertIs<SessionEvent.ProtocolReady>(f.nextEventSkippingNegotiation())
        assertIs<SessionEvent.Activated>(f.nextEvent())

        server.sendJson("""{"type":"server/unpair","payload":{}}""")
        val goodbye = Json.parseToJsonElement(server.receiveJson()).jsonObject
        assertEquals("client/goodbye", goodbye.getValue("type").jsonPrimitive.content)
        assertEquals(
            "unpaired",
            goodbye.getValue("payload").jsonObject.getValue("reason").jsonPrimitive.content,
        )
        assertTrue(f.trustStore.records().none { it.serverId == f.serverId })
        withTimeout(5_000) {
            while (f.transport.disconnectCount == 0) delay(10)
        }
    }

    @Test
    fun sharedPskRecordIsRetainedThroughGoodbyeAndClose() = runRealTime {
        val f = fixture(this)
        val sharedPsk = ByteArray(32) { 0x22 }
        f.trustStore.addSharedRecord(sharedPsk)
        val server = FakeServer(f, sharedPsk)
        f.session.start()
        server.establish()
        server.completeHelloExchange()
        server.activate()
        assertIs<SessionEvent.ProtocolReady>(f.nextEventSkippingNegotiation())
        assertIs<SessionEvent.Activated>(f.nextEvent())

        server.sendJson("""{"type":"server/unpair","payload":{}}""")
        val goodbye = Json.parseToJsonElement(server.receiveJson()).jsonObject
        assertEquals(
            "unpaired",
            goodbye.getValue("payload").jsonObject.getValue("reason").jsonPrimitive.content,
        )
        // The shared record may authenticate other servers; it survives.
        assertTrue(f.trustStore.records().any { it.psk.contentEquals(sharedPsk) })
        withTimeout(5_000) {
            while (f.transport.disconnectCount == 0) delay(10)
        }
    }

    @Test
    fun unpairedSessionIgnoresServerUnpair() = runRealTime {
        val f = fixture(this)
        val server = FakeServer(f, SendspinPsk.SENTINEL_PSK)
        f.session.start()
        server.establish()
        server.completeHelloExchange()
        server.activate()
        assertIs<SessionEvent.ProtocolReady>(f.nextEventSkippingNegotiation())
        assertIs<SessionEvent.Activated>(f.nextEvent())

        server.sendJson("""{"type":"server/unpair","payload":{}}""")
        // The connection continues unchanged: the next message still flows.
        server.sendJson("""{"type":"server/state","payload":{}}""")
        val app = f.session.applicationMessages.produceIn(this)
        assertEquals("""{"type":"server/state","payload":{}}""", withTimeout(5_000) { app.receive() })
        assertEquals(0, f.transport.disconnectCount)
        assertTrue(f.transport.sentBinaries.isNotEmpty())
    }
}
