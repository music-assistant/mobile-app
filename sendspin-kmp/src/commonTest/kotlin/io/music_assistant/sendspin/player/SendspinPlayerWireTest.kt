package io.music_assistant.sendspin.player

import io.music_assistant.sendspin.api.EncoderFactory
import io.music_assistant.sendspin.api.PlayerEvent
import io.music_assistant.sendspin.api.PlayerState
import io.music_assistant.sendspin.api.SourceConfig
import io.music_assistant.sendspin.api.SourceDialect
import io.music_assistant.sendspin.api.SourceStatus
import io.music_assistant.sendspin.api.WireVersion
import io.music_assistant.sendspin.fakes.FakeCapture
import io.music_assistant.sendspin.fakes.FakeEncoderFactory
import io.music_assistant.sendspin.fakes.FakeNoiseServer
import io.music_assistant.sendspin.wire.SendspinJson
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Every public-API scenario on the 9.x wire, plus its `client/state` cadence. */
@OptIn(ExperimentalCoroutinesApi::class)
internal class LegacySendspinPlayerTest : SendspinPlayerScenarios(WireVersion.LEGACY_9) {
    @Test
    fun stateIsSynchronizedOnceAfterActivationAndThenEveryTwoSecondsWhilePlaying() = playerTest { h ->
        val server = h.connectServer()
        h.awaitConnected()
        h.nextEvent()
        assertEquals(1, server.clientStates.size)
        assertEquals("synchronized", server.clientStates.single().player().getValue("state").jsonPrimitive.content)

        server.startStream()
        val start = currentTime * 1_000
        repeat(10) { i -> server.sendAudio(4, start + i * 500_000L, h.pcm(500)) }
        runCurrent()
        assertIs<PlayerEvent.PlaybackStarted>(h.nextEvent())
        advanceTimeBy(4_100)
        runCurrent()
        assertTrue(server.clientStates.size >= 3, "states: ${server.clientStates.size}")
    }

    @Test
    fun theSourceRoleIsNotOfferedOnThe9xWire() = playerTest(source = SourceConfig(FakeCapture())) { h ->
        val server = h.connectServer()
        assertEquals(SourceStatus.Off, h.awaitConnected().source)
        assertEquals(listOf("player@v1"), server.helloPayload().roles())
    }
}

/** Every public-API scenario on spec 1.0.0-rc1, plus its `client/state` shape and timing. */
@OptIn(ExperimentalCoroutinesApi::class)
internal class SpecSendspinPlayerTest : SendspinPlayerScenarios(WireVersion.SPEC_1_0) {
    @Test
    fun stateIsSentOnceTheClockIsSyncedAndAgainOnlyWhenTheDelayChanges() = playerTest { h ->
        val server = h.connectServer()
        h.awaitConnected()
        h.nextEvent()
        assertTrue(server.clientStates.isEmpty(), "one burst is not convergence")
        advanceTimeBy(1_000) // the second burst, 2 s after the first
        runCurrent()

        val first = server.clientStates.single()
        assertEquals("true", first.getValue("available").jsonPrimitive.content)
        val player = first.player()
        assertFalse(player.containsKey("state"))
        assertEquals(0, player.getValue("output_delay_ms").jsonPrimitive.content.toInt())
        assertEquals(
            SendspinPlayerImpl.REQUIRED_LEAD_TIME_MS,
            player.getValue("required_lead_time_ms").jsonPrimitive.content.toInt(),
        )
        assertEquals(SendspinPlayerImpl.MIN_BUFFER_MS, player.getValue("min_buffer_ms").jsonPrimitive.content.toInt())
        assertEquals(0, player.getValue("supported_commands").jsonArray.size)

        h.config.value = h.config.value!!.copy(userDelayMs = 500)
        runCurrent()
        assertEquals(500, server.clientStates[1].player().getValue("output_delay_ms").jsonPrimitive.content.toInt())

        // A negative lag is local latency compensation; the wire range starts at 0.
        h.config.value = h.config.value!!.copy(userDelayMs = -200)
        runCurrent()
        assertEquals(0, server.clientStates[2].player().getValue("output_delay_ms").jsonPrimitive.content.toInt())

        server.startStream()
        val start = currentTime * 1_000
        repeat(10) { i -> server.sendAudio(4, start + i * 500_000L, h.pcm(500)) }
        runCurrent()
        assertIs<PlayerEvent.PlaybackStarted>(h.nextEvent())
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(3, server.clientStates.size, "no periodic resend on spec 1.0.0-rc1")
    }

    @Test
    fun noStateBeforeTheClockIsSynced() = playerTest { h ->
        val server = h.connectServer(silent = true)
        h.awaitConnected()
        advanceTimeBy(1_500)
        runCurrent()
        assertTrue(server.clientStates.isEmpty())
    }

    // --- source@v1 ---

    private fun sourceTest(
        dialect: SourceDialect = SourceDialect.SPEC,
        encoders: EncoderFactory? = null,
        block: suspend TestScope.(Harness, FakeCapture) -> Unit,
    ) {
        val capture = FakeCapture()
        playerTest(sharedPsk = SHARED_PSK, source = SourceConfig(capture, dialect), encoders = encoders) { h ->
            block(h, capture)
        }
    }

    /** A paired server that grants the source role, with the clock converged and the state reported. */
    private suspend fun TestScope.connectSource(
        h: Harness,
        activities: String = """["playback"]""",
        serverClockOffsetMicros: Long = 0L,
    ): FakeNoiseServer {
        val server = h.connectServer(
            psk = SHARED_PSK,
            serverClockOffsetMicros = serverClockOffsetMicros,
            activities = activities,
            activeRoles = """["player@v1","source@v1"]""",
        )
        advanceTimeBy(1_000)
        runCurrent()
        return server
    }

    private fun Harness.sourceStatus(): SourceStatus = (player.state.value as PlayerState.Connected).source

    @Test
    fun theSourceIsNotReadyBeforeTheStateReport() = sourceTest { h, _ ->
        val server = h.connectServer(psk = SHARED_PSK, activeRoles = """["player@v1","source@v1"]""")
        assertEquals(SourceStatus.Off, h.awaitConnected().source, "the server lists no source before client/state")
        assertTrue(server.clientStates.isEmpty())

        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(1, server.clientStates.size)
        assertEquals(SourceStatus.Ready, h.sourceStatus())
    }

    @Test
    fun theSourceRoleIsOfferedWithoutASupportObjectAndWaitsForAStart() = sourceTest { h, capture ->
        val server = connectSource(h)
        val hello = server.helloPayload()
        assertEquals(listOf("player@v1", "source@v1"), hello.roles())
        assertFalse(hello.containsKey("source@v1_support"), "the current spec has no source object in the hello")
        val source = server.clientStates.single().getValue("source").jsonObject
        assertEquals("present", source.getValue("signal").jsonPrimitive.content)
        assertEquals(SourceStatus.Ready, h.sourceStatus())

        capture.capture(currentTime * 1_000)
        runCurrent()
        assertEquals(0, capture.recordings, "only the server starts a stream")
        assertTrue(server.clientStreamStarts.isEmpty())
    }

    @Test
    fun aDialectChangeRestartsTheConnectionWithTheRc1SupportObject() = sourceTest { h, _ ->
        connectSource(h)
        h.config.value = h.config.value?.let { it.copy(source = it.source?.copy(dialect = SourceDialect.RC1)) }
        runCurrent()
        val server = connectSource(h)
        assertEquals(2, h.handedOut.size)
        val support = server.helloPayload().getValue("source@v1_support").jsonObject
        assertEquals("true", support.getValue("features").jsonObject.getValue("line_sense").jsonPrimitive.content)
    }

    @Test
    fun aStartStreamsPcmInServerTimeAndAStopEndsTheStream() = sourceTest { h, capture ->
        val server = connectSource(h, serverClockOffsetMicros = 5_000_000)
        server.sourceCommand("start")
        runCurrent()
        assertTrue(capture.recording)
        val capturedAt = currentTime * 1_000
        val pcm = capture.capture(capturedAt)
        runCurrent()

        val start = server.clientStreamStarts.single()
        assertEquals("pcm", start.codec())
        assertEquals(48_000, start.getValue("sample_rate").jsonPrimitive.content.toInt())
        assertEquals(2, start.getValue("channels").jsonPrimitive.content.toInt())
        assertEquals(16, start.getValue("bit_depth").jsonPrimitive.content.toInt())
        assertFalse(start.containsKey("codec_header"))
        val (timestamp, data) = server.sourceChunks.single()
        assertTrue(abs(timestamp - (capturedAt + 5_000_000)) <= 2, "server time, was $timestamp")
        assertContentEquals(pcm, data)
        assertEquals(SourceStatus.Streaming, h.sourceStatus())

        server.sourceCommand("start") // A duplicate start has no effect.
        runCurrent()
        assertEquals(1, capture.recordings)

        server.sourceCommand("stop")
        runCurrent()
        assertFalse(capture.recording)
        assertEquals("client-stream/end", server.clientMessageTypes.last())
        assertEquals(SourceStatus.Ready, h.sourceStatus())

        server.sourceCommand("start")
        runCurrent()
        capture.capture(currentTime * 1_000)
        runCurrent()
        assertEquals(2, server.clientStreamStarts.size, "a new start opens a new stream")
    }

    @Test
    fun aLongCaptureBlockIsCutIntoTwentyMillisecondChunks() = sourceTest { h, capture ->
        val server = connectSource(h)
        server.sourceCommand("start")
        runCurrent()
        val capturedAt = currentTime * 1_000
        capture.capture(capturedAt, millis = 50)
        runCurrent()
        assertEquals(listOf(capturedAt, capturedAt + 20_000), server.sourceChunks.map { it.first })
        assertTrue(server.sourceChunks.all { it.second.size == 20 * 48 * 4 })
    }

    @Test
    fun aStartWithoutThePlaybackActivityIsIgnored() = sourceTest { h, capture ->
        val server = connectSource(h, activities = "[]")
        server.sourceCommand("start")
        runCurrent()
        assertEquals(0, capture.recordings)
        assertFalse("client-stream/end" in server.clientMessageTypes)
    }

    @Test
    fun rc1ObeysAStartWithoutThePlaybackActivity() = sourceTest(SourceDialect.RC1) { h, capture ->
        val server = connectSource(h, activities = "[]")
        server.sourceCommand("start")
        runCurrent()
        capture.capture(currentTime * 1_000)
        runCurrent()
        assertEquals(1, server.sourceChunks.size)
    }

    @Test
    fun theStreamEndsWhenThePlaybackActivityGoes() = sourceTest { h, capture ->
        val server = connectSource(h)
        server.sourceCommand("start")
        runCurrent()
        capture.capture(currentTime * 1_000)
        runCurrent()
        server.activate(activities = "[]", activeRoles = null)
        runCurrent()
        assertFalse(capture.recording)
        assertEquals("client-stream/end", server.clientMessageTypes.last())
        assertEquals(SourceStatus.Ready, h.sourceStatus())
    }

    @Test
    fun theStreamEndsWhenTheRoleGoes() = sourceTest { h, capture ->
        val server = connectSource(h)
        server.sourceCommand("start")
        runCurrent()
        capture.capture(currentTime * 1_000)
        runCurrent()
        server.activate(activeRoles = """["player@v1"]""")
        runCurrent()
        assertFalse(capture.recording)
        assertEquals("client-stream/end", server.clientMessageTypes.last())
        assertEquals(SourceStatus.Refused, h.sourceStatus())
    }

    @Test
    fun aPairedServerThatLeavesTheRoleOutRefusesIt() = sourceTest { h, _ ->
        h.connectServer(psk = SHARED_PSK)
        assertEquals(SourceStatus.Refused, h.awaitConnected().source)
    }

    @Test
    fun anUnpairedSessionIsNotARefusal() = playerTest(source = SourceConfig(FakeCapture())) { h ->
        h.connectServer()
        assertEquals(SourceStatus.Off, h.awaitConnected().source)
    }

    @Test
    fun anInputThatEndsBeforeItGivesAudioDeclinesTheStart() = sourceTest { h, capture ->
        val server = connectSource(h)
        capture.end()
        server.sourceCommand("start")
        runCurrent()
        assertTrue(server.clientStreamStarts.isEmpty())
        assertEquals("client-stream/end", server.clientMessageTypes.last())
        assertEquals(SourceStatus.Ready, h.sourceStatus())
    }

    @Test
    fun rc1HasNoDecline() = sourceTest(SourceDialect.RC1) { h, capture ->
        val server = connectSource(h)
        capture.end()
        server.sourceCommand("start")
        runCurrent()
        assertFalse("client-stream/end" in server.clientMessageTypes)
    }

    @Test
    fun audioOlderThanTheBacklogLimitIsDropped() = sourceTest { h, capture ->
        val server = connectSource(h)
        server.sourceCommand("start")
        runCurrent()
        capture.capture(currentTime * 1_000 - 600_000)
        val live = capture.capture(currentTime * 1_000, marker = 2)
        runCurrent()
        assertContentEquals(live, server.sourceChunks.single().second)
    }

    @Test
    fun flacStreamsWithItsCodecHeader() = sourceTest(encoders = FakeEncoderFactory()) { h, capture ->
        val server = connectSource(h)
        server.sourceCommand("start")
        runCurrent()
        val pcm = capture.capture(currentTime * 1_000)
        runCurrent()
        val start = server.clientStreamStarts.single()
        assertEquals("flac", start.codec())
        assertContentEquals(
            FakeEncoderFactory.HEADER,
            Base64.decode(start.getValue("codec_header").jsonPrimitive.content),
        )
        assertContentEquals(pcm, server.sourceChunks.single().second)
    }

    @Test
    fun audioFromBeforeTheFlacHeaderIsKept() {
        val encoders = FakeEncoderFactory(headerAfter = 2)
        sourceTest(encoders = encoders) { h, capture -> audioFromBeforeTheHeader(h, capture) }
    }

    private suspend fun TestScope.audioFromBeforeTheHeader(h: Harness, capture: FakeCapture) {
        val server = connectSource(h)
        server.sourceCommand("start")
        runCurrent()
        capture.capture(currentTime * 1_000)
        runCurrent()
        assertTrue(server.clientStreamStarts.isEmpty(), "no stream before the header")
        capture.capture(currentTime * 1_000 + 20_000)
        runCurrent()
        assertEquals("flac", server.clientStreamStarts.single().codec())
        assertEquals(2, server.sourceChunks.size)
    }

    @Test
    fun aFlacFailureReplacesTheFormatWithPcmForThisAndLaterStreams() {
        val encoders = FakeEncoderFactory(failAt = 2)
        sourceTest(encoders = encoders) { h, capture ->
            val server = connectSource(h)
            server.sourceCommand("start")
            runCurrent()
            capture.capture(currentTime * 1_000)
            capture.capture(currentTime * 1_000 + 20_000)
            runCurrent()
            assertEquals(listOf("flac", "pcm"), server.clientStreamStarts.map { it.codec() })
            assertEquals(2, server.sourceChunks.size)
            assertTrue(encoders.created.single().released)
            assertFalse("client-stream/end" in server.clientMessageTypes, "the open stream gets a new format")

            server.sourceCommand("stop")
            server.sourceCommand("start")
            runCurrent()
            capture.capture(currentTime * 1_000)
            runCurrent()
            assertEquals("pcm", server.clientStreamStarts.last().codec())
            assertEquals(1, encoders.created.size, "no new FLAC encoder after a failure")
        }
    }

    @Test
    fun aFlacEncoderWithoutAHeaderGivesWayToPcm() {
        val encoders = FakeEncoderFactory(headerAfter = null)
        sourceTest(encoders = encoders) { h, capture ->
            val server = connectSource(h)
            server.sourceCommand("start")
            runCurrent()
            capture.capture(currentTime * 1_000)
            runCurrent()
            assertTrue(server.clientStreamStarts.isEmpty())
            advanceTimeBy(2_100)
            capture.capture(currentTime * 1_000)
            runCurrent()
            assertEquals("pcm", server.clientStreamStarts.single().codec())
            assertEquals(1, server.sourceChunks.size)
        }
    }
}

private fun JsonObject.player(): JsonObject = getValue("player").jsonObject

private fun FakeNoiseServer.helloPayload(): JsonObject =
    SendspinJson.parseToJsonElement(clientHello ?: "").jsonObject.getValue("payload").jsonObject

private fun JsonObject.codec(): String = getValue("codec").jsonPrimitive.content

private fun JsonObject.roles(): List<String> = getValue("supported_roles").jsonArray.map { it.jsonPrimitive.content }
