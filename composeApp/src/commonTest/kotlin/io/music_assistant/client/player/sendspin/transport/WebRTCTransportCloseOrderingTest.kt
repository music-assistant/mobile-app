package io.music_assistant.client.player.sendspin.transport

import io.music_assistant.client.webrtc.DataChannelInbound
import io.music_assistant.client.webrtc.DataChannelReceiveSource
import io.music_assistant.client.webrtc.DataChannelState
import io.music_assistant.client.webrtc.DataChannelWrapper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * The channel-closed signal must be produced by the same coroutine that pumps
 * frames, so frames already buffered when the channel closes are delivered
 * before Disconnected — a separate state watcher could race past them and the
 * session would drop the tail of the stream as post-epoch frames.
 */
class WebRTCTransportCloseOrderingTest {
    private class ScriptedReceiveSource : DataChannelReceiveSource {
        val script = Channel<DataChannelInbound>(Channel.UNLIMITED)

        override suspend fun receive(): DataChannelInbound =
            script.receiveCatching().getOrNull() ?: throw CancellationException("source closed")
    }

    @Test
    fun bufferedFramesAreDeliveredBeforeTheDisconnectedSignal() = runTest {
        withContext(Dispatchers.Default) {
            val source = ScriptedReceiveSource()
            val wrapper = DataChannelWrapper(
                dataChannel = null,
                connectionEvents = null,
                receiveSource = source,
                initialState = DataChannelState.Open,
                label = "sendspin",
            )
            val transport = WebRTCDataChannelTransport(wrapper)

            // Frames buffered and the source closed before anything consumes them.
            source.script.trySend(DataChannelInbound.Text("""{"type":"server/hello"}"""))
            source.script.trySend(DataChannelInbound.Binary(byteArrayOf(4, 1, 2, 3)))
            source.script.close()

            val events = transport.events.produceIn(this)
            transport.connect()

            assertIs<InboundTransportEvent.Connected>(withTimeout(5_000) { events.receive() })
            assertEquals(
                """{"type":"server/hello"}""",
                assertIs<InboundTransportEvent.Text>(withTimeout(5_000) { events.receive() }).text,
            )
            assertContentEquals(
                byteArrayOf(4, 1, 2, 3),
                assertIs<InboundTransportEvent.Binary>(withTimeout(5_000) { events.receive() }).bytes,
            )
            assertIs<InboundTransportEvent.Disconnected>(withTimeout(5_000) { events.receive() })
            events.cancel()
            transport.close()
        }
    }
}
