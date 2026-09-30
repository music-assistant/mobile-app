package io.music_assistant.sendspin.session

import io.music_assistant.sendspin.noise.HandshakeFailedException
import io.music_assistant.sendspin.noise.NoiseFraming
import io.music_assistant.sendspin.noise.PskCategory
import io.music_assistant.sendspin.noise.SendspinBase64
import io.music_assistant.sendspin.noise.SendspinPsk
import io.music_assistant.sendspin.wire.SendspinJson
import io.music_assistant.sendspin.wire.ServerMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Establishment, hello shape, activation gating and the spec's ordered
 * rejection rules, audio byte-exactness (plain and fragmented), in-band
 * re-handshake, silent handshake failure, and the Pairing PSK flow.
 */
internal class NoiseSessionTest : NoiseSessionTestHarness() {
    private fun goodbyeReason(json: String): String =
        SendspinJson.parseToJsonElement(json).jsonObject.getValue("payload").jsonObject
            .getValue("reason").jsonPrimitive.content

    @Test
    fun establishesActivatesAndGatesOutboundTraffic() = sessionTest { f ->
        val server = FakeServer(f, SendspinPsk.SENTINEL_PSK)
        server.establish()
        val clientHello = server.completeHelloExchange()

        val payload = SendspinJson.parseToJsonElement(clientHello).jsonObject.getValue("payload").jsonObject
        assertFalse(payload.containsKey("client_id"))
        assertFalse(payload.containsKey("version"))
        assertEquals("none", payload.getValue("trust_level").jsonPrimitive.content)
        assertEquals(
            "pairing_psk",
            payload.getValue("supported_pair_methods").jsonArray[0].jsonObject.getValue("method").jsonPrimitive.content,
        )
        assertTrue(payload.getValue("unpaired_access").jsonObject.getValue("enabled").jsonPrimitive.content.toBoolean())

        val ready = assertIs<Event.Ready>(f.nextEvent()).info
        assertEquals(f.serverId, ready.serverId)
        assertEquals("Enc Server", ready.serverName)
        assertEquals(TrustLevel.NONE, ready.trustLevel)

        // Outbound traffic is gated until the first admissible activation.
        val gatedSend = launch { f.session.send("""{"type":"client/time"}""") }
        server.activate()
        val activated = assertIs<Event.Activated>(f.nextEvent()).activation
        assertEquals(listOf("playback"), activated.activities)
        assertEquals(listOf("player@v1"), activated.activeRoles)
        gatedSend.join()
        assertEquals("""{"type":"client/time"}""", server.receiveJson())
    }

    @Test
    fun backToBackMessagesAfterActivationKeepSourceOrder() = sessionTest { f ->
        val server = FakeServer(f, SendspinPsk.SENTINEL_PSK)
        server.establish()
        server.completeHelloExchange()
        server.activate()
        server.sendJson("""{"type":"server/state","payload":{}}""")
        server.sendAudio(4, 77, byteArrayOf(9, 9, 9))
        server.sendJson("""{"type":"stream/end"}""")

        assertIs<Event.Ready>(f.nextEvent())
        assertIs<Event.Activated>(f.nextEvent())
        assertIs<ServerMessage.State>(f.nextMessage())
        val audio = assertIs<Event.Audio>(f.nextEvent())
        assertEquals(77, audio.timestamp)
        assertContentEquals(byteArrayOf(9, 9, 9), audio.data)
        assertIs<ServerMessage.StreamEnd>(f.nextMessage())
    }

    @Test
    fun audioIsByteExactIncludingFragmentedReassembly() = sessionTest { f ->
        val server = FakeServer(f, SendspinPsk.SENTINEL_PSK)
        server.establish()
        server.completeHelloExchange()
        server.activate()
        assertIs<Event.Ready>(f.nextEvent())
        assertIs<Event.Activated>(f.nextEvent())

        val small = ByteArray(100) { it.toByte() }
        server.sendAudio(4, 1, small)
        assertContentEquals(small, assertIs<Event.Audio>(f.nextEvent()).data)

        val large = ByteArray(NoiseFraming.MAX_UNFRAGMENTED_PAYLOAD + 5_000) { (it % 251).toByte() }
        server.sendAudio(4, 2, large)
        assertContentEquals(large, assertIs<Event.Audio>(f.nextEvent()).data)
    }

    @Test
    fun rehandshakeSwapsKeysRepeatsHelloAndResumesAfterActivate() = sessionTest { f ->
        val server = FakeServer(f, SendspinPsk.SENTINEL_PSK)
        server.establish()
        server.completeHelloExchange()
        server.activate()
        assertIs<Event.Ready>(f.nextEvent())
        assertIs<Event.Activated>(f.nextEvent())

        server.rehandshake(f.trustStore.pairingPsk)
        // A send issued during the exchange must not interleave: it flushes
        // only after the post-re-handshake activation.
        val queuedSend = launch { f.session.send("""{"type":"client/time"}""") }

        val clientHello = server.completeHelloExchange()
        assertEquals(
            "none",
            SendspinJson.parseToJsonElement(clientHello).jsonObject.getValue("payload").jsonObject
                .getValue("trust_level").jsonPrimitive.content,
        )
        assertEquals(PskCategory.PAIRING, assertIs<Event.Ready>(f.nextEvent()).info.matchedPskCategory)

        server.activate(activities = """["pairing"]""", activeRoles = "[]", pairing = """{"method":"pairing_psk"}""")
        assertIs<Event.Activated>(f.nextEvent())

        // Quiesced through the pairing activity: only client/pair-finalize crosses.
        val finalize = SendspinJson.parseToJsonElement(server.receiveJson()).jsonObject
        assertEquals("client/pair-finalize", finalize.getValue("type").jsonPrimitive.content)
        val newPsk = SendspinBase64.decode(
            finalize.getValue("payload").jsonObject.getValue("long_term_psk").jsonPrimitive.content,
        )
        server.sendJson("""{"type":"server/pair-finalize","payload":{}}""")

        server.rehandshake(newPsk)
        server.completeHelloExchange()
        assertIs<Event.Ready>(f.nextEvent())
        server.activate()
        assertIs<Event.Activated>(f.nextEvent())
        queuedSend.join()
        assertEquals("""{"type":"client/time"}""", server.receiveJson())
    }

    @Test
    fun unknownPskIdFailsSilentlyWithoutApplicationError() = sessionTest { f ->
        val server = FakeServer(f, ByteArray(32) { 0x55 })
        launch { runCatching { server.establish() } }
        assertIs<HandshakeFailedException>(f.awaitFailure())
        assertTrue(f.transport.closed, "socket closed on handshake failure")
        // The only outbound frame remains client/init.
        assertEquals(1, f.transport.sent.size)
    }

    @Test
    fun sentinelPlaybackWithoutUnpairedAccessClosesWithPairingRequired() = sessionTest(unpairedAccess = false) { f ->
        val server = FakeServer(f, SendspinPsk.SENTINEL_PSK)
        server.establish()
        server.completeHelloExchange()
        assertIs<Event.Ready>(f.nextEvent())

        server.activate(activities = """["playback"]""", activeRoles = """["player@v1"]""")
        assertEquals("pairing_required", goodbyeReason(server.receiveJson()))
        assertEquals("pairing_required", assertIs<SessionRejected>(f.awaitFailure()).reason)
        assertTrue(f.transport.closed)
    }

    @Test
    fun disallowedActivitySetClosesWithUnauthorized() = sessionTest(unpairedAccess = false) { f ->
        val server = FakeServer(f, SendspinPsk.SENTINEL_PSK)
        server.establish()
        server.completeHelloExchange()
        assertIs<Event.Ready>(f.nextEvent())

        server.activate(activities = """["playback","management"]""", activeRoles = "[]")
        assertEquals("unauthorized", goodbyeReason(server.receiveJson()))
        assertEquals("unauthorized", assertIs<SessionRejected>(f.awaitFailure()).reason)
    }

    @Test
    fun unsupportedPairingMethodRepliesPairAbortAndStaysOpen() = sessionTest { f ->
        val server = FakeServer(f, SendspinPsk.SENTINEL_PSK)
        server.establish()
        server.completeHelloExchange()
        assertIs<Event.Ready>(f.nextEvent())

        server.activate(activities = """["pairing"]""", activeRoles = "[]", pairing = """{"method":"static_pin"}""")
        val abort = SendspinJson.parseToJsonElement(server.receiveJson()).jsonObject
        assertEquals("pair/abort", abort.getValue("type").jsonPrimitive.content)
        assertEquals(
            "method_not_supported",
            abort.getValue("payload").jsonObject.getValue("reason").jsonPrimitive.content,
        )
        assertFalse(f.transport.closed, "connection stays open")

        server.activate(activities = "[]", activeRoles = "[]")
        assertIs<Event.Activated>(f.nextEvent())
    }

    @Test
    fun sourceRoleAtNoneTrustClosesWithUnauthorized() = sessionTest { f ->
        val server = FakeServer(f, SendspinPsk.SENTINEL_PSK)
        server.establish()
        server.completeHelloExchange()
        assertIs<Event.Ready>(f.nextEvent())

        server.activate(activities = """["playback"]""", activeRoles = """["source@v1"]""")
        assertEquals("unauthorized", goodbyeReason(server.receiveJson()))
        assertIs<SessionRejected>(f.awaitFailure())
    }

    @Test
    fun roleViolationIsUnauthorizedNotPairingRequired() = sessionTest(unpairedAccess = false) { f ->
        val server = FakeServer(f, SendspinPsk.SENTINEL_PSK)
        server.establish()
        server.completeHelloExchange()
        assertIs<Event.Ready>(f.nextEvent())

        server.activate(activities = """["playback"]""", activeRoles = """["source@v1"]""")
        assertEquals("unauthorized", goodbyeReason(server.receiveJson()))
        assertIs<SessionRejected>(f.awaitFailure())
    }

    @Test
    fun rejectedPairingActivationEndsThePriorAttemptSoALateFinalizePersistsNothing() = sessionTest { f ->
        val server = pairingPreamble(f)
        server.receiveJson() // the first attempt's client/pair-finalize

        server.activate(activities = """["pairing"]""", activeRoles = "[]", pairing = """{"method":"static_pin"}""")
        val abort = SendspinJson.parseToJsonElement(server.receiveJson()).jsonObject
        assertEquals("pair/abort", abort.getValue("type").jsonPrimitive.content)
        assertFalse(f.transport.closed)

        server.sendJson("""{"type":"server/pair-finalize","payload":{}}""")
        server.sendJson("""{"type":"server/state","payload":{}}""")
        assertIs<ServerMessage.State>(f.nextMessage())
        assertTrue(f.trustStore.records().none { it.serverId == f.serverId })
    }

    @Test
    fun pairingPskFlowPersistsRecordOnlyAfterServerFinalizeThenRehandshakesToIt() = sessionTest { f ->
        val server = pairingPreamble(f)

        val finalize = SendspinJson.parseToJsonElement(server.receiveJson()).jsonObject
        assertEquals("client/pair-finalize", finalize.getValue("type").jsonPrimitive.content)
        val newPsk = SendspinBase64.decode(
            finalize.getValue("payload").jsonObject.getValue("long_term_psk").jsonPrimitive.content,
        )
        assertEquals(32, newPsk.size)
        assertTrue(f.trustStore.records().none { it.serverId == f.serverId }, "nothing persisted before the ack")

        server.sendJson("""{"type":"server/pair-finalize","payload":{}}""")
        withTimeout(AWAIT_MILLIS) {
            while (f.trustStore.records().none { it.serverId == f.serverId }) delay(10)
        }
        assertContentEquals(newPsk, f.trustStore.records().single { it.serverId == f.serverId }.psk)

        server.rehandshake(newPsk)
        val clientHello = server.completeHelloExchange()
        assertEquals(
            "user",
            SendspinJson.parseToJsonElement(clientHello).jsonObject.getValue("payload").jsonObject
                .getValue("trust_level").jsonPrimitive.content,
        )
        assertEquals(TrustLevel.USER, assertIs<Event.Ready>(f.nextEvent()).info.trustLevel)
    }

    @Test
    fun cancellingActivateDiscardsAttemptAndAdmitsANewOne() = sessionTest { f ->
        val server = pairingPreamble(f)
        val firstPsk = SendspinBase64.decode(
            SendspinJson.parseToJsonElement(server.receiveJson()).jsonObject
                .getValue("payload").jsonObject.getValue("long_term_psk").jsonPrimitive.content,
        )

        server.activate(activities = """["pairing"]""", activeRoles = "[]", pairing = """{"method":"pairing_psk"}""")
        assertIs<Event.Activated>(f.nextEvent())
        val secondPsk = SendspinBase64.decode(
            SendspinJson.parseToJsonElement(server.receiveJson()).jsonObject
                .getValue("payload").jsonObject.getValue("long_term_psk").jsonPrimitive.content,
        )

        server.sendJson("""{"type":"server/pair-finalize","payload":{}}""")
        withTimeout(AWAIT_MILLIS) {
            while (f.trustStore.records().none { it.serverId == f.serverId }) delay(10)
        }
        val record = f.trustStore.records().single { it.serverId == f.serverId }
        assertContentEquals(secondPsk, record.psk)
        assertFalse(record.psk.contentEquals(firstPsk))
    }

    @Test
    fun pairAbortDiscardsAttemptPersistingNothing() = sessionTest { f ->
        val server = pairingPreamble(f)
        server.receiveJson() // client/pair-finalize

        server.sendJson("""{"type":"pair/abort","payload":{"reason":"user_cancelled"}}""")
        server.sendJson("""{"type":"server/pair-finalize","payload":{}}""")
        server.sendJson("""{"type":"server/state","payload":{}}""")
        assertIs<ServerMessage.State>(f.nextMessage())
        assertTrue(f.trustStore.records().none { it.serverId == f.serverId })
    }

    @Test
    fun pairingAttemptTimeoutSendsPairAbort() = sessionTest(pairingAttemptTimeoutMillis = 150) { f ->
        val server = pairingPreamble(f)
        server.receiveJson() // client/pair-finalize

        val abort = SendspinJson.parseToJsonElement(server.receiveJson()).jsonObject
        assertEquals("pair/abort", abort.getValue("type").jsonPrimitive.content)
        assertEquals("attempt_timeout", abort.getValue("payload").jsonObject.getValue("reason").jsonPrimitive.content)
        assertTrue(f.trustStore.records().none { it.serverId == f.serverId })
    }

    @Test
    fun cleanServerCloseEndsRunNormallyAndLaterSendsFail() = sessionTest { f ->
        val server = FakeServer(f, SendspinPsk.SENTINEL_PSK)
        server.establish()
        server.completeHelloExchange()
        server.activate()
        assertIs<Event.Ready>(f.nextEvent())
        assertIs<Event.Activated>(f.nextEvent())

        f.transport.serverDrops()
        f.awaitClean()
        assertFailsWith<IllegalStateException> { f.session.send("{}") }
    }

    @Test
    fun connectionFailureSurfacesAsTransportLost() = sessionTest { f ->
        val server = FakeServer(f, SendspinPsk.SENTINEL_PSK)
        server.establish()
        server.completeHelloExchange()
        assertIs<Event.Ready>(f.nextEvent())

        f.transport.serverDrops(IllegalStateException("reset"))
        assertEquals("reset", assertIs<TransportLost>(f.awaitFailure()).cause?.message)
    }

    @Test
    fun burstOfInterleavedFramesIsDeliveredInOrder() = sessionTest { f ->
        val server = FakeServer(f, SendspinPsk.SENTINEL_PSK)
        server.establish()
        server.completeHelloExchange()
        server.activate()
        assertIs<Event.Ready>(f.nextEvent())
        assertIs<Event.Activated>(f.nextEvent())

        val total = 1000
        repeat(total) { i ->
            if (i % 2 == 0) {
                server.sendJson("""{"type":"server/state","payload":{"seq":$i}}""")
            } else {
                server.sendAudio(4, i.toLong(), byteArrayOf((i % 127).toByte()))
            }
        }
        repeat(total) { i ->
            if (i % 2 == 0) {
                assertIs<ServerMessage.State>(f.nextMessage(), "text frame $i")
            } else {
                val audio = assertIs<Event.Audio>(f.nextEvent(), "binary frame $i")
                assertEquals(i.toLong(), audio.timestamp)
            }
        }
    }
}
