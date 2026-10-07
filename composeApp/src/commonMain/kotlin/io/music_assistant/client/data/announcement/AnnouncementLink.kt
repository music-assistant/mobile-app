package io.music_assistant.client.data.announcement

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import io.music_assistant.client.webrtc.DataChannelInbound
import io.music_assistant.client.webrtc.DataChannelWrapper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/** One live-announcement connection: the webserver socket, or its WebRTC data channel. */
interface AnnouncementLink {
    /** Single collector. Ends after [LinkInbound.Closed] or when the transport goes away. */
    val inbound: Flow<LinkInbound>
    suspend fun sendText(text: String)
    suspend fun sendBinary(bytes: ByteArray)
    suspend fun close()
}

sealed interface LinkInbound {
    data class Text(val text: String) : LinkInbound

    /** A close the server explained; a data channel has no close frame, so it never sends one. */
    data class Closed(val code: Short?, val reason: String?) : LinkInbound
}

internal class WebSocketLink private constructor(
    private val session: DefaultClientWebSocketSession,
) : AnnouncementLink {
    override val inbound: Flow<LinkInbound> = flow {
        for (frame in session.incoming) {
            (frame as? Frame.Text)?.let { emit(LinkInbound.Text(it.readText())) }
        }
        // The session consumes the close frame itself and exposes its reason here.
        session.closeReason.await()?.let { emit(LinkInbound.Closed(it.code, it.message)) }
    }

    override suspend fun sendText(text: String) = session.send(Frame.Text(text))
    override suspend fun sendBinary(bytes: ByteArray) = session.send(Frame.Binary(true, bytes))
    override suspend fun close() = session.close(CloseReason(CloseReason.Codes.NORMAL, ""))

    companion object {
        suspend fun connect(client: HttpClient, url: String): AnnouncementLink =
            WebSocketLink(client.webSocketSession(url))
    }
}

internal class DataChannelLink(private val channel: DataChannelWrapper) : AnnouncementLink {
    // The server answers in text only.
    override val inbound: Flow<LinkInbound> = channel.inbound
        .filterIsInstance<DataChannelInbound.Text>()
        .map { LinkInbound.Text(it.text) }

    override suspend fun sendText(text: String) = channel.send(text)
    override suspend fun sendBinary(bytes: ByteArray) = channel.sendBinary(bytes)
    override suspend fun close() = channel.close()
}
