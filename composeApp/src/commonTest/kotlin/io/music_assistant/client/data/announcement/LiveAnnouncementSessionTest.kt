package io.music_assistant.client.data.announcement

import io.music_assistant.client.utils.myJson
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class LiveAnnouncementSessionTest {
    private class FakeLink : AnnouncementLink {
        val server = Channel<LinkInbound>(Channel.UNLIMITED)
        val sent = mutableListOf<Any>() // String for text, ByteArray for binary
        var closed = false

        override val inbound: Flow<LinkInbound> = server.consumeAsFlow()
        override suspend fun sendText(text: String) {
            sent += text
        }
        override suspend fun sendBinary(bytes: ByteArray) {
            sent += bytes
        }
        override suspend fun close() {
            closed = true
        }

        fun reply(type: String) = server.trySend(LinkInbound.Text("""{"type":"$type"}"""))
        fun types() = sent.map { (it as? String)?.let { text -> text.field("type") } ?: "audio" }
    }

    private fun TestScope.start(
        link: FakeLink,
        frames: Channel<ByteArray>,
        options: AnnouncementOptions = AnnouncementOptions(),
    ) = async {
        runLiveAnnouncement(link, "token-1", "player-1", 48_000, options, frames, handshakeTimeout = 5.seconds)
    }

    @Test
    fun `sends auth and start then holds audio until started then streams and stops`() = runTest {
        val link = FakeLink()
        val frames = Channel<ByteArray>(Channel.UNLIMITED)
        frames.trySend(byteArrayOf(1))
        val result = start(link, frames)
        runCurrent()
        assertEquals(listOf("auth", "start"), link.types())

        link.reply("started")
        frames.trySend(byteArrayOf(2))
        frames.close()
        runCurrent()
        assertEquals(listOf("auth", "start", "audio", "audio", "stop"), link.types())
        assertEquals(listOf(1.toByte(), 2.toByte()), link.sent.filterIsInstance<ByteArray>().map { it.single() })

        link.reply("finished")
        assertEquals(LiveAnnouncementOutcome.Finished, result.await())
        assertTrue(link.closed)
    }

    @Test
    fun `start carries the rate and only the chosen options`() = runTest {
        val link = FakeLink()
        val result = start(link, Channel(), AnnouncementOptions(volumeLevel = 30))
        runCurrent()

        val start = myJson.parseToJsonElement(link.sent[1] as String).jsonObject
        assertEquals("token-1", (link.sent[0] as String).field("token"))
        assertEquals(setOf("type", "player_id", "sample_rate", "channels", "volume_level"), start.keys)
        assertEquals(48_000, start.getValue("sample_rate").jsonPrimitive.content.toInt())
        link.server.trySend(LinkInbound.Closed(null, null))
        result.await()
    }

    @Test
    fun `server error carries its message`() = runTest {
        val link = FakeLink()
        val result = start(link, Channel())
        link.server.trySend(LinkInbound.Text("""{"type":"error","message":"Player is unavailable"}"""))

        assertEquals(LiveAnnouncementOutcome.Failed("Player is unavailable"), result.await())
    }

    @Test
    fun `rejection close carries its reason`() = runTest {
        val link = FakeLink()
        val result = start(link, Channel())
        link.server.trySend(LinkInbound.Closed(4001, "Not allowed to control this player"))

        assertEquals(LiveAnnouncementOutcome.Failed("Not allowed to control this player"), result.await())
    }

    @Test
    fun `microphone failure never sends stop`() = runTest {
        val link = FakeLink()
        val frames = Channel<ByteArray>(Channel.UNLIMITED)
        val result = start(link, frames)
        link.reply("started")
        frames.close(IllegalStateException("Microphone did not start"))

        assertEquals(LiveAnnouncementOutcome.Failed(null), result.await())
        assertTrue("stop" !in link.types())
    }

    @Test
    fun `no started reply in time fails without audio`() = runTest {
        val link = FakeLink()
        val frames = Channel<ByteArray>(Channel.UNLIMITED)
        frames.trySend(byteArrayOf(1))
        val result = start(link, frames)
        advanceTimeBy(6.seconds)

        assertEquals(LiveAnnouncementOutcome.Failed(null), result.await())
        assertEquals(listOf("auth", "start"), link.types())
    }
}

private fun String.field(name: String) = myJson.parseToJsonElement(this).jsonObject[name]?.jsonPrimitive?.content
