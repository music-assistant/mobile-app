package io.music_assistant.sendspin.transport

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.websocket.CloseReason
import io.ktor.websocket.close
import io.ktor.websocket.readBytes
import io.ktor.websocket.readText
import io.music_assistant.sendspin.api.Frame
import io.music_assistant.sendspin.api.SendspinTransport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.launch
import io.ktor.websocket.Frame as WsFrame

/**
 * One WebSocket connection. Frames are pumped on the session's own scope into a
 * bounded channel: when the consumer falls behind, the pump suspends and TCP
 * flow control pushes back on the server.
 */
internal class WebSocketTransport private constructor(
    private val session: DefaultClientWebSocketSession,
) : SendspinTransport {
    private val frames = Channel<Frame>(INBOUND_CAPACITY)

    override val inbound: ReceiveChannel<Frame> get() = frames

    init {
        session.launch { pump() }
    }

    // Ktor's Frame is `expect sealed`; the metadata compile needs the `else`.
    @Suppress("REDUNDANT_ELSE_IN_WHEN")
    private suspend fun pump() {
        try {
            for (frame in session.incoming) {
                when (frame) {
                    is WsFrame.Text -> frames.send(Frame.Text(frame.readText()))
                    is WsFrame.Binary -> frames.send(Frame.Binary(frame.readBytes()))
                    else -> Unit // Close, Ping, Pong: Ktor handles them.
                }
            }
            frames.close()
        } catch (e: CancellationException) {
            frames.close()
            throw e
        } catch (e: Throwable) {
            frames.close(e)
        }
    }

    override suspend fun send(frame: Frame) {
        when (frame) {
            is Frame.Text -> session.send(WsFrame.Text(frame.text))
            is Frame.Binary -> session.send(WsFrame.Binary(fin = true, data = frame.bytes))
        }
    }

    override suspend fun close() {
        session.close(CloseReason(CloseReason.Codes.NORMAL, "client"))
    }

    companion object {
        private const val INBOUND_CAPACITY = 64

        suspend fun connect(client: HttpClient, url: String): WebSocketTransport =
            WebSocketTransport(client.webSocketSession(url))
    }
}
