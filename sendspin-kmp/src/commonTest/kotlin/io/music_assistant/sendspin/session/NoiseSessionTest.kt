package io.music_assistant.sendspin.session

import io.music_assistant.sendspin.api.WireVersion
import io.music_assistant.sendspin.fakes.FakeNoiseServer
import io.music_assistant.sendspin.noise.HandshakeFailedException
import io.music_assistant.sendspin.noise.NoiseFraming
import io.music_assistant.sendspin.noise.PskCategory
import io.music_assistant.sendspin.noise.SendspinPsk
import io.music_assistant.sendspin.noise.UnsupportedCoreVersionException
import io.music_assistant.sendspin.wire.SendspinJson
import io.music_assistant.sendspin.wire.ServerMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertIsNot
import kotlin.test.assertTrue

/**
 * Establishment, activation gating and the spec's ordered rejection rules,
 * audio byte-exactness (plain and fragmented), in-band re-handshake, and the
 * Pairing PSK flow, on both wires. Each wire's own shapes and rules follow in
 * [LegacyNoiseSessionTest] and [SpecNoiseSessionTest].
 */
internal abstract class NoiseSessionScenarios(wire: WireVersion) : NoiseSessionTestHarness(wire) {
    /** A Sentinel session past hello and activation, its two events consumed. */
    protected suspend fun activated(f: Fixture): FakeNoiseServer {
        val server = FakeServer(f, SendspinPsk.SENTINEL_PSK)
        server.establish()
        server.completeHelloExchange()
        server.activate()
        assertIs<Event.Ready>(f.nextEvent())
        assertIs<Event.Activated>(f.nextEvent())
        return server
    }

    protected fun payloadOf(json: String): JsonObject =
        SendspinJson.parseToJsonElement(json).jsonObject.getValue("payload").jsonObject

    protected fun goodbyeReason(json: String): String = payloadOf(json).getValue("reason").jsonPrimitive.content

    @Test
    fun establishesActivatesAndGatesOutboundTraffic() = sessionTest { f ->
        val server = FakeServer(f, SendspinPsk.SENTINEL_PSK)
        server.establish()
        val clientHello = server.completeHelloExchange()

        val payload = payloadOf(clientHello)
        assertFalse(payload.containsKey("client_id"))
        assertFalse(payload.containsKey("version"))
        assertEquals("Enc Device", payload.getValue("name").jsonPrimitive.content)
        assertTrue(payload.getValue("unpaired_access").jsonObject.getValue("enabled").jsonPrimitive.content.toBoolean())

        val ready = assertIs<Event.Ready>(f.nextEvent()).info
        assertEquals(f.serverId, ready.serverId)
        assertEquals("Enc Server", ready.serverName)
        assertEquals(TrustLevel.NONE, ready.trustLevel)
        assertEquals(wire, ready.wire)

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
        server.sendJson("""{"type":"stream/end","payload":{}}""")

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
    fun rehandshakeSwapsKeysAndResumesAfterActivate() = sessionTest { f ->
        val server = FakeServer(f, SendspinPsk.SENTINEL_PSK)
        server.establish()
        server.completeHelloExchange()
        server.activate()
        assertIs<Event.Ready>(f.nextEvent())
        assertIs<Event.Activated>(f.nextEvent())

        server.rehandshake(f.trustStore.pairingPsk, PAIRING_CATEGORY)
        // A send issued during the exchange must not interleave: it flushes
        // only after the post-re-handshake activation.
        val queuedSend = launch { f.session.send("""{"type":"client/time"}""") }
        helloAfterRehandshake(server, f)

        server.activate(activities = """["pairing"]""", activeRoles = "[]", pairing = """{"method":"pairing_psk"}""")
        assertIs<Event.Activated>(f.nextEvent())

        // Quiesced through the pairing activity: only the pairing messages cross.
        val newPsk = server.receivePairFinalize()
        server.sendJson("""{"type":"server/pair-finalize","payload":{}}""")

        server.rehandshake(newPsk)
        helloAfterRehandshake(server, f)
        server.activate()
        assertIs<Event.Activated>(f.nextEvent())
        queuedSend.join()
        assertEquals("""{"type":"client/time"}""", server.receiveJson())
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
        server.receivePairFinalize() // the first attempt

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

        val newPsk = server.receivePairFinalize()
        assertEquals(32, newPsk.size)
        assertTrue(f.trustStore.records().none { it.serverId == f.serverId }, "nothing persisted before the ack")

        server.sendJson("""{"type":"server/pair-finalize","payload":{}}""")
        withTimeout(AWAIT_MILLIS) {
            while (f.trustStore.records().none { it.serverId == f.serverId }) delay(10)
        }
        assertContentEquals(newPsk, f.trustStore.records().single { it.serverId == f.serverId }.psk)

        server.rehandshake(newPsk)
        when (wire) {
            WireVersion.LEGACY_9 -> {
                val clientHello = server.completeHelloExchange()
                assertEquals("user", payloadOf(clientHello).getValue("trust_level").jsonPrimitive.content)
                assertEquals(TrustLevel.USER, assertIs<Event.Ready>(f.nextEvent()).info.trustLevel)
            }

            WireVersion.SPEC_1_0 -> {
                server.activate()
                assertIs<Event.Activated>(f.nextEvent())
            }
        }
    }

    @Test
    fun cancellingActivateDiscardsAttemptAndAdmitsANewOne() = sessionTest { f ->
        val server = pairingPreamble(f)
        val firstPsk = server.receivePairFinalize(expectedPairingIndex = 1)

        server.activate(activities = """["pairing"]""", activeRoles = "[]", pairing = """{"method":"pairing_psk"}""")
        assertIs<Event.Activated>(f.nextEvent())
        val secondPsk = server.receivePairFinalize(expectedPairingIndex = 2)

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
        server.receivePairFinalize()

        server.sendJson("""{"type":"pair/abort","payload":{"reason":"user_cancelled"}}""")
        server.sendJson("""{"type":"server/pair-finalize","payload":{}}""")
        server.sendJson("""{"type":"server/state","payload":{}}""")
        assertIs<ServerMessage.State>(f.nextMessage())
        assertTrue(f.trustStore.records().none { it.serverId == f.serverId })
    }

    @Test
    fun pairingAttemptTimeoutSendsPairAbort() = sessionTest(pairingAttemptTimeoutMillis = 150) { f ->
        val server = pairingPreamble(f)
        server.receivePairFinalize()

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

/** The 9.x wire: `trust_level`, the pair-method array, hello repeated after a re-handshake. */
internal class LegacyNoiseSessionTest : NoiseSessionScenarios(WireVersion.LEGACY_9) {
    @Test
    fun helloCarriesTrustLevelPairMethodArrayAndSupportedCommands() = sessionTest { f ->
        val server = FakeServer(f, SendspinPsk.SENTINEL_PSK)
        server.establish()
        val payload = payloadOf(server.completeHelloExchange())

        assertEquals("none", payload.getValue("trust_level").jsonPrimitive.content)
        assertEquals(
            "pairing_psk",
            payload.getValue("supported_pair_methods").jsonArray[0].jsonObject.getValue("method").jsonPrimitive.content,
        )
        assertEquals(0, payload.getValue("player@v1_support").jsonObject.getValue("supported_commands").jsonArray.size)
    }

    @Test
    fun rehandshakeRepeatsHelloWithTheNewTrustLevel() = sessionTest { f ->
        val server = FakeServer(f, SendspinPsk.SENTINEL_PSK)
        server.establish()
        server.completeHelloExchange()
        assertIs<Event.Ready>(f.nextEvent())

        server.rehandshake(f.trustStore.pairingPsk, PAIRING_CATEGORY)
        val payload = payloadOf(server.completeHelloExchange())
        assertEquals("none", payload.getValue("trust_level").jsonPrimitive.content)
        assertEquals(PskCategory.PAIRING, assertIs<Event.Ready>(f.nextEvent()).info.matchedPskCategory)
    }

    @Test
    fun malformedMessagesAreIgnored() = sessionTest { f ->
        val server = activated(f)
        server.sendRawFrame(4, ByteArray(4))
        server.sendJson("""{"type":"stream/end"}""")
        assertIs<ServerMessage.StreamEnd>(f.nextMessage())
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
}

/** Spec 1.0.0-rc1: the new hello and pairing shapes, category matching, and the re-handshake without hello. */
internal class SpecNoiseSessionTest : NoiseSessionScenarios(WireVersion.SPEC_1_0) {
    @Test
    fun helloOmitsTrustLevelAndSupportedCommandsAndKeysPairMethodsByName() = sessionTest { f ->
        val server = FakeServer(f, SendspinPsk.SENTINEL_PSK)
        server.establish()
        val clientHello = server.completeHelloExchange()
        val payload = payloadOf(clientHello)

        assertFalse(payload.containsKey("trust_level"))
        assertFalse(payload.getValue("player@v1_support").jsonObject.containsKey("supported_commands"))
        assertEquals(0, payload.getValue("supported_pair_methods").jsonObject.getValue("pairing_psk").jsonObject.size)
        assertFalse(clientHello.contains("null"), "a null optional field is rejected by the server: $clientHello")
    }

    @Test
    fun helloCapsTheNameAt64Utf8BytesOnACodePointBoundary() = sessionTest(
        deviceName = "Кухонний програвач 🎵".repeat(3),
    ) { f ->
        val server = FakeServer(f, SendspinPsk.SENTINEL_PSK)
        server.establish()
        val name = payloadOf(server.completeHelloExchange()).getValue("name").jsonPrimitive.content
        assertTrue(name.encodeToByteArray().size <= 64, "${name.encodeToByteArray().size} bytes")
        assertTrue("Кухонний програвач 🎵".repeat(3).startsWith(name))
        assertTrue(name.encodeToByteArray().size > 60, "no more than one code point dropped")
    }

    @Test
    fun aJsonMessageWithoutAPayloadObjectClosesTheConnection() = sessionTest { f ->
        val server = activated(f)
        server.sendJson("""{"type":"stream/end"}""")
        assertIs<NoiseFraming.ProtocolException>(f.awaitFailure())
        assertTrue(f.transport.closed)
    }

    @Test
    fun aJsonMessageThatIsNotUtf8ClosesTheConnection() = sessionTest { f ->
        val server = activated(f)
        server.sendRawFrame(NoiseFraming.TYPE_JSON, byteArrayOf(0xC3.toByte(), 0x28))
        assertIs<NoiseFraming.ProtocolException>(f.awaitFailure())
    }

    @Test
    fun anAudioMessageShorterThan13BytesClosesTheConnection() = sessionTest { f ->
        val server = activated(f)
        server.sendRawFrame(4, ByteArray(11))
        assertIs<NoiseFraming.ProtocolException>(f.awaitFailure())
    }

    @Test
    fun aPayloadThatDoesNotFitTheModelIsNotAProtocolError() = sessionTest { f ->
        val server = activated(f)
        server.sendJson("""{"type":"server/time","payload":{}}""")
        server.sendJson("""{"type":"stream/end","payload":{}}""")
        assertIs<ServerMessage.StreamEnd>(f.nextMessage())
    }

    @Test
    fun unknownPskIdInTheInitialHandshakeCompletesWithTheSentinel() = sessionTest { f ->
        val server = FakeServer(f, ByteArray(32) { 0x55 })
        server.establish(verifyWith = SendspinPsk.SENTINEL_PSK)
        server.completeHelloExchange()
        assertEquals(PskCategory.SENTINEL, assertIs<Event.Ready>(f.nextEvent()).info.matchedPskCategory)
    }

    @Test
    fun aPskIdHeldUnderAnotherCategoryIsAMiss() = sessionTest { f ->
        val server = FakeServer(f, f.trustStore.pairingPsk)
        server.establish(categoryCode = "lt", verifyWith = SendspinPsk.SENTINEL_PSK)
        server.completeHelloExchange()
        assertEquals(PskCategory.SENTINEL, assertIs<Event.Ready>(f.nextEvent()).info.matchedPskCategory)
    }

    @Test
    fun unknownPskIdDuringARehandshakeFails() = sessionTest { f ->
        val server = FakeServer(f, SendspinPsk.SENTINEL_PSK)
        server.establish()
        server.completeHelloExchange()
        assertIs<Event.Ready>(f.nextEvent())

        launch { runCatching { server.rehandshake(ByteArray(32) { 0x55 }) } }
        assertIs<HandshakeFailedException>(f.awaitFailure())
        assertTrue(f.transport.closed)
    }

    @Test
    fun rehandshakeSendsNoHelloAndAnActivateWithoutRolesKeepsThem() = sessionTest { f ->
        val server = FakeServer(f, SendspinPsk.SENTINEL_PSK)
        server.establish()
        server.completeHelloExchange()
        server.activate()
        assertIs<Event.Ready>(f.nextEvent())
        assertIs<Event.Activated>(f.nextEvent())

        val longTermPsk = ByteArray(32) { 0x21 }
        f.trustStore.recordLongTermPsk(longTermPsk, f.serverId)
        server.rehandshake(longTermPsk)
        server.activate(activities = """["playback"]""", activeRoles = null)
        val activated = assertIs<Event.Activated>(f.nextEvent()).activation
        assertEquals(listOf("player@v1"), activated.activeRoles)
    }

    @Test
    fun pairInitPrecedesFinalizeAndItsIndexCountsPerHandshake() = sessionTest { f ->
        val server = pairingPreamble(f)
        server.receivePairFinalize(expectedPairingIndex = 1)

        server.activate(activities = """["pairing"]""", activeRoles = "[]", pairing = """{"method":"pairing_psk"}""")
        assertIs<Event.Activated>(f.nextEvent())
        server.receivePairFinalize(expectedPairingIndex = 2)

        server.rehandshake(f.trustStore.pairingPsk, PAIRING_CATEGORY)
        server.activate(activities = """["pairing"]""", activeRoles = "[]", pairing = """{"method":"pairing_psk"}""")
        assertIs<Event.Activated>(f.nextEvent())
        server.receivePairFinalize(expectedPairingIndex = 1)
    }

    @Test
    fun aPairingKeyedSessionAdmitsEmptyActivities() = sessionTest { f ->
        val server = FakeServer(f, SendspinPsk.SENTINEL_PSK)
        server.establish()
        server.completeHelloExchange()
        assertIs<Event.Ready>(f.nextEvent())

        server.rehandshake(f.trustStore.pairingPsk, PAIRING_CATEGORY)
        server.activate(activities = "[]", activeRoles = "[]")
        assertEquals(emptyList(), assertIs<Event.Activated>(f.nextEvent()).activation.activities)
        assertFalse(f.transport.closed)
    }

    @Test
    fun legacyFragmentTypesAreIgnoredAsUnknownMessages() = sessionTest { f ->
        val server = FakeServer(f, SendspinPsk.SENTINEL_PSK)
        server.establish()
        server.completeHelloExchange()
        server.activate()
        assertIs<Event.Ready>(f.nextEvent())
        assertIs<Event.Activated>(f.nextEvent())

        server.sendRawFrame(NoiseFraming.TYPE_FRAGMENT_MORE, byteArrayOf(0, 1, 2))
        server.sendRawFrame(NoiseFraming.TYPE_FRAGMENT_END, byteArrayOf(9))
        server.sendJson("""{"type":"server/state","payload":{}}""")
        assertIs<ServerMessage.State>(f.nextMessage())
        assertFalse(f.transport.closed)
    }

    @Test
    fun serverErrorForAnUnsupportedVersionIsTerminal() = sessionTest { f ->
        FakeServer(f, SendspinPsk.SENTINEL_PSK).failInit("unsupported_version")
        assertIs<UnsupportedCoreVersionException>(f.awaitFailure())
        assertTrue(f.transport.closed)
    }

    @Test
    fun serverErrorMalformedIsARetriableFailure() = sessionTest { f ->
        FakeServer(f, SendspinPsk.SENTINEL_PSK).failInit("malformed")
        val failure = f.awaitFailure()
        assertIs<HandshakeFailedException>(failure)
        assertIsNot<UnsupportedCoreVersionException>(failure)
    }
}
