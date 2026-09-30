package io.music_assistant.sendspin.player

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.music_assistant.sendspin.SendspinPlayer
import io.music_assistant.sendspin.api.AudioCodec
import io.music_assistant.sendspin.api.AudioPhase
import io.music_assistant.sendspin.api.Endpoint
import io.music_assistant.sendspin.api.FailureCause
import io.music_assistant.sendspin.api.LocalPlayerConfig
import io.music_assistant.sendspin.api.MonotonicClock
import io.music_assistant.sendspin.api.PlayerEvent
import io.music_assistant.sendspin.api.PlayerState
import io.music_assistant.sendspin.api.SendspinDeps
import io.music_assistant.sendspin.api.StopCause
import io.music_assistant.sendspin.api.WarningCode
import io.music_assistant.sendspin.fakes.FakeDecoderFactory
import io.music_assistant.sendspin.fakes.FakeNoiseServer
import io.music_assistant.sendspin.fakes.FakeSink
import io.music_assistant.sendspin.fakes.FakeTransport
import io.music_assistant.sendspin.identity.FakeSendspinKeyStore
import io.music_assistant.sendspin.identity.SendspinTrustStore
import io.music_assistant.sendspin.noise.SendspinPsk
import io.music_assistant.sendspin.noise.crypto.CryptographyKotlinNoiseCrypto
import io.music_assistant.sendspin.noise.crypto.X25519KeyPair
import io.music_assistant.sendspin.transport.TransportConnector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import io.music_assistant.sendspin.api.SendspinPlayer as SendspinPlayerApi

/**
 * Whole-module scenarios through the public API: the player comes from the
 * root factory, the server is a [FakeNoiseServer] that speaks the spec's JSON
 * by hand, and every port is a fake. Two things reach past the public surface:
 * unpaired access and long-term records are management-only settings with no
 * app port, so the harness seeds them through the trust store; and the
 * connector-close test needs the connector seam on [SendspinPlayerImpl].
 *
 * Known gap: the WebSocket endpoint with proxy auth cannot be driven here
 * (Ktor's MockEngine has no WebSocket support); `ProxyAuthTest` covers the
 * pure auth exchange, and the WebRTC endpoint stands in for the transport.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SendspinPlayerTest {
    private inner class Harness(
        val scope: TestScope,
        private val unpairedAccess: Boolean,
        private val sharedPsk: ByteArray?,
        private val create: Harness.(StateFlow<LocalPlayerConfig?>, SendspinDeps, CoroutineScope) -> SendspinPlayerApi,
    ) {
        val crypto = CryptographyKotlinNoiseCrypto()
        val keyStore = FakeSendspinKeyStore()
        val clock = MonotonicClock { scope.currentTime * 1_000 }
        val sink = FakeSink { clock.nowMicros() }
        val transports = Channel<FakeTransport>(Channel.UNLIMITED)
        val handedOut = mutableListOf<FakeTransport>()
        val endpoint = Endpoint.WebRtc {
            FakeTransport().also {
                handedOut += it
                transports.trySend(it)
            }
        }
        val config = MutableStateFlow<LocalPlayerConfig?>(
            LocalPlayerConfig(
                endpoint,
                "Device",
                listOf(AudioCodec.FLAC),
                bufferCapacityBytes = 10_000_000,
                userDelayMs = 0,
            ),
        )
        val online = MutableStateFlow(true)
        val events = Channel<PlayerEvent>(Channel.UNLIMITED)
        var pairCalls = 0
        var connectorsClosed = 0
        val httpClient = HttpClient(MockEngine { respond("") })
        lateinit var serverStatic: X25519KeyPair
        lateinit var player: SendspinPlayerApi

        suspend fun start() {
            serverStatic = crypto.generateX25519KeyPair()
            // The player loads its identity and settings from the same key store.
            val store = SendspinTrustStore.load(keyStore, crypto)
            store.setUnpairedAccessEnabled(unpairedAccess)
            sharedPsk?.let(store::addSharedRecord)
            val deps = SendspinDeps(
                sink = sink,
                decoders = FakeDecoderFactory(),
                keyStore = keyStore,
                httpClient = httpClient,
                online = online,
                approvePairing = { pairCalls++ },
                audioDispatcher = StandardTestDispatcher(scope.testScheduler),
                clock = clock,
            )
            player = create(config, deps, scope.backgroundScope)
            scope.backgroundScope.launch { player.events.collect { events.trySend(it) } }
            scope.runCurrent()
        }

        suspend fun nextTransport(): FakeTransport = withTimeout(AWAIT_MILLIS) { transports.receive() }

        suspend fun nextEvent(): PlayerEvent = withTimeout(AWAIT_MILLIS) { events.receive() }

        /** Brings a server up on the next transport and answers its probes. */
        suspend fun connectServer(
            psk: ByteArray = SendspinPsk.SENTINEL_PSK,
            serverClockOffsetMicros: Long = 0L,
            silent: Boolean = false,
        ): FakeNoiseServer {
            val server = FakeNoiseServer(crypto, nextTransport(), serverStatic, psk = psk)
            server.serverClockOffsetMicros = serverClockOffsetMicros
            server.silent = silent
            server.bringUp()
            server.serve(scope.backgroundScope)
            // Let the first probe burst complete so the clock is synced before streaming.
            scope.advanceTimeBy(1_500)
            scope.runCurrent()
            return server
        }

        suspend inline fun <reified T : PlayerState> awaitState(): T =
            withTimeout(AWAIT_MILLIS) { player.state.first { it is T } } as T

        suspend fun awaitConnected(): PlayerState.Connected = awaitState()

        suspend fun awaitReconnecting(): PlayerState.Reconnecting = awaitState()

        /** The live connection fails from the network side. */
        fun dropConnection() = handedOut.last().serverDrops(IllegalStateException("cable"))

        /** 48 kHz stereo 16-bit PCM of [millis], every byte set to [marker]. */
        fun pcm(millis: Int, marker: Byte = 1) = ByteArray(millis * 48 * 4) { marker }

        /**
         * Local time at which the device plays the first frame of the write whose
         * bytes are [marker]. The device plays writes back to back and idles when
         * nothing is queued, so a write starts at the later of its own time and
         * the end of the previous write.
         */
        fun playTimeOf(marker: Byte): Long {
            val handle = sink.handles.single()
            var end = 0L
            handle.writes.forEachIndexed { index, bytes ->
                val start = maxOf(end, handle.writeTimes[index])
                if (bytes.isNotEmpty() && bytes[0] == marker) return start
                end = start + bytes.size / handle.format.bytesPerFrame * 1_000_000L / handle.format.sampleRate
            }
            error("no write with marker $marker")
        }
    }

    private fun playerTest(
        unpairedAccess: Boolean = true,
        sharedPsk: ByteArray? = null,
        create: Harness.(StateFlow<LocalPlayerConfig?>, SendspinDeps, CoroutineScope) -> SendspinPlayerApi =
            { config, deps, scope -> SendspinPlayer(config, deps, scope) },
        block: suspend TestScope.(Harness) -> Unit,
    ) = runTest {
        val h = Harness(this, unpairedAccess, sharedPsk, create)
        h.start()
        block(h)
    }

    private fun assertWithin(expected: Long, actual: Long, toleranceMicros: Long, what: String) =
        assertTrue(abs(actual - expected) <= toleranceMicros, "$what: expected $expected, was $actual")

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
        assertEquals(0, h.awaitReconnecting().attempt)
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
        assertEquals(listOf<String?>("user_request"), server2.goodbyeReasons)
        assertTrue(h.sink.handles.single().closed)
    }

    @Test
    fun eachEnabledLifetimeClosesItsConnectorAndLeavesTheAppClientUsable() = playerTest(
        create = { config, deps, scope ->
            SendspinPlayerImpl(config, deps, scope, crypto) { client ->
                check(client === httpClient) { "the connector derives from the app's client" }
                TransportConnector({ error("WebSocket not used here") }, release = { connectorsClosed++ })
            }
        },
    ) { h ->
        val enabled = h.config.value
        repeat(2) { round ->
            h.connectServer()
            h.awaitConnected()
            h.config.value = null
            runCurrent()
            assertEquals(PlayerState.Disabled, h.player.state.value)
            assertEquals(round + 1, h.connectorsClosed, "one connector closed per lifetime")
            h.config.value = enabled
            runCurrent()
        }
        h.httpClient.get("http://ma.local/still-open")
    }

    @Test
    fun chunksPlayInTimestampOrderAtServerTimeMinusOffsetDespiteAHugeServerClock() = playerTest { h ->
        val offset = 3_450_000_000_000L
        val server = h.connectServer(serverClockOffsetMicros = offset)
        h.awaitConnected()
        h.nextEvent()
        server.startStream()
        val localStart = currentTime * 1_000
        val serverStart = localStart + offset + 300_000
        // Out of order on the wire; each chunk is 10 ms, spaced 100 ms apart.
        for (i in listOf(3, 1, 4, 0, 2)) {
            server.sendAudio(4, serverStart + i * 100_000L, h.pcm(10, marker = (i + 1).toByte()))
        }
        advanceTimeBy(1_000)
        runCurrent()

        val markers = h.sink.handles.single().writes.filter { it.isNotEmpty() && it[0] != 0.toByte() }.map { it[0] }
        assertEquals(listOf<Byte>(1, 2, 3, 4, 5), markers, "played in timestamp order")
        for (i in 0..4) {
            val expected = localStart + 300_000 + i * 100_000L
            assertWithin(expected, h.playTimeOf((i + 1).toByte()), 5_000, "chunk $i")
        }
    }

    @Test
    fun userDelayChangeShiftsLaterWrites() = playerTest { h ->
        val server = h.connectServer()
        h.awaitConnected()
        h.nextEvent()
        server.startStream()
        val start = currentTime * 1_000
        server.sendAudio(4, start + 300_000, h.pcm(10, marker = 1))
        advanceTimeBy(150)
        runCurrent()
        h.config.value = h.config.value!!.copy(userDelayMs = 500)
        runCurrent()
        server.sendAudio(4, start + 400_000, h.pcm(10, marker = 2))
        advanceTimeBy(1_500)
        runCurrent()

        assertWithin(start + 300_000, h.playTimeOf(1), 5_000, "before the change")
        assertWithin(start + 900_000, h.playTimeOf(2), 5_000, "after the change")
    }

    @Test
    fun reconnectBacksOffExponentiallyWithJitterAndOpensOneChannelPerAttempt() = playerTest { h ->
        for (attempt in 0 until 3) {
            h.connectServer()
            h.awaitConnected()
            assertEquals(attempt + 1, h.handedOut.size, "one openChannel per attempt")
            h.dropConnection()
            val reconnecting = h.awaitReconnecting()
            assertEquals(attempt, reconnecting.attempt)
            val delay = reconnecting.nextRetryAtMs!! - currentTime
            val base = 1_000L shl attempt
            assertTrue(delay in (base * 0.8).toLong()..(base * 1.2).toLong(), "attempt $attempt delay $delay")
        }
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
        h.awaitReconnecting()
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
    fun streamEndStopsWithServerEnded() = playerTest { h ->
        val server = h.connectServer()
        h.awaitConnected()
        h.nextEvent()
        server.startStream()
        server.sendAudio(4, currentTime * 1_000, h.pcm(10))
        runCurrent()
        assertIs<PlayerEvent.PlaybackStarted>(h.nextEvent())

        server.endStream()
        runCurrent()
        assertEquals(PlayerEvent.PlaybackStopped(StopCause.ServerEnded), h.nextEvent())
        assertEquals(AudioPhase.Idle, (h.player.state.value as PlayerState.Connected).audio.phase)
    }

    @Test
    fun streamClearFlushesAndTheNextAudioStartsPlaybackAgain() = playerTest { h ->
        val server = h.connectServer()
        h.awaitConnected()
        h.nextEvent()
        server.startStream()
        server.sendAudio(4, currentTime * 1_000, h.pcm(10, marker = 1))
        runCurrent()
        assertIs<PlayerEvent.PlaybackStarted>(h.nextEvent())

        server.clearStream()
        runCurrent()
        assertEquals(PlayerEvent.PlaybackStopped(StopCause.Cleared), h.nextEvent())
        server.sendAudio(4, currentTime * 1_000 + 100_000, h.pcm(10, marker = 2))
        advanceTimeBy(200)
        runCurrent()
        assertIs<PlayerEvent.PlaybackStarted>(h.nextEvent())
        val handle = h.sink.handles.single()
        assertEquals(1, handle.flushes, "clear flushes the same sink")
        assertEquals(2.toByte(), handle.writes.last()[0])
    }

    @Test
    fun serverUnpairIsAGoodbyeAndRepeatedUnpairsEndInFailedUnpaired() = playerTest(sharedPsk = SHARED_PSK) { h ->
        repeat(5) { i ->
            val server = h.connectServer(psk = SHARED_PSK)
            h.awaitConnected()
            server.unpair()
            runCurrent()
            assertEquals(listOf<String?>("unpaired"), server.goodbyeReasons, "attempt $i")
            if (i < 4) assertIs<PlayerState.Reconnecting>(h.player.state.value, "attempt $i retries")
        }
        assertEquals(PlayerState.Failed(FailureCause.Unpaired), h.awaitState<PlayerState.Failed>())
        assertEquals(0, h.pairCalls, "a paired session never asks for silent pairing")
    }

    @Test
    fun fivePairingRequiredRejectionsEndInFailedUnauthorized() = playerTest(unpairedAccess = false) { h ->
        repeat(5) { i ->
            val server = FakeNoiseServer(h.crypto, h.nextTransport(), h.serverStatic)
            server.establish()
            server.completeHelloExchange()
            server.activate()
            assertEquals("pairing_required", server.receiveGoodbyeReason(), "attempt $i")
            runCurrent()
            if (i < 4) assertIs<PlayerState.Reconnecting>(h.player.state.value, "attempt $i retries")
        }
        assertEquals(PlayerState.Failed(FailureCause.Unauthorized), h.player.state.value)
        assertTrue(h.pairCalls >= 1, "silent pairing was requested")
        advanceTimeBy(600_000)
        runCurrent()
        assertEquals(5, h.handedOut.size, "no attempts after Failed")
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
        assertEquals(listOf<String?>("restart"), server.goodbyeReasons, "warm goodbye on restart")
        val server2 = h.connectServer()
        h.awaitConnected()
        assertEquals(2, h.handedOut.size, "exactly one new connection")
        assertEquals(1, h.sink.handles.size, "pipeline survives a connection restart")
        assertFalse(h.sink.handles.single().closed)
        server2.startStream()
        runCurrent()
        assertEquals(1, h.sink.handles.size, "same format on the new connection resumes")
    }

    @Test
    fun offlineWaitsForTheNetworkAndOnlineRetriesAtOnce() = playerTest { h ->
        h.connectServer()
        h.awaitConnected()
        h.online.value = false
        h.dropConnection()
        assertNull(h.awaitReconnecting().nextRetryAtMs, "no retry scheduled while offline")
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(1, h.handedOut.size, "no attempts while offline")

        h.online.value = true
        runCurrent()
        assertEquals(2, h.handedOut.size, "retries as soon as the network is back")
        assertIs<PlayerState.Connecting>(h.player.state.value)
    }

    @Test
    fun aServerSilentOnClientTimeIsDroppedAfterThreeBursts() = playerTest { h ->
        h.connectServer(silent = true) // 1.5 s: the first burst goes unanswered
        h.awaitConnected()
        advanceTimeBy(3_500) // 5.0 s: two silent bursts
        runCurrent()
        assertIs<PlayerState.Connected>(h.player.state.value, "two silent bursts are tolerated")
        advanceTimeBy(3_500) // 8.5 s: the third ends the attempt
        runCurrent()
        assertIs<PlayerState.Reconnecting>(h.player.state.value)
        assertEquals(1, h.handedOut.size)

        h.connectServer()
        h.awaitConnected()
        assertEquals(2, h.handedOut.size, "reconnected once the server answers")
    }

    @Test
    fun unsupportedCodecWarnsAndOpensNoSink() = playerTest { h ->
        val server = h.connectServer()
        h.awaitConnected()
        h.nextEvent()
        server.startStream(codec = "opus")
        server.sendAudio(4, currentTime * 1_000, h.pcm(10))
        runCurrent()
        assertEquals(PlayerEvent.Warning(WarningCode.UnsupportedFormat("opus")), h.nextEvent())
        assertTrue(h.sink.handles.isEmpty(), "no sink for a stream we cannot decode")
        assertEquals(AudioPhase.Idle, (h.player.state.value as PlayerState.Connected).audio.phase)
    }

    private companion object {
        /** Above the reconnect cap, so a wait spans any backoff. */
        const val AWAIT_MILLIS = 40_000L
        val SHARED_PSK = ByteArray(32) { 7 }
    }
}
