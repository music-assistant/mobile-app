package io.music_assistant.client.data.share

import io.music_assistant.client.api.ErrorMessageBus
import io.music_assistant.client.data.model.client.QueueOption
import io.music_assistant.client.data.play.PlayRequest
import io.music_assistant.sendspin.api.AudioCapture
import io.music_assistant.sendspin.api.AudioStatus
import io.music_assistant.sendspin.api.CaptureFormat
import io.music_assistant.sendspin.api.CapturedAudio
import io.music_assistant.sendspin.api.ClockQuality
import io.music_assistant.sendspin.api.Endpoint
import io.music_assistant.sendspin.api.PlayerState
import io.music_assistant.sendspin.api.SourceDialect
import io.music_assistant.sendspin.api.SourceStatus
import io.music_assistant.sendspin.api.WireVersion
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceAudioRouterTest {
    private object Capture : AudioCapture {
        override val format = CaptureFormat(sampleRate = 48_000, channels = 2, bitDepth = 16)
        override fun frames(): Flow<CapturedAudio> = emptyFlow()
    }

    private class FakeShare(override val isSupported: Boolean = true) : DeviceAudioShare {
        override val capture = MutableStateFlow<AudioCapture?>(null)
        override var endedBySystem = false
        var stops = 0

        override fun stop() {
            stops++
            capture.value = null
        }

        fun revoke() {
            endedBySystem = true
            capture.value = null
        }
    }

    private class Harness(scope: TestScope, wire: WireVersion = WireVersion.SPEC_1_0, instanceId: String? = "sendspin") {
        val share = FakeShare()
        val source = DeviceAudioSource(share, MutableStateFlow(instanceId))
        val player = MutableStateFlow<PlayerState>(connected(SourceStatus.Off))
        val plays = mutableListOf<PlayRequest>()

        /** What the server answers when asked for the source uri; null is a server that cannot tell. */
        var listed: Boolean? = true

        /** Each released player, with whether it was told to stop first. */
        val released = mutableListOf<Pair<String, Boolean>>()
        val errors = ErrorMessageBus()
        val router = DeviceAudioRouter(
            share = share,
            source = source,
            playerState = player,
            endpoint = MutableStateFlow(Endpoint.WebSocket("ws://server/sendspin", wire)),
            isListed = { listed },
            play = { plays += it },
            releasePlayer = { playerId, stop -> released += playerId to stop },
            errorBus = errors,
            scope = scope.backgroundScope,
            describe = { it.key },
        )

        fun status(status: SourceStatus) {
            player.value = connected(status)
        }

        /** The platform capture runs and the user picked the kitchen. */
        fun startShare() {
            share.capture.value = Capture
            router.route("kitchen")
        }
    }

    private companion object {
        fun connected(source: SourceStatus) =
            PlayerState.Connected("device", "Server", ClockQuality.Good, AudioStatus.IDLE, source)
    }

    @Test
    fun `the play request waits until the server lists the source`() = runTest {
        val h = Harness(this)
        h.listed = false
        h.startShare()
        h.status(SourceStatus.Ready)
        advanceTimeBy(1_000)
        runCurrent()
        assertTrue(h.plays.isEmpty(), "the server rejects a source that it does not list")

        h.listed = true
        advanceTimeBy(300)
        runCurrent()
        assertEquals(1, h.plays.size)
    }

    @Test
    fun `a source that the server never lists ends the share without a play request`() = runTest {
        val h = Harness(this)
        h.listed = false
        h.startShare()
        h.status(SourceStatus.Ready)
        advanceTimeBy(6_000)
        runCurrent()
        assertTrue(h.plays.isEmpty())
        assertEquals(DeviceAudioShareState.Idle, h.router.state.value)
    }

    @Test
    fun `a server that cannot tell gets the play request at once`() = runTest {
        val h = Harness(this)
        h.listed = null
        h.startShare()
        h.status(SourceStatus.Ready)
        runCurrent()
        assertEquals(1, h.plays.size)
    }

    @Test
    fun `a share plays the source uri on the target once the server accepts the source`() = runTest {
        val h = Harness(this)
        runCurrent()
        assertTrue(h.router.isAvailable.value)

        h.startShare()
        runCurrent()
        assertEquals(DeviceAudioShareState.Starting("kitchen"), h.router.state.value)
        assertTrue(h.plays.isEmpty(), "only an accepted source can play")

        h.status(SourceStatus.Ready)
        runCurrent()
        assertEquals(
            listOf(PlayRequest("kitchen", listOf("sendspin://audio_source/device"), QueueOption.REPLACE)),
            h.plays,
        )

        h.status(SourceStatus.Streaming)
        runCurrent()
        assertEquals(DeviceAudioShareState.Active, h.router.state.value)
        assertEquals(SourceDialect.SPEC, h.source.dialect.value)
    }

    @Test
    fun `a refusal retries with the rc1 dialect and keeps it`() = runTest {
        val h = Harness(this)
        h.startShare()
        runCurrent()
        h.status(SourceStatus.Refused)
        runCurrent()
        assertEquals(SourceDialect.RC1, h.source.dialect.value)
        assertTrue(h.plays.isEmpty())

        // The audio link restarts with the new hello.
        h.player.value = PlayerState.Connecting(0)
        runCurrent()
        h.status(SourceStatus.Ready)
        runCurrent()
        assertEquals(1, h.plays.size)
        assertEquals(SourceDialect.RC1, h.source.dialect.value)
    }

    @Test
    fun `a refusal of both dialects ends the share`() = runTest {
        val h = Harness(this)
        h.startShare()
        runCurrent()
        h.status(SourceStatus.Refused)
        runCurrent()
        h.player.value = PlayerState.Connecting(0)
        runCurrent()
        h.status(SourceStatus.Refused)
        runCurrent()

        assertEquals(DeviceAudioShareState.Idle, h.router.state.value)
        assertEquals(1, h.share.stops)
        assertNull(h.share.capture.value)
        assertEquals(SourceDialect.SPEC, h.source.dialect.value)
        assertEquals("device_audio_end_refused", h.errors.messages.first())
    }

    @Test
    fun `a source that the server never answers ends the share`() = runTest {
        val h = Harness(this)
        h.startShare()
        advanceTimeBy(15_001)
        runCurrent()

        assertEquals(DeviceAudioShareState.Idle, h.router.state.value)
        assertEquals("device_audio_end_timeout", h.errors.messages.first())
    }

    @Test
    fun `a stream that the server stops ends the share`() = runTest {
        val h = Harness(this)
        h.startShare()
        h.status(SourceStatus.Ready)
        runCurrent()
        h.status(SourceStatus.Streaming)
        runCurrent()
        h.status(SourceStatus.Ready)
        runCurrent()

        assertEquals(DeviceAudioShareState.Idle, h.router.state.value)
        assertEquals("device_audio_end_server_stopped", h.errors.messages.first())
        assertEquals(listOf("kitchen" to false), h.released, "no stop: the server stopped the player already")
    }

    @Test
    fun `a dropped audio link ends the share`() = runTest {
        val h = Harness(this)
        h.startShare()
        h.status(SourceStatus.Ready)
        runCurrent()
        h.status(SourceStatus.Streaming)
        runCurrent()
        h.player.value = PlayerState.Reconnecting(0, null, AudioStatus.IDLE)
        runCurrent()

        assertEquals(DeviceAudioShareState.Idle, h.router.state.value)
        assertEquals("device_audio_end_connection_lost", h.errors.messages.first())
        assertEquals(listOf("kitchen" to true), h.released)
    }

    @Test
    fun `a capture that the system ends tells the user`() = runTest {
        val h = Harness(this)
        h.startShare()
        runCurrent()
        h.share.revoke()
        runCurrent()

        assertEquals(DeviceAudioShareState.Idle, h.router.state.value)
        assertEquals("device_audio_end_revoked", h.errors.messages.first())
        assertTrue(h.released.isEmpty(), "no play request went out, so no player plays the device")
    }

    @Test
    fun `a stop by the user is silent`() = runTest {
        val h = Harness(this)
        h.startShare()
        h.status(SourceStatus.Ready)
        runCurrent()
        h.router.stop()
        runCurrent()
        h.status(SourceStatus.Off)
        runCurrent()

        assertEquals(DeviceAudioShareState.Idle, h.router.state.value)
        assertEquals(1, h.share.stops)
        assertEquals(listOf("kitchen" to true), h.released, "the player must not wait in silence for the source")
        h.errors.emit("end of messages")
        assertEquals("end of messages", h.errors.messages.first())
    }

    @Test
    fun `no share on the 9x wire or without a source provider`() = runTest {
        val legacy = Harness(this, wire = WireVersion.LEGACY_9)
        val noProvider = Harness(this, instanceId = null)
        runCurrent()

        assertFalse(legacy.router.isAvailable.value)
        assertFalse(noProvider.router.isAvailable.value)
        noProvider.share.capture.value = Capture
        assertNull(noProvider.source.config.first(), "no source role without a provider")
    }
}
