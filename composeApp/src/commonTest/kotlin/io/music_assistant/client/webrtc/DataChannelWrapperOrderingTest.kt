package io.music_assistant.client.webrtc

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/**
 * Locks in the wrapper's single-ordered-inbound-stream contract: mixed text
 * and binary messages are delivered in exact source order, losslessly, even
 * when the consumer lags behind the producer. The previous two-independent-
 * shared-flows design could neither preserve cross-kind order nor guarantee
 * delivery (bounded buffers dropped on overflow) — both properties are load-
 * bearing for the encrypted Sendspin protocol, where a dropped or reordered
 * control frame strands the handshake or activation state machine.
 */
class DataChannelWrapperOrderingTest {
    private class ScriptedReceiveSource : DataChannelReceiveSource {
        val script = Channel<DataChannelInbound>(Channel.UNLIMITED)

        override suspend fun receive(): DataChannelInbound =
            script.receiveCatching().getOrNull() ?: throw CancellationException("source closed")
    }

    private fun wrapper(source: DataChannelReceiveSource): DataChannelWrapper =
        DataChannelWrapper(
            dataChannel = null,
            connectionEvents = null,
            receiveSource = source,
            initialState = DataChannelState.Open,
            label = "test",
        )

    @Test
    fun mixedTextAndBinaryPreserveSourceOrder() = runTest {
        withContext(Dispatchers.Default) {
            val source = ScriptedReceiveSource()
            val wrapper = wrapper(source)
            val inbound = wrapper.inbound.produceIn(this)

            source.script.trySend(DataChannelInbound.Text("handshake-2"))
            source.script.trySend(DataChannelInbound.Binary(byteArrayOf(0, 1)))
            source.script.trySend(DataChannelInbound.Text("late-text"))
            source.script.trySend(DataChannelInbound.Binary(byteArrayOf(4, 2)))

            assertEquals("handshake-2", (withTimeout(5_000) { inbound.receive() } as DataChannelInbound.Text).text)
            assertContentEquals(
                byteArrayOf(0, 1),
                (withTimeout(5_000) { inbound.receive() } as DataChannelInbound.Binary).bytes,
            )
            assertEquals("late-text", (withTimeout(5_000) { inbound.receive() } as DataChannelInbound.Text).text)
            assertContentEquals(
                byteArrayOf(4, 2),
                (withTimeout(5_000) { inbound.receive() } as DataChannelInbound.Binary).bytes,
            )
            wrapper.close()
            coroutineContext[Job]?.cancelChildren()
        }
    }

    @Test
    fun suspendedConsumerLosesNothingUnderBurst() = runTest {
        withContext(Dispatchers.Default) {
            val source = ScriptedReceiveSource()
            val wrapper = wrapper(source)

            // Burst far beyond any historical bounded-buffer size while the
            // consumer has not even subscribed yet.
            val total = 5000
            repeat(total) { i ->
                if (i % 2 == 0) {
                    source.script.trySend(DataChannelInbound.Text("t$i"))
                } else {
                    source.script.trySend(DataChannelInbound.Binary(byteArrayOf((i % 127).toByte())))
                }
            }

            val inbound = wrapper.inbound.produceIn(this)
            repeat(total) { i ->
                when (val msg = withTimeout(10_000) { inbound.receive() }) {
                    is DataChannelInbound.Text -> assertEquals("t$i", msg.text)
                    is DataChannelInbound.Binary ->
                        assertContentEquals(byteArrayOf((i % 127).toByte()), msg.bytes)
                }
            }
            wrapper.close()
            coroutineContext[Job]?.cancelChildren()
        }
    }
}
