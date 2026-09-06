package io.music_assistant.sendspin.transport

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.pingInterval
import io.music_assistant.sendspin.api.Endpoint
import io.music_assistant.sendspin.api.SendspinTransport
import kotlin.time.Duration.Companion.seconds

/**
 * Opens one authenticated transport per call. The caller owns the result.
 * [openWebSocket] is injectable so the connector is testable without Ktor.
 */
internal class TransportConnector(
    private val openWebSocket: suspend (url: String) -> SendspinTransport,
) {
    suspend fun connect(endpoint: Endpoint, clientId: String): SendspinTransport = when (endpoint) {
        is Endpoint.WebRtc -> endpoint.openChannel()
        is Endpoint.WebSocket -> openWebSocket(endpoint.url).also { ws ->
            try {
                ProxyAuth.authenticate(ws, endpoint.authToken, clientId)
            } catch (e: Throwable) {
                ws.close()
                throw e
            }
        }
    }

    companion object {
        private val PING_INTERVAL = 5.seconds

        /** Production connector: WebSockets over a client derived from the app's. */
        fun ktor(httpClient: HttpClient): TransportConnector {
            val wsClient by lazy {
                httpClient.config {
                    install(WebSockets) {
                        pingInterval = PING_INTERVAL
                    }
                }
            }
            return TransportConnector { url -> WebSocketTransport.connect(wsClient, url) }
        }
    }
}
