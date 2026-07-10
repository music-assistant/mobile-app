package io.music_assistant.client.player.sendspin.transport

import co.touchlab.kermit.Logger
import com.sendspin.protocol.SendSpinTransport
import com.sendspin.protocol.TransportState
import io.music_assistant.client.player.sendspin.model.ClientAuthMessage
import io.music_assistant.client.utils.myJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Wraps a [SendSpinTransport] to perform Music Assistant's proxy-mode `auth` handshake, which the
 * upstream Sendspin protocol has no concept of. On [connect] it opens the inner transport, sends the
 * `auth` message, waits for `auth_ok`, and only THEN reports [TransportState.Connected] upward — so
 * the library sends `client/hello` in the correct order (after authentication).
 *
 * Keeps the canonical library pure: auth lives entirely in this app-side decorator.
 */
class AuthenticatingTransport(
    private val inner: SendSpinTransport,
    private val authToken: String,
    private val clientId: String,
) : SendSpinTransport {
    private val logger = Logger.withTag("AuthenticatingTransport")
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _state = MutableStateFlow<TransportState>(TransportState.Connecting)
    override val state: StateFlow<TransportState> = _state
    override val textFrames: Flow<String> = inner.textFrames
    override val binaryFrames: Flow<ByteArray> = inner.binaryFrames

    override suspend fun connect() {
        // Forward every inner state EXCEPT Connected, which we gate behind the auth handshake.
        scope.launch {
            inner.state.collect { s ->
                if (s !is TransportState.Connected) _state.value = s
            }
        }

        inner.connect()
        if (inner.state.value !is TransportState.Connected) {
            // connect() already set Error on the inner transport; the collector propagated it.
            return
        }

        // Subscribe for auth_ok BEFORE sending auth so the reply can't race ahead of the collector.
        val authOk = scope.async(start = CoroutineStart.UNDISPATCHED) {
            withTimeoutOrNull(AUTH_TIMEOUT_MS) { inner.textFrames.first { it.isAuthOk() } }
        }

        val authJson = myJson.encodeToString(ClientAuthMessage(token = authToken, clientId = clientId))
        if (!inner.send(authJson)) {
            authOk.cancel()
            _state.value = TransportState.Error(IllegalStateException("Failed to send auth frame"))
            return
        }

        if (authOk.await() == null) {
            _state.value = TransportState.Error(IllegalStateException("auth_ok not received within timeout"))
            return
        }
        logger.i { "auth_ok received — authenticated" }
        _state.value = TransportState.Connected
    }

    override fun send(text: String): Boolean = inner.send(text)
    override fun send(bytes: ByteArray): Boolean = inner.send(bytes)
    override fun disconnect(code: Int, reason: String?) = inner.disconnect(code, reason)

    override fun close() {
        scope.cancel()
        inner.close()
    }

    private fun String.isAuthOk(): Boolean = try {
        myJson.parseToJsonElement(this).jsonObject["type"]?.jsonPrimitive?.contentOrNull == "auth_ok"
    } catch (_: Exception) {
        false
    }

    private companion object {
        const val AUTH_TIMEOUT_MS = 10_000L
    }
}
