package io.music_assistant.sendspin.session

import io.music_assistant.sendspin.api.AudioCodec
import io.music_assistant.sendspin.api.AudioFormatSpec
import io.music_assistant.sendspin.api.WireVersion
import io.music_assistant.sendspin.fakes.FakeNoiseServer
import io.music_assistant.sendspin.fakes.FakeTransport
import io.music_assistant.sendspin.identity.FakeSendspinKeyStore
import io.music_assistant.sendspin.identity.SendspinTrustStore
import io.music_assistant.sendspin.noise.SendspinBase64
import io.music_assistant.sendspin.noise.SendspinPsk
import io.music_assistant.sendspin.noise.crypto.CryptographyKotlinNoiseCrypto
import io.music_assistant.sendspin.noise.crypto.X25519KeyPair
import io.music_assistant.sendspin.wire.AudioChunk
import io.music_assistant.sendspin.wire.PlayerSupport
import io.music_assistant.sendspin.wire.ServerMessage
import io.music_assistant.sendspin.wire.WireAudioFormat
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.assertIs
import kotlin.test.fail

/**
 * Drives [NoiseSession] end to end over [FakeTransport] with a [FakeNoiseServer]
 * speaking [wire]. Runs on virtual time: the crypto is synchronous, so the only
 * way a timeout fires is a genuine deadlock, which then fails fast.
 */
internal abstract class NoiseSessionTestHarness(protected val wire: WireVersion) {
    sealed interface Event {
        data class Ready(val info: SessionInfo) : Event
        data class Activated(val activation: Activation) : Event
        data class Message(val message: ServerMessage) : Event
        class Audio(val timestamp: Long, val data: ByteArray) : Event
    }

    protected class Fixture(
        val crypto: CryptographyKotlinNoiseCrypto,
        val transport: FakeTransport,
        val session: NoiseSession,
        val trustStore: SendspinTrustStore,
        val serverStatic: X25519KeyPair,
        val events: Channel<Event>,
        val run: Deferred<Result<Unit>>,
    ) {
        val serverId: String get() = SendspinBase64.encode(serverStatic.publicKey)

        suspend fun nextEvent(): Event = withTimeout(AWAIT_MILLIS) { events.receive() }

        suspend fun nextMessage(): ServerMessage = assertIs<Event.Message>(nextEvent()).message

        suspend fun awaitFailure(): Throwable = run.await().exceptionOrNull() ?: fail("session ended normally")

        suspend fun awaitClean() = run.await().getOrThrow()
    }

    protected fun FakeServer(f: Fixture, psk: ByteArray): FakeNoiseServer =
        FakeNoiseServer(f.crypto, f.transport, f.serverStatic, f.trustStore.identity.keyPair.publicKey, psk, wire)

    private suspend fun TestScope.fixture(
        unpairedAccess: Boolean,
        pairingAttemptTimeoutMillis: Long,
        deviceName: String,
    ): Fixture {
        val crypto = CryptographyKotlinNoiseCrypto()
        val trustStore = SendspinTrustStore.load(FakeSendspinKeyStore(), crypto)
        trustStore.setUnpairedAccessEnabled(unpairedAccess)
        val transport = FakeTransport()
        val session = NoiseSession(
            transport = transport,
            config = SessionConfig(
                wire = wire,
                deviceName = deviceName,
                playerSupport = PlayerSupport(
                    supportedFormats = listOf(WireAudioFormat.from(AudioFormatSpec(AudioCodec.OPUS, 2, 48000, 16))),
                    bufferCapacity = 1_000_000,
                    supportedCommands = emptyList(),
                ),
                deviceInfo = null,
                pairingAttemptTimeoutMillis = pairingAttemptTimeoutMillis,
            ),
            crypto = crypto,
            trustStore = trustStore,
        )
        val events = Channel<Event>(Channel.UNLIMITED)
        val handler = object : SessionHandler {
            override fun onReady(info: SessionInfo) {
                events.trySend(Event.Ready(info))
            }

            override fun onActivated(activation: Activation) {
                events.trySend(Event.Activated(activation))
            }

            override suspend fun onMessage(message: ServerMessage) {
                events.trySend(Event.Message(message))
            }

            override fun onAudio(chunk: AudioChunk) {
                events.trySend(
                    Event.Audio(chunk.timestampMicros, chunk.body.copyOfRange(chunk.offset, chunk.body.size)),
                )
            }
        }
        val run = async { runCatching { session.run(handler) } }
        return Fixture(crypto, transport, session, trustStore, crypto.generateX25519KeyPair(), events, run)
    }

    protected fun sessionTest(
        unpairedAccess: Boolean = true,
        pairingAttemptTimeoutMillis: Long = 120_000,
        deviceName: String = "Enc Device",
        block: suspend TestScope.(Fixture) -> Unit,
    ) = runTest {
        val f = fixture(unpairedAccess, pairingAttemptTimeoutMillis, deviceName)
        try {
            block(f)
        } finally {
            f.run.cancel()
        }
    }

    /**
     * What follows a re-handshake: the 9.x wire repeats the hello exchange (returned,
     * with its `Ready`); spec 1.0.0-rc1 goes straight to `server/activate` (null).
     */
    protected suspend fun helloAfterRehandshake(server: FakeNoiseServer, f: Fixture): String? = when (wire) {
        WireVersion.LEGACY_9 -> server.completeHelloExchange().also { assertIs<Event.Ready>(f.nextEvent()) }
        WireVersion.SPEC_1_0 -> null
    }

    /** Sentinel session, then re-handshake to the Pairing PSK and a pairing activation. */
    protected suspend fun pairingPreamble(f: Fixture): FakeNoiseServer {
        val server = FakeServer(f, SendspinPsk.SENTINEL_PSK)
        server.establish()
        server.completeHelloExchange()
        assertIs<Event.Ready>(f.nextEvent())

        server.rehandshake(f.trustStore.pairingPsk, PAIRING_CATEGORY)
        helloAfterRehandshake(server, f)
        server.activate(activities = """["pairing"]""", activeRoles = "[]", pairing = """{"method":"pairing_psk"}""")
        assertIs<Event.Activated>(f.nextEvent())
        return server
    }

    protected companion object {
        const val AWAIT_MILLIS = 5_000L
        const val PAIRING_CATEGORY = "pr"
    }
}
