package io.music_assistant.sendspin.session

import io.music_assistant.sendspin.noise.SendspinPsk
import io.music_assistant.sendspin.wire.SendspinJson
import io.music_assistant.sendspin.wire.ServerMessage
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The three `server/unpair` branches: a stored record is deleted before
 * goodbye and close, a shared PSK record survives goodbye and close, and an
 * unpaired session ignores the message.
 */
internal class ServerUnpairTest : NoiseSessionTestHarness() {
    private fun goodbyeReason(json: String): String =
        SendspinJson.parseToJsonElement(json).jsonObject.getValue("payload").jsonObject
            .getValue("reason").jsonPrimitive.content

    @Test
    fun storedPubkeyRecordIsDeletedThenGoodbyeAndClose() = sessionTest { f ->
        val longTermPsk = ByteArray(32) { 0x21 }
        f.trustStore.recordLongTermPsk(longTermPsk, f.serverId)
        val server = FakeServer(f, longTermPsk)
        server.establish()
        server.completeHelloExchange()
        server.activate()
        assertIs<Event.Ready>(f.nextEvent())
        assertIs<Event.Activated>(f.nextEvent())

        server.sendJson("""{"type":"server/unpair","payload":{}}""")
        assertEquals("unpaired", goodbyeReason(server.receiveJson()))
        assertTrue(f.trustStore.records().none { it.serverId == f.serverId })
        assertEquals("unpaired", assertIs<SessionRejected>(f.awaitFailure()).reason)
        assertTrue(f.transport.closed)
    }

    @Test
    fun sharedPskRecordIsRetainedThroughGoodbyeAndClose() = sessionTest { f ->
        val sharedPsk = ByteArray(32) { 0x22 }
        f.trustStore.addSharedRecord(sharedPsk)
        val server = FakeServer(f, sharedPsk)
        server.establish()
        server.completeHelloExchange()
        server.activate()
        assertIs<Event.Ready>(f.nextEvent())
        assertIs<Event.Activated>(f.nextEvent())

        server.sendJson("""{"type":"server/unpair","payload":{}}""")
        assertEquals("unpaired", goodbyeReason(server.receiveJson()))
        assertTrue(f.trustStore.records().any { it.psk.contentEquals(sharedPsk) })
        assertIs<SessionRejected>(f.awaitFailure())
        assertTrue(f.transport.closed)
    }

    @Test
    fun unpairedSessionIgnoresServerUnpair() = sessionTest { f ->
        val server = FakeServer(f, SendspinPsk.SENTINEL_PSK)
        server.establish()
        server.completeHelloExchange()
        server.activate()
        assertIs<Event.Ready>(f.nextEvent())
        assertIs<Event.Activated>(f.nextEvent())

        server.sendJson("""{"type":"server/unpair","payload":{}}""")
        server.sendJson("""{"type":"server/state","payload":{}}""")
        assertIs<ServerMessage.State>(f.nextMessage())
        assertFalse(f.transport.closed)
    }
}
