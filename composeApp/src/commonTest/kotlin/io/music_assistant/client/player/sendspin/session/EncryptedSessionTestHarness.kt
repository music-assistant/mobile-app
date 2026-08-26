package io.music_assistant.client.player.sendspin.session

import io.music_assistant.client.player.sendspin.identity.FakeSendspinKeyStore
import io.music_assistant.client.player.sendspin.identity.SendspinTrustStore
import io.music_assistant.client.player.sendspin.model.AudioFormatSpec
import io.music_assistant.client.player.sendspin.model.NoiseHandshakeMessage
import io.music_assistant.client.player.sendspin.model.NoiseHandshakePayload
import io.music_assistant.client.player.sendspin.model.PlayerSupport
import io.music_assistant.client.player.sendspin.model.ServerInitMessage
import io.music_assistant.client.player.sendspin.model.ServerInitPayload
import io.music_assistant.client.player.sendspin.model.VersionedRole
import io.music_assistant.client.player.sendspin.noise.HandshakeState
import io.music_assistant.client.player.sendspin.noise.NoiseFraming
import io.music_assistant.client.player.sendspin.noise.NoisePattern
import io.music_assistant.client.player.sendspin.noise.NoiseRole
import io.music_assistant.client.player.sendspin.noise.NoiseTransport
import io.music_assistant.client.player.sendspin.noise.SendspinBase64
import io.music_assistant.client.player.sendspin.noise.SendspinPsk
import io.music_assistant.client.player.sendspin.noise.crypto.CryptographyKotlinNoiseCrypto
import io.music_assistant.client.player.sendspin.noise.crypto.X25519KeyPair
import io.music_assistant.client.player.sendspin.transport.InboundTransportEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlin.test.assertEquals

/**
 * Test harness for driving [EncryptedSession] end to end over
 * [FakeSendspinTransport]: an in-test Noise-initiator server, session
 * fixtures, and real-time test scaffolding (the session runs on real
 * dispatchers, so tests avoid the virtual clock).
 *
 * The in-test server reuses the production Noise core and framing, so these
 * tests pin session behavior, not Noise interoperability — the reference
 * vectors are the independent oracle for the core itself.
 */
internal abstract class EncryptedSessionTestHarness {
    protected val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    protected class Fixture(
        val crypto: CryptographyKotlinNoiseCrypto,
        val transport: FakeSendspinTransport,
        val session: EncryptedSession,
        val trustStore: SendspinTrustStore,
        val serverStatic: X25519KeyPair,
        val events: ReceiveChannel<SessionEvent>,
    ) {
        val serverId: String get() = SendspinBase64.encode(serverStatic.publicKey)

        suspend fun nextEvent(): SessionEvent = withTimeout(AWAIT_MILLIS) { events.receive() }

        suspend fun nextEventSkippingNegotiation(): SessionEvent {
            while (true) {
                val event = nextEvent()
                if (event !is SessionEvent.Negotiating) return event
            }
        }
    }

    protected inner class FakeServer(
        private val fixture: Fixture,
        private val psk: ByteArray,
    ) {
        lateinit var noise: NoiseTransport
        lateinit var handshakeHash: ByteArray
        private var decoder = NoiseFraming.Decoder()

        suspend fun establish(epoch: Int = 1, pskIdOverride: String? = null) {
            val crypto = fixture.crypto
            val transport = fixture.transport
            val clientInit = withTimeout(5_000) { transport.textOut.receive() }
            val serverInitText = json.encodeToString(
                ServerInitMessage(payload = ServerInitPayload(fixture.serverId, 1)),
            )
            transport.emit(InboundTransportEvent.Text(epoch, serverInitText))

            val prologue = clientInit.encodeToByteArray() + serverInitText.encodeToByteArray()
            val handshake = HandshakeState.initialize(
                crypto = crypto,
                pattern = NoisePattern.KKPSK2,
                role = NoiseRole.INITIATOR,
                prologue = prologue,
                localStatic = fixture.serverStatic,
                remoteStaticPublic = fixture.trustStore.identity.keyPair.publicKey,
                psk = psk,
            )
            val pskId = pskIdOverride ?: SendspinPsk.pskId(crypto, psk)
            val message1 = handshake.writeMessage("""{"psk_id":"$pskId"}""".encodeToByteArray())
            transport.emit(
                InboundTransportEvent.Text(
                    epoch,
                    json.encodeToString(
                        NoiseHandshakeMessage(
                            payload = NoiseHandshakePayload(SendspinBase64.encode(message1)),
                        ),
                    ),
                ),
            )
            val message2Json = withTimeout(5_000) { transport.textOut.receive() }
            handshake.readMessage(
                SendspinBase64.decode(
                    json.decodeFromString<NoiseHandshakeMessage>(message2Json).payload.data,
                ),
            )
            noise = handshake.result!!.transport
            handshakeHash = handshake.result!!.handshakeHash
            decoder = NoiseFraming.Decoder()
        }

        suspend fun sendJson(text: String, epoch: Int = 1) {
            NoiseFraming.encode(NoiseFraming.TYPE_JSON, text.encodeToByteArray()).forEach {
                fixture.transport.emit(InboundTransportEvent.Binary(epoch, noise.encrypt(it)))
            }
        }

        suspend fun sendAudio(type: Int, payload: ByteArray, epoch: Int = 1) {
            NoiseFraming.encode(type, payload).forEach {
                fixture.transport.emit(InboundTransportEvent.Binary(epoch, noise.encrypt(it)))
            }
        }

        /** Receives and reassembles the client's next application message. */
        suspend fun receiveMessage(): NoiseFraming.Message {
            while (true) {
                val ciphertext = withTimeout(5_000) { fixture.transport.binaryOut.receive() }
                val message = decoder.decode(noise.decrypt(ciphertext)) ?: continue
                return message
            }
        }

        suspend fun receiveJson(): String {
            val message = receiveMessage()
            assertEquals(NoiseFraming.TYPE_JSON, message.type)
            return message.payload.decodeToString()
        }

        /** Runs a server-initiated in-band re-handshake to [newPsk]. */
        suspend fun rehandshake(newPsk: ByteArray, epoch: Int = 1) {
            val handshake = HandshakeState.initialize(
                crypto = fixture.crypto,
                pattern = NoisePattern.KKPSK2,
                role = NoiseRole.INITIATOR,
                prologue = handshakeHash,
                localStatic = fixture.serverStatic,
                remoteStaticPublic = fixture.trustStore.identity.keyPair.publicKey,
                psk = newPsk,
            )
            val pskId = SendspinPsk.pskId(fixture.crypto, newPsk)
            val message1 = handshake.writeMessage("""{"psk_id":"$pskId"}""".encodeToByteArray())
            // Message 1 travels as an ordinary encrypted JSON message.
            sendJson(
                json.encodeToString(
                    NoiseHandshakeMessage(payload = NoiseHandshakePayload(SendspinBase64.encode(message1))),
                ),
                epoch,
            )
            // Message 2 arrives under the old transport keys.
            val message2Json = receiveJson()
            handshake.readMessage(
                SendspinBase64.decode(
                    json.decodeFromString<NoiseHandshakeMessage>(message2Json).payload.data,
                ),
            )
            noise = handshake.result!!.transport
            handshakeHash = handshake.result!!.handshakeHash
            decoder = NoiseFraming.Decoder()
        }

        suspend fun completeHelloExchange(epoch: Int = 1): String {
            sendJson("""{"type":"server/hello","payload":{"name":"Enc Server"}}""", epoch)
            return receiveJson()
        }

        suspend fun activate(
            activities: String = """["playback"]""",
            activeRoles: String? = """["player@v1"]""",
            pairing: String? = null,
            epoch: Int = 1,
        ) {
            val fields = buildList {
                add("\"activities\":$activities")
                if (activeRoles != null) add("\"active_roles\":$activeRoles")
                if (pairing != null) add("\"pairing\":$pairing")
            }.joinToString(",")
            sendJson("""{"type":"server/activate","payload":{$fields}}""", epoch)
        }
    }

    protected suspend fun fixture(
        scope: CoroutineScope,
        requiresAuth: Boolean = false,
        unpairedAccess: Boolean = true,
        pairingAttemptTimeoutMillis: Long = 120_000,
    ): Fixture {
        val crypto = CryptographyKotlinNoiseCrypto()
        val trustStore = SendspinTrustStore.load(FakeSendspinKeyStore(), crypto)
        trustStore.setUnpairedAccessEnabled(unpairedAccess)
        val transport = FakeSendspinTransport()
        val session = EncryptedSession(
            transport = transport,
            config = EncryptedSessionConfig(
                requiresAuth = requiresAuth,
                authJson = if (requiresAuth) """{"type":"auth","token":"tok"}""" else null,
                deviceName = "Enc Device",
                supportedRoles = listOf(VersionedRole.PLAYER_V1),
                playerSupport = PlayerSupport(
                    supportedFormats = listOf(
                        AudioFormatSpec(
                            codec = io.music_assistant.client.player.sendspin.model.AudioCodec.OPUS,
                            channels = 2,
                            sampleRate = 48000,
                            bitDepth = 16,
                        ),
                    ),
                    bufferCapacity = 1_000_000,
                    supportedCommands = emptyList(),
                ),
                deviceInfo = null,
                pairingAttemptTimeoutMillis = pairingAttemptTimeoutMillis,
            ),
            crypto = crypto,
            trustStore = trustStore,
        )
        val serverStatic = crypto.generateX25519KeyPair()
        val events = session.events.produceIn(scope)
        return Fixture(crypto, transport, session, trustStore, serverStatic, events)
    }

    protected fun runRealTime(block: suspend CoroutineScope.() -> Unit) = runTest {
        // The session runs on real dispatchers; keep the test on real time too
        // so channel waits don't race the virtual clock.
        withContext(Dispatchers.Default) {
            block()
            coroutineContext[Job]?.cancelChildren()
        }
    }

    /** Runs the pairing preamble: sentinel session, re-handshake to the Pairing PSK. */
    protected suspend fun CoroutineScope.pairingPreamble(f: Fixture): FakeServer {
        val server = FakeServer(f, SendspinPsk.SENTINEL_PSK)
        f.session.start()
        server.establish()
        server.completeHelloExchange()
        kotlin.test.assertIs<SessionEvent.ProtocolReady>(f.nextEventSkippingNegotiation())

        server.rehandshake(f.trustStore.pairingPsk)
        kotlin.test.assertIs<SessionEvent.RehandshakeCompleted>(f.nextEvent())
        server.completeHelloExchange()
        kotlin.test.assertIs<SessionEvent.ProtocolReady>(f.nextEvent())
        server.activate(
            activities = """["pairing"]""",
            activeRoles = "[]",
            pairing = """{"method":"pairing_psk"}""",
        )
        kotlin.test.assertIs<SessionEvent.Activated>(f.nextEvent())
        return server
    }

    protected companion object {
        const val AWAIT_MILLIS = 5_000L
    }
}
