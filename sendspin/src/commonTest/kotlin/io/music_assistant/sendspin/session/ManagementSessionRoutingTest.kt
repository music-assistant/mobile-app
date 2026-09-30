package io.music_assistant.sendspin.session

import io.music_assistant.sendspin.noise.SendspinPsk
import io.music_assistant.sendspin.wire.SendspinJson
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Session-level routing of management requests: activity and trust scoping,
 * one `management/result` per request in order, and respond-then-goodbye when
 * the requester removes its own record.
 */
internal class ManagementSessionRoutingTest : NoiseSessionTestHarness() {
    private fun result(json: String) = SendspinJson.parseToJsonElement(json).jsonObject

    @Test
    fun managementOnSessionWithoutManagementActivityIsPermissionDenied() = sessionTest { f ->
        val server = FakeServer(f, SendspinPsk.SENTINEL_PSK)
        server.establish()
        server.completeHelloExchange()
        server.activate() // playback only
        assertIs<Event.Ready>(f.nextEvent())
        assertIs<Event.Activated>(f.nextEvent())

        server.sendJson("""{"type":"management/list-records","payload":{}}""")
        val reply = result(server.receiveJson())
        assertEquals("management/result", reply.getValue("type").jsonPrimitive.content)
        assertEquals("permission_denied", reply.getValue("payload").jsonObject.getValue("result").jsonPrimitive.content)
        assertFalse(f.transport.closed, "connection stays open")
    }

    @Test
    fun backToBackRequestsProduceOneResultEachInOrder() = sessionTest { f ->
        val longTermPsk = ByteArray(32) { 0x44 }
        f.trustStore.recordLongTermPsk(longTermPsk, f.serverId)
        val server = FakeServer(f, longTermPsk)
        server.establish()
        server.completeHelloExchange()
        server.activate(activities = """["management"]""", activeRoles = "[]")
        assertIs<Event.Ready>(f.nextEvent())
        assertIs<Event.Activated>(f.nextEvent())

        server.sendJson("""{"type":"management/list-records","payload":{}}""")
        server.sendJson("""{"type":"management/get-pairing-config","payload":{}}""")

        val first = result(server.receiveJson())
        assertTrue(first.getValue("payload").jsonObject.getValue("data").jsonObject.containsKey("records"))
        val second = result(server.receiveJson())
        assertTrue(second.getValue("payload").jsonObject.getValue("data").jsonObject.containsKey("pairing_psk"))
    }

    @Test
    fun managementOnLongTermSessionSucceedsAndOwnRecordRemovalCloses() = sessionTest { f ->
        val longTermPsk = ByteArray(32) { 0x33 }
        f.trustStore.recordLongTermPsk(longTermPsk, f.serverId)
        val server = FakeServer(f, longTermPsk)
        server.establish()
        server.completeHelloExchange()
        server.activate(activities = """["management"]""", activeRoles = "[]")
        assertIs<Event.Ready>(f.nextEvent())
        assertIs<Event.Activated>(f.nextEvent())

        server.sendJson("""{"type":"management/list-records","payload":{}}""")
        assertEquals(
            "ok",
            result(server.receiveJson()).getValue("payload").jsonObject.getValue("result").jsonPrimitive.content,
        )

        val ownPskId = f.trustStore.pskIdOf(longTermPsk)
        server.sendJson("""{"type":"management/remove-record","payload":{"psk_id":"$ownPskId"}}""")
        assertEquals(
            "ok",
            result(server.receiveJson()).getValue("payload").jsonObject.getValue("result").jsonPrimitive.content,
        )
        val goodbye = result(server.receiveJson())
        assertEquals("client/goodbye", goodbye.getValue("type").jsonPrimitive.content)
        assertEquals("unauthorized", goodbye.getValue("payload").jsonObject.getValue("reason").jsonPrimitive.content)
        assertTrue(f.trustStore.records().none { it.serverId == f.serverId })
        assertEquals("unauthorized", assertIs<SessionRejected>(f.awaitFailure()).reason)
        assertTrue(f.transport.closed)
    }
}
