package io.music_assistant.sendspin.transport

import io.music_assistant.sendspin.api.Frame
import io.music_assistant.sendspin.api.SendspinTransport
import io.music_assistant.sendspin.wire.ClientAuthMessage
import io.music_assistant.sendspin.wire.ServerMessage
import io.music_assistant.sendspin.wire.WireCodec
import kotlinx.coroutines.withTimeoutOrNull

class ProxyAuthException(message: String) : Exception(message)

/**
 * The MA proxy's `auth` / `auth_ok` exchange. Runs before anything else on a
 * fresh connection, so it may read [SendspinTransport.inbound] directly.
 */
internal object ProxyAuth {
    private const val REPLY_TIMEOUT_MILLIS = 10_000L

    suspend fun authenticate(
        transport: SendspinTransport,
        token: String,
        clientId: String,
        timeoutMillis: Long = REPLY_TIMEOUT_MILLIS,
    ) {
        transport.send(Frame.Text(WireCodec.encode(ClientAuthMessage(token = token, clientId = clientId))))
        val reply = withTimeoutOrNull(timeoutMillis) { transport.inbound.receiveCatching().getOrNull() }
            ?: throw ProxyAuthException("no auth reply (timeout or connection closed)")
        val message = (reply as? Frame.Text)?.let { WireCodec.parse(it.text) }
        if (message !is ServerMessage.AuthOk) {
            throw ProxyAuthException("expected auth_ok, got ${message ?: "binary frame"}")
        }
    }
}
