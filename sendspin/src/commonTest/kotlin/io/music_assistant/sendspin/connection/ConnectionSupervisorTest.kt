package io.music_assistant.sendspin.connection

import io.music_assistant.sendspin.api.Endpoint
import io.music_assistant.sendspin.api.FailureCause
import io.music_assistant.sendspin.api.MonotonicClock
import io.music_assistant.sendspin.fakes.FakeNoiseServer
import io.music_assistant.sendspin.fakes.FakeTransport
import io.music_assistant.sendspin.identity.FakeSendspinKeyStore
import io.music_assistant.sendspin.identity.SendspinTrustStore
import io.music_assistant.sendspin.noise.crypto.CryptographyKotlinNoiseCrypto
import io.music_assistant.sendspin.noise.crypto.X25519KeyPair
import io.music_assistant.sendspin.session.Activation
import io.music_assistant.sendspin.session.NoiseSession
import io.music_assistant.sendspin.session.SessionConfig
import io.music_assistant.sendspin.session.SessionHandler
import io.music_assistant.sendspin.session.SessionInfo
import io.music_assistant.sendspin.transport.TransportConnector
import io.music_assistant.sendspin.wire.AudioChunk
import io.music_assistant.sendspin.wire.ServerMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionSupervisorTest {
    private val crypto = CryptographyKotlinNoiseCrypto()
    private val online = MutableStateFlow(true)
    private val sessionConfig = SessionConfig("Device", playerSupport = null, deviceInfo = null)
    private var pairCalls = 0
    private val noopHandler = object : SessionHandler {
        override fun onReady(info: SessionInfo) = Unit
        override fun onActivated(activation: Activation) = Unit
        override suspend fun onMessage(message: ServerMessage) = Unit
        override fun onAudio(chunk: AudioChunk) = Unit
    }

    private class Harness(
        val trustStore: SendspinTrustStore,
        val serverStatic: X25519KeyPair,
        val supervisor: ConnectionSupervisor,
        val transports: MutableList<FakeTransport>,
        val states: MutableList<Pair<ConnectionState, Long>>,
        val job: Job,
    ) {
        /** Every ConnectionState reached, with the virtual time it was reached at. */
        fun statesOf(type: (ConnectionState) -> Boolean) = states.filter { type(it.first) }
    }

    private suspend fun TestScope.harness(
        unpairedAccess: Boolean = true,
        endpoint: Endpoint? = null,
        connect: suspend (attempt: Int) -> FakeTransport = { FakeTransport() },
        companion: suspend CoroutineScope.(NoiseSession) -> Unit = { awaitCancellation() },
    ): Harness {
        val trustStore = SendspinTrustStore.load(FakeSendspinKeyStore(), crypto)
        trustStore.setUnpairedAccessEnabled(unpairedAccess)
        val transports = mutableListOf<FakeTransport>()
        var attempts = 0
        val connector = TransportConnector { _ -> connect(attempts++).also { transports += it } }
        val clock = MonotonicClock { testScheduler.currentTime * 1_000 }
        val supervisor = ConnectionSupervisor(
            connector,
            trustStore,
            crypto,
            online,
            clock,
            pairWebPlayer = { pairCalls++ },
            random = Random(42),
        )
        val states = mutableListOf<Pair<ConnectionState, Long>>()
        val job = launch {
            launch { supervisor.state.collect { states += it to testScheduler.currentTime } }
            supervisor.run(
                endpoint ?: Endpoint.WebRtc { connect(attempts++).also { transports += it } },
                sessionConfig,
                noopHandler,
                companion = companion,
            )
        }
        runCurrent()
        return Harness(trustStore, crypto.generateX25519KeyPair(), supervisor, transports, states, job)
    }

    private fun Harness.server(transport: FakeTransport) =
        FakeNoiseServer(crypto, transport, serverStatic, trustStore.identity.keyPair.publicKey)

    @Test
    fun connectFailuresBackOffExponentiallyAndCap() = runTest {
        val h = harness(connect = { error("refused") })
        advanceTimeBy(200_000)
        h.job.cancel()

        val backoffs = h.statesOf { it is ConnectionState.Backoff }.map { it.first as ConnectionState.Backoff }
        assertTrue(backoffs.size >= 8)
        backoffs.take(5).forEachIndexed { i, b ->
            assertEquals(i, b.attempt)
            val delayMs = (b.retryAtMicros / 1_000) - h.states.first { it.first == b }.second
            val base = 1_000L shl i
            assertTrue(delayMs in (base * 0.8).toLong()..(base * 1.2).toLong(), "attempt $i delay $delayMs")
        }
        assertTrue(backoffs.drop(6).all { (it.reason is DropReason.ConnectFailed) })
        val lateDelay = (backoffs[7].retryAtMicros / 1_000) - h.states.first { it.first == backoffs[7] }.second
        assertTrue(lateDelay in 24_000L..36_000L, "capped: $lateDelay")
    }

    @Test
    fun offlineWaitsForNetworkThenRetriesAtOnce() = runTest {
        var attempts = 0
        val h = harness(connect = {
            attempts++
        error("refused")
        })
        online.value = false
        advanceTimeBy(5_000)
        assertIs<ConnectionState.WaitingForNetwork>(h.supervisor.state.value)
        val before = attempts
        advanceTimeBy(60_000)
        assertEquals(before, attempts, "no attempts while offline")

        online.value = true
        runCurrent()
        assertEquals(before + 1, attempts, "retries as soon as the network is back")
        h.job.cancel()
    }

    @Test
    fun stableActiveSessionResetsTheAttemptCounter() = runTest {
        var attempt = 0
        val h = harness(connect = { if (attempt++ < 3) error("refused") else FakeTransport() })
        // Never advanceUntilIdle() against the supervisor: it always schedules the next retry.
        h.supervisor.state.first { it is ConnectionState.Connecting && it.attempt == 3 }
        runCurrent()
        assertEquals(3, h.statesOf { it is ConnectionState.Backoff }.size)

        val server = h.server(h.transports.last())
        server.bringUp()
        runCurrent()
        val active = assertIs<ConnectionState.Active>(h.supervisor.state.value)
        assertTrue(active.activated)
        assertEquals(1, pairCalls, "sentinel session requested silent pairing")

        advanceTimeBy(ReconnectPolicy.STABLE_ACTIVE_MILLIS + 1)
        h.transports.last().serverDrops(IllegalStateException("cable"))
        runCurrent()
        val backoff = assertIs<ConnectionState.Backoff>(h.supervisor.state.value)
        assertEquals(0, backoff.attempt, "counter reset after a stable session")
        assertIs<DropReason.Lost>(backoff.reason)
        h.job.cancel()
    }

    @Test
    fun repeatedRejectionsEndInFailed() = runTest {
        val h = harness(unpairedAccess = false)
        repeat(ReconnectPolicy.MAX_CONSECUTIVE_REJECTIONS) { i ->
            h.supervisor.state.first { it is ConnectionState.Connecting && it.attempt == i }
            runCurrent()
            val server = h.server(h.transports.last())
            server.establish()
            server.completeHelloExchange()
            server.activate() // playback on a sentinel without unpaired access: pairing_required
            server.receiveJson() // client/goodbye
            runCurrent()
        }
        assertEquals(ConnectionState.Failed(FailureCause.Unauthorized), h.supervisor.state.value)
        assertEquals(5, h.transports.size)
        advanceTimeBy(600_000)
        assertEquals(5, h.transports.size, "no attempts after Failed")
        h.job.cancel()
    }

    @Test
    fun webRtcEndpointOpensAFreshChannelPerAttempt() = runTest {
        var opened = 0
        val endpoint = Endpoint.WebRtc {
            opened++
        FakeTransport().also { it.serverDrops() }
        }
        val h = harness(endpoint = endpoint)
        advanceTimeBy(20_000)
        h.job.cancel()
        assertTrue(opened >= 4, "opened $opened channels")
        assertEquals(opened, h.statesOf { it is ConnectionState.Connecting }.size)
    }

    @Test
    fun companionFailureEndsTheAttemptAndClosesTheTransport() = runTest {
        val h = harness(companion = {
            delay(500)
        throw ServerSilentException()
        })
        runCurrent()
        val transport = h.transports.single()
        advanceTimeBy(600)
        val backoff = assertIs<ConnectionState.Backoff>(h.supervisor.state.value)
        assertEquals(DropReason.Silent, backoff.reason)
        assertTrue(transport.closed)
        h.job.cancel()
    }

    @Test
    fun cancellingRunLeavesIdleStateAndNoChildren() = runTest {
        val h = harness()
        runCurrent()
        h.server(h.transports.single()).bringUp()
        runCurrent()
        assertIs<ConnectionState.Active>(h.supervisor.state.value)
        h.job.cancel()
        runCurrent()
        assertEquals(ConnectionState.Idle, h.supervisor.state.value)
        assertTrue(h.transports.single().closed)
    }
}
