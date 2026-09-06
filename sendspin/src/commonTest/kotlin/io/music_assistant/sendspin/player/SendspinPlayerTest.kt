package io.music_assistant.sendspin.player

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.music_assistant.sendspin.api.AudioPhase
import io.music_assistant.sendspin.api.Endpoint
import io.music_assistant.sendspin.api.LocalPlayerConfig
import io.music_assistant.sendspin.api.MonotonicClock
import io.music_assistant.sendspin.api.PlayerEvent
import io.music_assistant.sendspin.api.PlayerState
import io.music_assistant.sendspin.api.SendspinDeps
import io.music_assistant.sendspin.api.StopCause
import io.music_assistant.sendspin.fakes.FakeDecoderFactory
import io.music_assistant.sendspin.fakes.FakeNoiseServer
import io.music_assistant.sendspin.fakes.FakeSink
import io.music_assistant.sendspin.fakes.FakeTransport
import io.music_assistant.sendspin.identity.FakeSendspinKeyStore
import io.music_assistant.sendspin.identity.SendspinTrustStore
import io.music_assistant.sendspin.noise.crypto.CryptographyKotlinNoiseCrypto
import io.music_assistant.sendspin.wire.AudioCodec
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Whole-module tests through the public API with fakes at every port. */
@OptIn(ExperimentalCoroutinesApi::class)
class SendspinPlayerTest {
    private inner class Harness(val scope: TestScope) {
        val crypto = CryptographyKotlinNoiseCrypto()
        val keyStore = FakeSendspinKeyStore()
        val clock = MonotonicClock { scope.currentTime * 1_000 }
        val sink = FakeSink { clock.nowMicros() }
        val transports = Channel<FakeTransport>(Channel.UNLIMITED)
        val handedOut = mutableListOf<FakeTransport>()
        val endpoint = Endpoint.WebRtc { FakeTransport().also { handedOut += it; transports.trySend(it) } }
        val config = MutableStateFlow<LocalPlayerConfig?>(
            LocalPlayerConfig(endpoint, "Device", listOf(AudioCodec.FLAC), bufferCapacityBytes = 10_000_000, userDelayMs = 0),
        )
        val events = Channel<PlayerEvent>(Channel.UNLIMITED)
        var pairCalls = 0
        lateinit var identityPublicKey: ByteArray
        lateinit var serverStatic: io.music_assistant.sendspin.noise.crypto.X25519KeyPair
        lateinit var player: io.music_assistant.sendspin.api.SendspinPlayer

        suspend fun start() {
            // The player loads its identity and settings from the same key store.
            val store = SendspinTrustStore.load(keyStore, crypto)
            store.setUnpairedAccessEnabled(true)
            identityPublicKey = store.identity.keyPair.publicKey
            serverStatic = crypto.generateX25519KeyPair()
            val deps = SendspinDeps(
                sink = sink,
                decoders = FakeDecoderFactory(),
                keyStore = keyStore,
                crypto = crypto,
                httpClient = HttpClient(MockEngine { respond("") }),
                online = MutableStateFlow(true),
                pairWebPlayer = { pairCalls++ },
                audioDispatcher = StandardTestDispatcher(scope.testScheduler),
                clock = clock,
            )
            player = SendspinPlayerImpl(config, deps, scope.backgroundScope)
            scope.backgroundScope.launch { player.events.collect { events.trySend(it) } }
            scope.runCurrent()
        }

        suspend fun nextTransport(): FakeTransport = withTimeout(5_000) { transports.receive() }

        suspend fun nextEvent(): PlayerEvent = withTimeout(5_000) { events.receive() }

        /** Brings a server up on the next transport and answers its probes. */
        suspend fun connectServer(): FakeNoiseServer {
            val transport = nextTransport()
            val server = FakeNoiseServer(crypto, transport, serverStatic, identityPublicKey)
            server.bringUp()
            server.serve(scope.backgroundScope)
            // Let the first probe burst complete so the clock is synced before streaming.
            scope.advanceTimeBy(1_500)
            scope.runCurrent()
            return server
        }

        suspend fun awaitConnected(): PlayerState.Connected =
            withTimeout(5_000) { player.state.first { it is PlayerState.Connected } } as PlayerState.Connected

        /** The live connection fails from the network side. */
        fun dropConnection() = handedOut.last().serverDrops(IllegalStateException("cable"))

        fun pcm(millis: Int) = ByteArray(millis * 48 * 4) { 1 }
    }

    private fun playerTest(block: suspend TestScope.(Harness) -> Unit) = runTest {
        val h = Harness(this)
        h.start()
        block(h)
    }

    @Test
    fun connectsStreamsReconnectsResumesAndDisablesWithGoodbye() = playerTest { h ->
        assertIs<PlayerState.Connecting>(h.player.state.value)
        val server = h.connectServer()
        val connected = h.awaitConnected()
        assertEquals("Enc Server", connected.serverName)
        assertEquals(PlayerEvent.ServerRefreshNeeded, h.nextEvent())
        assertEquals(1, h.pairCalls, "sentinel session asks for silent pairing")

        server.startStream()
        server.sendAudio(4, currentTime * 1_000, h.pcm(10))
        // Future audio keeps the buffer alive across the drop below.
        server.sendAudio(4, currentTime * 1_000 + 3_000_000, h.pcm(10))
        server.sendAudio(4, currentTime * 1_000 + 4_000_000, h.pcm(10))
        runCurrent()
        assertIs<PlayerEvent.PlaybackStarted>(h.nextEvent())
        assertEquals(AudioPhase.Playing, (h.player.state.value as PlayerState.Connected).audio.phase)
        assertEquals(1, h.sink.handles.size)

        // Connection drops: reconnecting, pipeline untouched.
        h.dropConnection()
        val reconnecting = withTimeout(5_000) { h.player.state.first { it is PlayerState.Reconnecting } } as PlayerState.Reconnecting
        assertEquals(0, reconnecting.attempt)
        val server2 = h.connectServer()
        h.awaitConnected()
        assertEquals(PlayerEvent.ServerRefreshNeeded, h.nextEvent())
        // Same format on the new connection: resume, no rebuild.
        server2.startStream()
        server2.sendAudio(4, currentTime * 1_000, h.pcm(10))
        runCurrent()
        assertEquals(1, h.sink.handles.size, "resume keeps the sink")
        assertEquals(2, h.sink.handles.single().writes.size)

        // Disable: goodbye user_request, then Disabled.
        h.config.value = null
        runCurrent()
        assertEquals(PlayerState.Disabled, h.player.state.value)
        assertEquals(PlayerEvent.PlaybackStopped(StopCause.Disabled), h.nextEvent())
        assertTrue(server2.clientMessageTypes.contains("client/goodbye"), "goodbye sent: ${server2.clientMessageTypes}")
        assertTrue(h.sink.handles.single().closed)
    }

    @Test
    fun starvationWhileConnectedIsSilentButStarvationWhileDownStopsPlayback() = playerTest { h ->
        val server = h.connectServer()
        h.awaitConnected()
        h.nextEvent() // ServerRefreshNeeded
        server.startStream()
        server.sendAudio(4, currentTime * 1_000, h.pcm(10))
        runCurrent()
        assertIs<PlayerEvent.PlaybackStarted>(h.nextEvent())
        val connected = h.player.state.value as PlayerState.Connected
        assertTrue(connected.audio.starved, "buffer is empty after playing the only chunk")
        assertTrue(h.events.isEmpty, "no stop while the connection is up")

        h.dropConnection()
        runCurrent()
        assertEquals(PlayerEvent.PlaybackStopped(StopCause.Starved), h.nextEvent())
        assertEquals(AudioPhase.Idle, (h.player.state.value as PlayerState.Reconnecting).audio.phase)
    }

    @Test
    fun bufferedAudioDrainsAcrossAReconnect() = playerTest { h ->
        val server = h.connectServer()
        h.awaitConnected()
        h.nextEvent()
        server.startStream()
        val now = currentTime * 1_000
        server.sendAudio(4, now, h.pcm(10))
        for (i in 1..5) server.sendAudio(4, now + i * 500_000L, h.pcm(10))
        runCurrent()
        assertIs<PlayerEvent.PlaybackStarted>(h.nextEvent())
        val writesBefore = h.sink.handles.single().writes.size

        h.dropConnection()
        withTimeout(5_000) { h.player.state.first { it is PlayerState.Reconnecting } }
        advanceTimeBy(1_500)
        runCurrent()
        assertTrue(h.sink.handles.single().writes.size > writesBefore, "audio kept flowing while disconnected")
        assertTrue(h.events.isEmpty, "buffered audio is not an outage")
        // Only after the buffer runs dry is playback declared stopped.
        advanceTimeBy(1_500)
        runCurrent()
        assertEquals(PlayerEvent.PlaybackStopped(StopCause.Starved), h.nextEvent())
    }

    @Test
    fun deviceNameChangeRestartsTheConnectionAndKeepsThePipeline() = playerTest { h ->
        val server = h.connectServer()
        h.awaitConnected()
        h.nextEvent()
        server.startStream()
        server.sendAudio(4, currentTime * 1_000, h.pcm(10))
        runCurrent()
        assertIs<PlayerEvent.PlaybackStarted>(h.nextEvent())

        // Future audio keeps the pipeline playing through the restart.
        server.sendAudio(4, currentTime * 1_000 + 3_000_000, h.pcm(10))
        runCurrent()
        h.config.value = h.config.value!!.copy(deviceName = "Renamed")
        runCurrent()
        assertTrue(server.clientMessageTypes.contains("client/goodbye"), "warm goodbye on restart")
        val server2 = h.connectServer()
        h.awaitConnected()
        assertEquals(1, h.sink.handles.size, "pipeline survives a connection restart")
        assertFalse(h.sink.handles.single().closed)
        server2.startStream()
        runCurrent()
        assertEquals(1, h.sink.handles.size, "same format on the new connection resumes")
    }
}
