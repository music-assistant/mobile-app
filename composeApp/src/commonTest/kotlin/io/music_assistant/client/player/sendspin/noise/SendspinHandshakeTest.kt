package io.music_assistant.client.player.sendspin.noise

import io.music_assistant.client.player.sendspin.model.NoiseHandshakeMessage
import io.music_assistant.client.player.sendspin.model.NoiseHandshakePayload
import io.music_assistant.client.player.sendspin.model.ServerInitMessage
import io.music_assistant.client.player.sendspin.model.ServerInitPayload
import io.music_assistant.client.player.sendspin.noise.crypto.CryptographyKotlinNoiseCrypto
import io.music_assistant.client.player.sendspin.noise.crypto.X25519KeyPair
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

        suspend fun run(
            io: PipeIo,
            serverInitVersion: Int = SENDSPIN_CORE_VERSION,
            innerPayloadOverride: String? = null,
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
                psk = psk,
            )
            val innerPayload = innerPayloadOverride
                ?: """{"psk_id":"${SendspinPsk.pskId(crypto, psk)}"}"""
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
            val payload2 = initiator.readMessage(SendspinBase64.decode(message2.payload.data))
            assertContentEquals("{}".encodeToByteArray(), payload2)
            transport = initiator.result!!.transport
        }
    }

    private suspend fun newFixture(): Triple<CryptographyKotlinNoiseCrypto, X25519KeyPair, X25519KeyPair> {
        val crypto = CryptographyKotlinNoiseCrypto()
        return Triple(crypto, crypto.generateX25519KeyPair(), crypto.generateX25519KeyPair())
    }

    @Test
    fun sentinelHandshakeEstablishesInteroperableTransport() = runTest {
        val (crypto, clientStatic, serverStatic) = newFixture()
        val server = FakeServer(crypto, serverStatic, clientStatic.publicKey, SendspinPsk.SENTINEL_PSK)
        val handshake = SendspinHandshake(
            crypto = crypto,
            clientStatic = clientStatic,
            pskCandidates = {
                listOf(PskCandidate(SendspinPsk.SENTINEL_PSK, PskCategory.SENTINEL))
            },
        )
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
        val serverPsk = ByteArray(32) { 0x42 }
        val server = FakeServer(crypto, serverStatic, clientStatic.publicKey, serverPsk)
        val handshake = SendspinHandshake(
            crypto = crypto,
            clientStatic = clientStatic,
            pskCandidates = {
                listOf(PskCandidate(SendspinPsk.SENTINEL_PSK, PskCategory.SENTINEL))
            },
        )
        val io = PipeIo()
        backgroundScope.launch { runCatching { server.run(io) } }
        assertFailsWith<HandshakeFailedException> { handshake.runInitial(io) }
    }

    @Test
    fun malformedInnerPayloadFailsHandshake() = runTest {
        val (crypto, clientStatic, serverStatic) = newFixture()
        val server = FakeServer(crypto, serverStatic, clientStatic.publicKey, SendspinPsk.SENTINEL_PSK)
        val handshake = SendspinHandshake(
            crypto = crypto,
            clientStatic = clientStatic,
            pskCandidates = {
                listOf(PskCandidate(SendspinPsk.SENTINEL_PSK, PskCategory.SENTINEL))
            },
        )
        val io = PipeIo()
        backgroundScope.launch { runCatching { server.run(io, innerPayloadOverride = "not-json") } }
        assertFailsWith<HandshakeFailedException> { handshake.runInitial(io) }
    }

    @Test
    fun storedRecordBoundToDifferentServerFailsAfterMatch() = runTest {
        val (crypto, clientStatic, serverStatic) = newFixture()
        val longTermPsk = ByteArray(32) { 0x17 }
        val server = FakeServer(crypto, serverStatic, clientStatic.publicKey, longTermPsk)
        val handshake = SendspinHandshake(
            crypto = crypto,
            clientStatic = clientStatic,
            pskCandidates = {
                listOf(
                    PskCandidate(
                        psk = longTermPsk,
                        category = PskCategory.LONG_TERM_STORED,
                        serverId = "someone-else",
                    ),
                )
            },
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
        val handshake = SendspinHandshake(
            crypto = crypto,
            clientStatic = clientStatic,
            pskCandidates = {
                listOf(
                    PskCandidate(
                        psk = longTermPsk,
                        category = PskCategory.LONG_TERM_STORED,
                        serverId = server.serverId,
                    ),
                )
            },
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
        val handshake = SendspinHandshake(
            crypto = crypto,
            clientStatic = clientStatic,
            pskCandidates = {
                listOf(PskCandidate(SendspinPsk.SENTINEL_PSK, PskCategory.SENTINEL))
            },
        )
        val io = PipeIo()
        backgroundScope.launch { runCatching { server.run(io, serverInitVersion = 2) } }
        assertFailsWith<HandshakeFailedException> { handshake.runInitial(io) }
    }

    @Test
    fun binaryFrameDuringHandshakeFails() = runTest {
        val (crypto, clientStatic, _) = newFixture()
        val handshake = SendspinHandshake(
            crypto = crypto,
            clientStatic = clientStatic,
            pskCandidates = { emptyList() },
        )
        val io = PipeIo()
        io.serverToClient.send(HandshakeFrame.Binary(byteArrayOf(1, 2, 3)))
        assertFailsWith<HandshakeFailedException> { handshake.runInitial(io) }
    }

    @Test
    fun silentServerTimesOutAsHandshakeFailure() = runTest {
        val (crypto, clientStatic, _) = newFixture()
        val handshake = SendspinHandshake(
            crypto = crypto,
            clientStatic = clientStatic,
            pskCandidates = { emptyList() },
            messageTimeoutMillis = 50,
        )
        assertFailsWith<HandshakeFailedException> { handshake.runInitial(PipeIo()) }
    }
}
