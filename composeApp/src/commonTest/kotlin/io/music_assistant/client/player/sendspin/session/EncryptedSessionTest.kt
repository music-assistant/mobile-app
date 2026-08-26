package io.music_assistant.client.player.sendspin.session

import io.music_assistant.client.player.sendspin.noise.NoiseFraming
import io.music_assistant.client.player.sendspin.noise.PskCategory
import io.music_assistant.client.player.sendspin.noise.SendspinBase64
import io.music_assistant.client.player.sendspin.noise.SendspinPsk
import io.music_assistant.client.player.sendspin.transport.InboundTransportEvent
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Drives [EncryptedSession] end to end against an in-test Noise-initiator
 * server over [FakeSendspinTransport]: establishment, hello shapes,
 * activation gating and the spec's ordered rejection rules, audio
 * byte-exactness (plain and fragmented), in-band re-handshake, silent
 * handshake failure, reconnect-epoch ordering, and the Pairing PSK flow.
 */
internal class EncryptedSessionTest : EncryptedSessionTestHarness() {
    @Test
    fun establishesActivatesAndGatesOutboundTraffic() = runRealTime {
        val f = fixture(this)
        val server = FakeServer(f, SendspinPsk.SENTINEL_PSK)
        f.session.start()
        assertEquals(1, f.transport.connectCount)

        server.establish()
        val clientHelloText = server.completeHelloExchange()

        // Encrypted client/hello shape: no client_id / version, trust fields present.
        val helloPayload = json.parseToJsonElement(clientHelloText)
            .jsonObject.getValue("payload").jsonObject
        assertFalse(helloPayload.containsKey("client_id"))
        assertFalse(helloPayload.containsKey("version"))
        assertEquals("none", helloPayload.getValue("trust_level").jsonPrimitive.content)
        assertEquals(
            "pairing_psk",
            helloPayload.getValue("supported_pair_methods").jsonArray[0]
                .jsonObject.getValue("method").jsonPrimitive.content,
        )
        assertTrue(
            helloPayload.getValue("unpaired_access").jsonObject
                .getValue("enabled").jsonPrimitive.content.toBoolean(),
        )

        val ready = assertIs<SessionEvent.ProtocolReady>(f.nextEventSkippingNegotiation())
        assertEquals(f.serverId, ready.serverId)
        assertEquals("Enc Server", ready.serverName)
        assertEquals(TrustLevel.NONE, ready.trustLevel)
        assertFalse(ready.isReconnectEpoch)

        // Outbound traffic is gated until the first admissible activation.
        val gatedSend = launch { f.session.sender.sendJson("""{"type":"client/time"}""") }
        server.activate()
        val activated = assertIs<SessionEvent.Activated>(f.nextEventSkippingNegotiation())
        assertEquals(listOf("playback"), activated.activities)
        assertEquals(listOf("player@v1"), activated.activeRoles)
        gatedSend.join()
        assertEquals("""{"type":"client/time"}""", server.receiveJson())
    }

    @Test
    fun backToBackMessagesAfterActivationKeepSourceOrder() = runRealTime {
        val f = fixture(this)
        val server = FakeServer(f, SendspinPsk.SENTINEL_PSK)
        f.session.start()
        server.establish()
        server.completeHelloExchange()
        // Activation, application JSON, and audio emitted back-to-back.
        server.activate()
        server.sendJson("""{"type":"server/state","payload":{}}""")
        server.sendAudio(4, byteArrayOf(9, 9, 9))
        server.sendJson("""{"type":"stream/end"}""")

        assertIs<SessionEvent.ProtocolReady>(f.nextEventSkippingNegotiation())
        assertIs<SessionEvent.Activated>(f.nextEvent())

        val app = f.session.applicationMessages.produceIn(this)
        assertEquals("""{"type":"server/state","payload":{}}""", withTimeout(5_000) { app.receive() })
        assertEquals("""{"type":"stream/end"}""", withTimeout(5_000) { app.receive() })

        val audio = f.session.audioFrames.produceIn(this)
        assertContentEquals(byteArrayOf(4, 9, 9, 9), withTimeout(5_000) { audio.receive() })
    }

    @Test
    fun audioFramesAreByteExactIncludingFragmentedReassembly() = runRealTime {
        val f = fixture(this)
        val server = FakeServer(f, SendspinPsk.SENTINEL_PSK)
        f.session.start()
        server.establish()
        server.completeHelloExchange()
        server.activate()
        assertIs<SessionEvent.ProtocolReady>(f.nextEventSkippingNegotiation())
        assertIs<SessionEvent.Activated>(f.nextEvent())

        val audio = f.session.audioFrames.produceIn(this)

        val small = ByteArray(100) { it.toByte() }
        server.sendAudio(4, small)
        assertContentEquals(byteArrayOf(4) + small, withTimeout(5_000) { audio.receive() })

        // Larger than one Noise frame: fragmented on the wire, reassembled to
        // the exact original frame bytes (type byte + payload).
        val large = ByteArray(NoiseFraming.MAX_UNFRAGMENTED_PAYLOAD + 5_000) { (it % 251).toByte() }
        server.sendAudio(4, large)
        assertContentEquals(byteArrayOf(4) + large, withTimeout(5_000) { audio.receive() })
    }

    @Test
    fun rehandshakeSwapsKeysRepeatsHelloAndResumesAfterActivate() = runRealTime {
        val f = fixture(this)
        val server = FakeServer(f, SendspinPsk.SENTINEL_PSK)
        f.session.start()
        server.establish()
        server.completeHelloExchange()
        server.activate()
        assertIs<SessionEvent.ProtocolReady>(f.nextEventSkippingNegotiation())
        assertIs<SessionEvent.Activated>(f.nextEvent())

        // Server re-handshakes to the device's Pairing PSK.
        server.rehandshake(f.trustStore.pairingPsk)
        assertIs<SessionEvent.RehandshakeCompleted>(f.nextEvent())

        // A send issued during the exchange must not interleave: it flushes
        // only after the post-re-handshake activation.
        val queuedSend = launch { f.session.sender.sendJson("""{"type":"client/time"}""") }

        val clientHello = server.completeHelloExchange()
        assertEquals(
            "none",
            json.parseToJsonElement(clientHello).jsonObject.getValue("payload")
                .jsonObject.getValue("trust_level").jsonPrimitive.content,
        )
        val ready = assertIs<SessionEvent.ProtocolReady>(f.nextEvent())
        assertEquals(PskCategory.PAIRING, ready.matchedPskCategory)

        server.activate(activities = """["pairing"]""", activeRoles = "[]", pairing = """{"method":"pairing_psk"}""")
        assertIs<SessionEvent.Activated>(f.nextEvent())

        // Application traffic stays quiesced through the whole pairing
        // activity: only the attempt's own client/pair-finalize crosses.
        // A non-pairing message here would land in the server's pairing
        // exchange and abort it.
        val finalize = json.parseToJsonElement(server.receiveJson()).jsonObject
        assertEquals("client/pair-finalize", finalize.getValue("type").jsonPrimitive.content)
        val newPsk = SendspinBase64.decode(
            finalize.getValue("payload").jsonObject.getValue("long_term_psk").jsonPrimitive.content,
        )
        server.sendJson("""{"type":"server/pair-finalize","payload":{}}""")

        // Pairing completed: the server re-handshakes to the new record and
        // reactivates playback; only then does the queued send flush.
        server.rehandshake(newPsk)
        assertIs<SessionEvent.RehandshakeCompleted>(f.nextEvent())
        server.completeHelloExchange()
        assertIs<SessionEvent.ProtocolReady>(f.nextEvent())
        server.activate()
        assertIs<SessionEvent.Activated>(f.nextEvent())
        queuedSend.join()
        assertEquals("""{"type":"client/time"}""", server.receiveJson())
    }

    @Test
    fun unknownPskIdFailsSilentlyWithoutApplicationError() = runRealTime {
        val f = fixture(this)
        val server = FakeServer(f, ByteArray(32) { 0x55 })
        f.session.start()
        launch { runCatching { server.establish() } }

        val failed = assertIs<SessionEvent.Failed>(f.nextEventSkippingNegotiation())
        assertFalse(failed.permanent)
        assertEquals(1, f.transport.disconnectCount, "socket closed on handshake failure")
        // No application-level error message was sent: the only outbound
        // frame remains client/init.
        assertEquals(1, f.transport.sentTexts.size)
        assertTrue(f.transport.sentBinaries.isEmpty())
    }

    @Test
    fun reconnectEpochRerunsFullHandshakeAndDropsStaleFrames() = runRealTime {
        val f = fixture(this)
        val server = FakeServer(f, SendspinPsk.SENTINEL_PSK)
        f.session.start()
        server.establish()
        server.completeHelloExchange()
        server.activate()
        assertIs<SessionEvent.ProtocolReady>(f.nextEventSkippingNegotiation())
        assertIs<SessionEvent.Activated>(f.nextEvent())
        val staleNoise = server.noise

        // Transport reconnects: new epoch announced before its first frame.
        f.transport.emit(InboundTransportEvent.Reconnecting(1, attempt = 1))
        f.transport.emit(InboundTransportEvent.Connected(2, isReconnect = true))
        // A stale epoch-1 frame arriving after the new epoch's Connected.
        f.transport.emit(
            InboundTransportEvent.Binary(1, staleNoise.encrypt(byteArrayOf(4, 1))),
        )

        assertIs<SessionEvent.Reconnecting>(f.nextEvent())

        val server2 = FakeServer(f, SendspinPsk.SENTINEL_PSK)
        server2.establish(epoch = 2)
        server2.completeHelloExchange(epoch = 2)
        server2.activate(epoch = 2)

        val ready = assertIs<SessionEvent.ProtocolReady>(f.nextEventSkippingNegotiation())
        assertTrue(ready.isReconnectEpoch)
        val activated = assertIs<SessionEvent.Activated>(f.nextEvent())
        assertTrue(activated.isReconnectEpoch)
    }

    @Test
    fun sentinelPlaybackWithoutUnpairedAccessClosesWithPairingRequired() = runRealTime {
        val f = fixture(this, unpairedAccess = false)
        val server = FakeServer(f, SendspinPsk.SENTINEL_PSK)
        f.session.start()
        server.establish()
        server.completeHelloExchange()
        assertIs<SessionEvent.ProtocolReady>(f.nextEventSkippingNegotiation())

        server.activate(activities = """["playback"]""", activeRoles = """["player@v1"]""")
        val goodbye = server.receiveJson()
        assertEquals(
            "pairing_required",
            json.parseToJsonElement(goodbye).jsonObject.getValue("payload")
                .jsonObject.getValue("reason").jsonPrimitive.content,
        )
        assertIs<SessionEvent.Failed>(f.nextEvent())
        assertEquals(1, f.transport.disconnectCount)
    }

    @Test
    fun disallowedActivitySetClosesWithUnauthorized() = runRealTime {
        val f = fixture(this, unpairedAccess = false)
        val server = FakeServer(f, SendspinPsk.SENTINEL_PSK)
        f.session.start()
        server.establish()
        server.completeHelloExchange()
        assertIs<SessionEvent.ProtocolReady>(f.nextEventSkippingNegotiation())

        // No unpaired-access setting makes {playback, management} allowed on
        // the sentinel PSK.
        server.activate(activities = """["playback","management"]""", activeRoles = "[]")
        val goodbye = server.receiveJson()
        assertEquals(
            "unauthorized",
            json.parseToJsonElement(goodbye).jsonObject.getValue("payload")
                .jsonObject.getValue("reason").jsonPrimitive.content,
        )
        assertIs<SessionEvent.Failed>(f.nextEvent())
    }

    @Test
    fun unsupportedPairingMethodRepliesPairAbortAndStaysOpen() = runRealTime {
        val f = fixture(this)
        val server = FakeServer(f, SendspinPsk.SENTINEL_PSK)
        f.session.start()
        server.establish()
        server.completeHelloExchange()
        assertIs<SessionEvent.ProtocolReady>(f.nextEventSkippingNegotiation())

        server.activate(
            activities = """["pairing"]""",
            activeRoles = "[]",
            pairing = """{"method":"static_pin"}""",
        )
        val abort = server.receiveJson()
        val parsed = json.parseToJsonElement(abort).jsonObject
        assertEquals("pair/abort", parsed.getValue("type").jsonPrimitive.content)
        assertEquals(
            "method_not_supported",
            parsed.getValue("payload").jsonObject.getValue("reason").jsonPrimitive.content,
        )
        assertEquals(0, f.transport.disconnectCount, "connection stays open")

        // A subsequent admissible activation still succeeds.
        server.activate(activities = "[]", activeRoles = "[]")
        assertIs<SessionEvent.Activated>(f.nextEvent())
    }

    @Test
    fun sourceRoleAtNoneTrustClosesWithUnauthorized() = runRealTime {
        val f = fixture(this)
        val server = FakeServer(f, SendspinPsk.SENTINEL_PSK)
        f.session.start()
        server.establish()
        server.completeHelloExchange()
        assertIs<SessionEvent.ProtocolReady>(f.nextEventSkippingNegotiation())

        // Unpaired access admits playback on the sentinel PSK, but the source
        // role is never grantable at trust level none.
        server.activate(activities = """["playback"]""", activeRoles = """["source@v1"]""")
        val goodbye = server.receiveJson()
        assertEquals(
            "unauthorized",
            json.parseToJsonElement(goodbye).jsonObject.getValue("payload")
                .jsonObject.getValue("reason").jsonPrimitive.content,
        )
        assertIs<SessionEvent.Failed>(f.nextEvent())
    }

    @Test
    fun roleViolationIsUnauthorizedNotPairingRequired() = runRealTime {
        val f = fixture(this, unpairedAccess = false)
        val server = FakeServer(f, SendspinPsk.SENTINEL_PSK)
        f.session.start()
        server.establish()
        server.completeHelloExchange()
        assertIs<SessionEvent.ProtocolReady>(f.nextEventSkippingNegotiation())

        // Rejection rules are ordered: pairing_required applies only when
        // enabling unpaired access would have made the activation admissible.
        // A role violation would remain inadmissible, so the answer is the
        // second rule's unauthorized, not pairing_required.
        server.activate(activities = """["playback"]""", activeRoles = """["source@v1"]""")
        val goodbye = server.receiveJson()
        assertEquals(
            "unauthorized",
            json.parseToJsonElement(goodbye).jsonObject.getValue("payload")
                .jsonObject.getValue("reason").jsonPrimitive.content,
        )
        assertIs<SessionEvent.Failed>(f.nextEvent())
    }

    @Test
    fun rejectedPairingActivationEndsThePriorAttemptSoALateFinalizePersistsNothing() = runRealTime {
        val f = fixture(this)
        val server = pairingPreamble(f)
        server.receiveJson() // the first attempt's client/pair-finalize

        // A second pairing activation with an unsupported method is answered
        // with pair/abort (connection open) — and, like every received
        // server/activate, it ends the prior attempt.
        server.activate(
            activities = """["pairing"]""",
            activeRoles = "[]",
            pairing = """{"method":"static_pin"}""",
        )
        val abort = json.parseToJsonElement(server.receiveJson()).jsonObject
        assertEquals("pair/abort", abort.getValue("type").jsonPrimitive.content)
        assertEquals(0, f.transport.disconnectCount, "connection stays open")

        // A finalize aimed at the superseded attempt persists nothing.
        server.sendJson("""{"type":"server/pair-finalize","payload":{}}""")
        server.sendJson("""{"type":"server/state","payload":{}}""")
        val app = f.session.applicationMessages.produceIn(this)
        assertEquals(
            """{"type":"server/state","payload":{}}""",
            withTimeout(5_000) { app.receive() },
        )
        assertTrue(f.trustStore.records().none { it.serverId == f.serverId })
    }

    @Test
    fun pairingPskFlowPersistsRecordOnlyAfterServerFinalizeThenRehandshakesToIt() = runRealTime {
        val f = fixture(this)
        val server = pairingPreamble(f)

        // The client starts the attempt with client/pair-finalize.
        val finalize = json.parseToJsonElement(server.receiveJson()).jsonObject
        assertEquals("client/pair-finalize", finalize.getValue("type").jsonPrimitive.content)
        val newPsk = SendspinBase64.decode(
            finalize.getValue("payload").jsonObject.getValue("long_term_psk").jsonPrimitive.content,
        )
        assertEquals(32, newPsk.size)
        assertTrue(
            f.trustStore.records().none { it.serverId == f.serverId },
            "nothing persisted before the server acknowledges",
        )

        server.sendJson("""{"type":"server/pair-finalize","payload":{}}""")
        // The record lands and the server re-handshakes to the new PSK.
        withTimeout(5_000) {
            while (f.trustStore.records().none { it.serverId == f.serverId }) {
                delay(10)
            }
        }
        assertContentEquals(
            newPsk,
            f.trustStore.records().single { it.serverId == f.serverId }.psk,
        )

        server.rehandshake(newPsk)
        assertIs<SessionEvent.RehandshakeCompleted>(f.nextEvent())
        val clientHello = server.completeHelloExchange()
        assertEquals(
            "user",
            json.parseToJsonElement(clientHello).jsonObject.getValue("payload")
                .jsonObject.getValue("trust_level").jsonPrimitive.content,
            "the client asserts user trust once the long-term record matched",
        )
        val ready = assertIs<SessionEvent.ProtocolReady>(f.nextEvent())
        assertEquals(TrustLevel.USER, ready.trustLevel)
    }

    @Test
    fun cancellingActivateDiscardsAttemptAndAdmitsANewOne() = runRealTime {
        val f = fixture(this)
        val server = pairingPreamble(f)
        val firstPsk = SendspinBase64.decode(
            json.parseToJsonElement(server.receiveJson()).jsonObject
                .getValue("payload").jsonObject.getValue("long_term_psk").jsonPrimitive.content,
        )

        // A second pairing activation cancels the first attempt (its PSK is
        // discarded, never persisted) and admits a fresh one.
        server.activate(
            activities = """["pairing"]""",
            activeRoles = "[]",
            pairing = """{"method":"pairing_psk"}""",
        )
        assertIs<SessionEvent.Activated>(f.nextEvent())
        val secondPsk = SendspinBase64.decode(
            json.parseToJsonElement(server.receiveJson()).jsonObject
                .getValue("payload").jsonObject.getValue("long_term_psk").jsonPrimitive.content,
        )

        server.sendJson("""{"type":"server/pair-finalize","payload":{}}""")
        withTimeout(5_000) {
            while (f.trustStore.records().none { it.serverId == f.serverId }) {
                delay(10)
            }
        }
        val record = f.trustStore.records().single { it.serverId == f.serverId }
        assertContentEquals(secondPsk, record.psk, "only the live attempt's PSK is persisted")
        assertFalse(record.psk.contentEquals(firstPsk), "the cancelled attempt's PSK is gone")
    }

    @Test
    fun pairAbortDiscardsAttemptPersistingNothing() = runRealTime {
        val f = fixture(this)
        val server = pairingPreamble(f)
        server.receiveJson() // client/pair-finalize

        server.sendJson("""{"type":"pair/abort","payload":{"reason":"user_cancelled"}}""")
        // A late finalize after the aborted attempt persists nothing.
        server.sendJson("""{"type":"server/pair-finalize","payload":{}}""")
        server.sendJson("""{"type":"server/state","payload":{}}""")
        val app = f.session.applicationMessages.produceIn(this)
        assertEquals("""{"type":"server/state","payload":{}}""", withTimeout(5_000) { app.receive() })
        assertTrue(f.trustStore.records().none { it.serverId == f.serverId })
    }

    @Test
    fun pairingAttemptTimeoutSendsPairAbort() = runRealTime {
        val f = fixture(this, pairingAttemptTimeoutMillis = 150)
        val server = pairingPreamble(f)
        server.receiveJson() // client/pair-finalize

        // No server response: the attempt times out and aborts.
        val abort = json.parseToJsonElement(server.receiveJson()).jsonObject
        assertEquals("pair/abort", abort.getValue("type").jsonPrimitive.content)
        assertEquals(
            "attempt_timeout",
            abort.getValue("payload").jsonObject.getValue("reason").jsonPrimitive.content,
        )
        assertTrue(f.trustStore.records().none { it.serverId == f.serverId })
    }

    @Test
    fun proxyAuthPreExchangeRunsBeforeClientInit() = runRealTime {
        val f = fixture(this, requiresAuth = true)
        val server = FakeServer(f, SendspinPsk.SENTINEL_PSK)
        f.session.start()

        assertEquals("""{"type":"auth","token":"tok"}""", withTimeout(5_000) { f.transport.textOut.receive() })
        f.transport.emit(InboundTransportEvent.Text(1, """{"type":"auth_ok"}"""))
        server.establish()
        server.completeHelloExchange()
        assertIs<SessionEvent.ProtocolReady>(f.nextEventSkippingNegotiation())
    }
}
