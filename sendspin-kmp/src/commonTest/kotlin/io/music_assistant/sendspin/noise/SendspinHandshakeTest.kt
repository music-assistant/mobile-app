package io.music_assistant.sendspin.noise

import io.music_assistant.sendspin.api.WireVersion
import io.music_assistant.sendspin.noise.crypto.CryptographyKotlinNoiseCrypto
import io.music_assistant.sendspin.noise.crypto.X25519KeyPair
import io.music_assistant.sendspin.wire.NoiseHandshakeMessage
import io.music_assistant.sendspin.wire.NoiseHandshakePayload
import io.music_assistant.sendspin.wire.ServerInitMessage
import io.music_assistant.sendspin.wire.ServerInitPayload
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIsNot

class SendspinHandshakeTest {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private class PipeIo : HandshakeIo {
        val clientToServer = Channel<String>(Channel.UNLIMITED)
        val serverToClient = Channel<HandshakeFrame>(Channel.UNLIMITED)

        override suspend fun sendText(text: String) {
            clientToServer.send(text)
        }

        override suspend fun receive(): HandshakeFrame = serverToClient.receive()
    }

    /**
     * A minimal in-test Sendspin server: Noise initiator over the piped
     * frames, with overridable behavior to exercise each failure branch.
     */
    private inner class FakeServer(
        val crypto: CryptographyKotlinNoiseCrypto,
        val serverStatic: X25519KeyPair,
        val clientStaticPublic: ByteArray,
        val psk: ByteArray,
    ) {
        val serverId: String get() = SendspinBase64.encode(serverStatic.publicKey)
        var receivedClientInit: String? = null
        var transport: NoiseTransport? = null

        /** Reads `client/init` and answers `server/error` instead of `server/init`. */
        suspend fun failInit(io: PipeIo, reason: String) {
            receivedClientInit = io.clientToServer.receive()
            io.serverToClient.send(HandshakeFrame.Text("""{"type":"server/error","payload":{"reason":"$reason"}}"""))
        }

        suspend fun run(
            io: PipeIo,
            serverInitVersion: Int = SENDSPIN_CORE_VERSION,
            innerPayloadOverride: String? = null,
            /** `psk_category` for message 1; null leaves it out, as a 9.x server does. */
            categoryCode: String? = null,
            /** The PSK message 2 is verified with; a spec server retries with the Sentinel. */
            verifyWith: ByteArray = psk,
        ) {
            val clientInitText = io.clientToServer.receive()
            receivedClientInit = clientInitText
            val serverInitText = json.encodeToString(
                ServerInitMessage(payload = ServerInitPayload(serverId, serverInitVersion)),
            )
            io.serverToClient.send(HandshakeFrame.Text(serverInitText))

            val prologue =
                clientInitText.encodeToByteArray() + serverInitText.encodeToByteArray()
            val initiator = HandshakeState.initialize(
                crypto = crypto,
                pattern = NoisePattern.KKPSK2,
                role = NoiseRole.INITIATOR,
                prologue = prologue,
                localStatic = serverStatic,
                remoteStaticPublic = clientStaticPublic,
                psk = null,
            )
            val pskId = SendspinPsk.pskId(crypto, psk)
            val innerPayload = innerPayloadOverride
                ?: categoryCode?.let { """{"psk_id":"$pskId","psk_category":"$it"}""" }
                ?: """{"psk_id":"$pskId"}"""
            val message1 = initiator.writeMessage(innerPayload.encodeToByteArray())
            io.serverToClient.send(
                HandshakeFrame.Text(
                    json.encodeToString(
                        NoiseHandshakeMessage(
                            payload = NoiseHandshakePayload(SendspinBase64.encode(message1)),
                        ),
                    ),
                ),
            )

            val message2Text = io.clientToServer.receive()
            val message2 = json.decodeFromString<NoiseHandshakeMessage>(message2Text)
            initiator.providePsk(verifyWith)
            val payload2 = initiator.readMessage(SendspinBase64.decode(message2.payload.data))
            assertContentEquals("{}".encodeToByteArray(), payload2)
            transport = initiator.result!!.transport
        }
    }

    private suspend fun newFixture(): Triple<CryptographyKotlinNoiseCrypto, X25519KeyPair, X25519KeyPair> {
        val crypto = CryptographyKotlinNoiseCrypto()
        return Triple(crypto, crypto.generateX25519KeyPair(), crypto.generateX25519KeyPair())
    }

    private fun handshake(
        crypto: CryptographyKotlinNoiseCrypto,
        clientStatic: X25519KeyPair,
        wire: WireVersion = WireVersion.LEGACY_9,
        messageTimeoutMillis: Long = 30_000,
        candidates: List<PskCandidate> = listOf(PskCandidate(SendspinPsk.SENTINEL_PSK, PskCategory.SENTINEL)),
    ) = SendspinHandshake(
        crypto = crypto,
        clientStatic = clientStatic,
        pskCandidates = { candidates },
        wire = wire,
        messageTimeoutMillis = messageTimeoutMillis,
    )

    @Test
    fun sentinelHandshakeEstablishesInteroperableTransport() = runTest {
        val (crypto, clientStatic, serverStatic) = newFixture()
        val server = FakeServer(crypto, serverStatic, clientStatic.publicKey, SendspinPsk.SENTINEL_PSK)
        val handshake = handshake(crypto, clientStatic)
        val io = PipeIo()
        val serverJob = launch { server.run(io) }
        val outcome = handshake.runInitial(io)
        serverJob.join()

        assertEquals(PskCategory.SENTINEL, outcome.matched.category)
        assertEquals(server.serverId, outcome.serverId)

        // The first wire frame is a well-formed client/init.
        val clientInit = Json.parseToJsonElement(server.receivedClientInit!!).jsonObject
        assertEquals("client/init", clientInit.getValue("type").jsonPrimitive.content)
        val payload = clientInit.getValue("payload").jsonObject
        assertEquals(handshake.clientId, payload.getValue("client_id").jsonPrimitive.content)
        assertEquals("1", payload.getValue("version").jsonPrimitive.content)
        assertEquals(
            SENDSPIN_SUITE_CHACHAPOLY,
            payload.getValue("suite").jsonPrimitive.content,
        )

        // Both directions of the established channel interoperate.
        val fromServer = server.transport!!.encrypt("server says hi".encodeToByteArray())
        assertContentEquals(
            "server says hi".encodeToByteArray(),
            outcome.transport.decrypt(fromServer),
        )
        val fromClient = outcome.transport.encrypt("client says hi".encodeToByteArray())
        assertContentEquals(
            "client says hi".encodeToByteArray(),
            server.transport!!.decrypt(fromClient),
        )
    }

    @Test
    fun unknownPskIdFailsHandshake() = runTest {
        val (crypto, clientStatic, serverStatic) = newFixture()
        val server = FakeServer(crypto, serverStatic, clientStatic.publicKey, ByteArray(32) { 0x42 })
        val handshake = handshake(crypto, clientStatic)
        val io = PipeIo()
        backgroundScope.launch { runCatching { server.run(io) } }
        assertFailsWith<HandshakeFailedException> { handshake.runInitial(io) }
    }

    @Test
    fun onSpecWireAnUnknownPskIdFallsBackToTheSentinel() = runTest {
        val (crypto, clientStatic, serverStatic) = newFixture()
        val server = FakeServer(crypto, serverStatic, clientStatic.publicKey, ByteArray(32) { 0x42 })
        val handshake = handshake(crypto, clientStatic, wire = WireVersion.SPEC_1_0)
        val io = PipeIo()
        val serverJob = launch { server.run(io, categoryCode = "lt", verifyWith = SendspinPsk.SENTINEL_PSK) }
        val outcome = handshake.runInitial(io)
        serverJob.join()
        assertEquals(PskCategory.SENTINEL, outcome.matched.category)
    }

    @Test
    fun onSpecWireACategoryMismatchIsAMiss() = runTest {
        val (crypto, clientStatic, serverStatic) = newFixture()
        val pairingPsk = ByteArray(32) { 0x42 }
        val server = FakeServer(crypto, serverStatic, clientStatic.publicKey, pairingPsk)
        val handshake = handshake(
            crypto,
            clientStatic,
            wire = WireVersion.SPEC_1_0,
            candidates = listOf(
                PskCandidate(SendspinPsk.SENTINEL_PSK, PskCategory.SENTINEL),
                PskCandidate(pairingPsk, PskCategory.PAIRING),
            ),
        )
        val io = PipeIo()
        // The server names the pairing PSK's id as a long-term one; only the Sentinel can complete.
        val serverJob = launch { server.run(io, categoryCode = "lt", verifyWith = SendspinPsk.SENTINEL_PSK) }
        val outcome = handshake.runInitial(io)
        serverJob.join()
        assertEquals(PskCategory.SENTINEL, outcome.matched.category)
    }

    @Test
    fun onSpecWireAMatchingCategoryIsAMatch() = runTest {
        val (crypto, clientStatic, serverStatic) = newFixture()
        val pairingPsk = ByteArray(32) { 0x42 }
        val server = FakeServer(crypto, serverStatic, clientStatic.publicKey, pairingPsk)
        val handshake = handshake(
            crypto,
            clientStatic,
            wire = WireVersion.SPEC_1_0,
            candidates = listOf(
                PskCandidate(SendspinPsk.SENTINEL_PSK, PskCategory.SENTINEL),
                PskCandidate(pairingPsk, PskCategory.PAIRING),
            ),
        )
        val io = PipeIo()
        val serverJob = launch { server.run(io, categoryCode = "pr") }
        val outcome = handshake.runInitial(io)
        serverJob.join()
        assertEquals(PskCategory.PAIRING, outcome.matched.category)
    }

    @Test
    fun onLegacyWireTheCategoryIsIgnored() = runTest {
        val (crypto, clientStatic, serverStatic) = newFixture()
        val pairingPsk = ByteArray(32) { 0x42 }
        val server = FakeServer(crypto, serverStatic, clientStatic.publicKey, pairingPsk)
        val handshake = handshake(
            crypto,
            clientStatic,
            candidates = listOf(PskCandidate(pairingPsk, PskCategory.PAIRING)),
        )
        val io = PipeIo()
        val serverJob = launch { server.run(io, categoryCode = "lt") }
        assertEquals(PskCategory.PAIRING, handshake.runInitial(io).matched.category)
        serverJob.join()
    }

    @Test
    fun malformedInnerPayloadFailsHandshake() = runTest {
        val (crypto, clientStatic, serverStatic) = newFixture()
        val server = FakeServer(crypto, serverStatic, clientStatic.publicKey, SendspinPsk.SENTINEL_PSK)
        val handshake = handshake(crypto, clientStatic)
        val io = PipeIo()
        backgroundScope.launch { runCatching { server.run(io, innerPayloadOverride = "not-json") } }
        assertFailsWith<HandshakeFailedException> { handshake.runInitial(io) }
    }

    @Test
    fun storedRecordBoundToDifferentServerFailsAfterMatch() = runTest {
        val (crypto, clientStatic, serverStatic) = newFixture()
        val longTermPsk = ByteArray(32) { 0x17 }
        val server = FakeServer(crypto, serverStatic, clientStatic.publicKey, longTermPsk)
        val handshake = handshake(
            crypto,
            clientStatic,
            candidates = listOf(
                PskCandidate(psk = longTermPsk, category = PskCategory.LONG_TERM_STORED, serverId = "someone-else"),
            ),
        )
        val io = PipeIo()
        backgroundScope.launch { runCatching { server.run(io) } }
        assertFailsWith<HandshakeFailedException> { handshake.runInitial(io) }
    }

    @Test
    fun storedRecordBoundToThisServerSucceeds() = runTest {
        val (crypto, clientStatic, serverStatic) = newFixture()
        val longTermPsk = ByteArray(32) { 0x17 }
        val server = FakeServer(crypto, serverStatic, clientStatic.publicKey, longTermPsk)
        val handshake = handshake(
            crypto,
            clientStatic,
            candidates = listOf(
                PskCandidate(psk = longTermPsk, category = PskCategory.LONG_TERM_STORED, serverId = server.serverId),
            ),
        )
        val io = PipeIo()
        val serverJob = launch { server.run(io) }
        val outcome = handshake.runInitial(io)
        serverJob.join()
        assertEquals(PskCategory.LONG_TERM_STORED, outcome.matched.category)
    }

    @Test
    fun unsupportedServerInitVersionFailsHandshake() = runTest {
        val (crypto, clientStatic, serverStatic) = newFixture()
        val server = FakeServer(crypto, serverStatic, clientStatic.publicKey, SendspinPsk.SENTINEL_PSK)
        val handshake = handshake(crypto, clientStatic)
        val io = PipeIo()
        backgroundScope.launch { runCatching { server.run(io, serverInitVersion = 2) } }
        assertFailsWith<UnsupportedCoreVersionException> { handshake.runInitial(io) }
    }

    @Test
    fun serverErrorForVersionOrSuiteIsTerminalAndMalformedIsNot() = runTest {
        val (crypto, clientStatic, serverStatic) = newFixture()
        val server = FakeServer(crypto, serverStatic, clientStatic.publicKey, SendspinPsk.SENTINEL_PSK)
        listOf("unsupported_version", "unsupported_suite").forEach { reason ->
            val io = PipeIo()
            backgroundScope.launch { server.failInit(io, reason) }
            assertFailsWith<UnsupportedCoreVersionException>(reason) { handshake(crypto, clientStatic).runInitial(io) }
        }
        val io = PipeIo()
        backgroundScope.launch { server.failInit(io, "malformed") }
        val failure = assertFailsWith<HandshakeFailedException> { handshake(crypto, clientStatic).runInitial(io) }
        assertIsNot<UnsupportedCoreVersionException>(failure)
    }

    @Test
    fun binaryFrameDuringHandshakeFails() = runTest {
        val (crypto, clientStatic, _) = newFixture()
        val handshake = handshake(crypto, clientStatic, candidates = emptyList())
        val io = PipeIo()
        io.serverToClient.send(HandshakeFrame.Binary(byteArrayOf(1, 2, 3)))
        assertFailsWith<HandshakeFailedException> { handshake.runInitial(io) }
    }

    @Test
    fun silentServerTimesOutAsHandshakeFailure() = runTest {
        val (crypto, clientStatic, _) = newFixture()
        val handshake = handshake(crypto, clientStatic, messageTimeoutMillis = 50, candidates = emptyList())
        assertFailsWith<HandshakeFailedException> { handshake.runInitial(PipeIo()) }
    }
}
