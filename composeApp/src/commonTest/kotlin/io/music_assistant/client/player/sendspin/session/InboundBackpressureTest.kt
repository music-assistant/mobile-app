package io.music_assistant.client.player.sendspin.session

import io.music_assistant.client.player.sendspin.transport.InboundTransportEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/**
 * Proves the session-level half of the lossless-inbound contract: a producer
 * bursting interleaved text and binary frames far beyond any configured
 * buffer, while the consumer is deliberately suspended, still results in
 * every frame delivered exactly once and in source order. This test fails if
 * a bounded lossy shared flow or try-emit-with-drop delivery is ever
 * reintroduced anywhere on the inbound path.
 */
class InboundBackpressureTest {
    @Test
    fun sessionDeliversEveryFrameInOrderDespiteSuspendedConsumer() = runTest {
        withContext(Dispatchers.Default) {
            val transport = FakeSendspinTransport()
            val session = LegacySession(
                transport = transport,
                config = LegacySessionConfig(
                    requiresAuth = false,
                    authJson = null,
                    helloJson = """{"type":"client/hello"}""",
                ),
            )
            session.start()
            withTimeout(5_000) { transport.textOut.receive() }

            // Burst while nothing consumes the session outputs.
            val total = 4000
            repeat(total) { i ->
                if (i % 2 == 0) {
                    transport.emit(
                        InboundTransportEvent.Text(1, """{"type":"server/state","payload":{"seq":$i}}"""),
                    )
                } else {
                    transport.emit(InboundTransportEvent.Binary(1, byteArrayOf((i % 127).toByte())))
                }
            }

            // Let the producer run well ahead of any consumer.
            delay(200)

            val app = session.applicationMessages.produceIn(this)
            val audio = session.audioFrames.produceIn(this)
            repeat(total) { i ->
                if (i % 2 == 0) {
                    assertEquals(
                        """{"type":"server/state","payload":{"seq":$i}}""",
                        withTimeout(10_000) { app.receive() },
                        "text frame $i",
                    )
                } else {
                    assertContentEquals(
                        byteArrayOf((i % 127).toByte()),
                        withTimeout(10_000) { audio.receive() },
                        "binary frame $i",
                    )
                }
            }
            session.close()
            coroutineContext[Job]?.cancelChildren()
        }
    }
}
