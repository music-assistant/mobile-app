package io.music_assistant.sendspin.fakes

import io.music_assistant.sendspin.api.Frame
import io.music_assistant.sendspin.api.SendspinTransport
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel

/** Scriptable transport: the test plays the server. */
class FakeTransport : SendspinTransport {
    private val fromServer = Channel<Frame>(Channel.UNLIMITED)

    /** Every frame the client sent, in order; the test server reads from here. */
    val outbound = Channel<Frame>(Channel.UNLIMITED)
    val sent = mutableListOf<Frame>()
    var closed = false
        private set

    override val inbound: ReceiveChannel<Frame> get() = fromServer

    override suspend fun send(frame: Frame) {
        check(!closed) { "transport closed" }
        sent += frame
        outbound.trySend(frame)
    }

    override suspend fun close() {
        closed = true
        fromServer.close()
    }

    fun serverSends(text: String) {
        fromServer.trySend(Frame.Text(text))
    }

    fun serverSends(bytes: ByteArray) {
        fromServer.trySend(Frame.Binary(bytes))
    }

    /** The connection ends; [cause] marks a failure, null a clean close. */
    fun serverDrops(cause: Throwable? = null) {
        fromServer.close(cause)
    }

    val sentTexts: List<String> get() = sent.filterIsInstance<Frame.Text>().map { it.text }
}
