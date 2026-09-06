package io.music_assistant.sendspin.noise

import io.music_assistant.sendspin.wire.ClientInitMessage
import io.music_assistant.sendspin.wire.ClientInitPayload
import io.music_assistant.sendspin.wire.NoiseHandshakeMessage
import io.music_assistant.sendspin.wire.NoiseHandshakePayload
import io.music_assistant.sendspin.wire.ServerInitMessage
import io.music_assistant.sendspin.noise.crypto.NoiseCrypto
import io.music_assistant.sendspin.noise.crypto.X25519KeyPair
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** The only cipher suite this client implements. */
const val SENDSPIN_SUITE_CHACHAPOLY = "25519_ChaChaPoly_SHA256"

/** Core message-format version this client speaks (exact-match field). */
const val SENDSPIN_CORE_VERSION = 1

/**
 * Any failure while establishing the encrypted channel. Per the spec's
 * failure-handling rules the caller closes the WebSocket without sending any
 * application-level error message.
 */
class HandshakeFailedException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

/** Frames the handshake driver can receive during the cleartext phase. */
sealed interface HandshakeFrame {
    class Text(val text: String) : HandshakeFrame
    class Binary(val bytes: ByteArray) : HandshakeFrame
}

/** Minimal transport view the initial (cleartext) handshake runs over. */
interface HandshakeIo {
    suspend fun sendText(text: String)
    suspend fun receive(): HandshakeFrame
}

/** The established encrypted channel plus its authentication context. */
class HandshakeOutcome(
    val transport: NoiseTransport,
    val handshakeHash: ByteArray,
    val serverId: String,
    val matched: PskCandidate,
)

/**
 * Client-side encrypted-connection establishment: `client/init`/`server/init`,
 * then the two `noise/handshake` messages (server is the Noise initiator).
 * The initial prologue is the exact transmitted bytes of both init messages —
 * never a re-encoding; a re-handshake's prologue is the prior handshake hash.
 */
class SendspinHandshake(
    private val crypto: NoiseCrypto,
    private val clientStatic: X25519KeyPair,
    private val pskCandidates: suspend () -> List<PskCandidate>,
    private val messageTimeoutMillis: Long = 30_000,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    val clientId: String get() = SendspinBase64.encode(clientStatic.publicKey)

    private suspend fun <T> awaitNext(what: String, block: suspend () -> T): T = try {
        withTimeout(messageTimeoutMillis) { block() }
    } catch (e: TimeoutCancellationException) {
        throw HandshakeFailedException("timed out waiting for $what", e)
    }

    /** Full initial establishment; on failure the caller closes the socket
     *  without an application-level error. */
    suspend fun runInitial(io: HandshakeIo): HandshakeOutcome {
        val clientInitText = json.encodeToString(
            ClientInitMessage(
                payload = ClientInitPayload(
                    clientId = clientId,
                    version = SENDSPIN_CORE_VERSION,
                    suite = SENDSPIN_SUITE_CHACHAPOLY,
                ),
            ),
        )
        io.sendText(clientInitText)

        val serverInitText = awaitNext("server/init") { receiveText(io) }
        val serverInit = try {
            json.decodeFromString<ServerInitMessage>(serverInitText)
        } catch (e: Exception) {
            throw HandshakeFailedException("malformed server/init", e)
        }
        if (serverInit.type != "server/init") {
            throw HandshakeFailedException("expected server/init, got ${serverInit.type}")
        }
        if (serverInit.payload.version != SENDSPIN_CORE_VERSION) {
            throw HandshakeFailedException(
                "unsupported core version ${serverInit.payload.version}",
            )
        }
        val serverId = serverInit.payload.serverId
        val serverStaticPublic = SendspinBase64.decodeOrNull(serverId)
            ?.takeIf { it.size == DH_LEN }
            ?: throw HandshakeFailedException("malformed server_id")

        // Prologue: exact wire bytes of client/init then server/init.
        val prologue = clientInitText.encodeToByteArray() + serverInitText.encodeToByteArray()

        return runNoiseExchange(
            prologue = prologue,
            serverId = serverId,
            serverStaticPublic = serverStaticPublic,
            sendMessage = { io.sendText(it) },
            receiveMessage = { receiveText(io) },
        )
    }

    private suspend fun receiveText(io: HandshakeIo): String =
        when (val frame = io.receive()) {
            is HandshakeFrame.Text -> frame.text
            is HandshakeFrame.Binary ->
                throw HandshakeFailedException("unexpected binary frame during handshake")
        }

    /** The two-message Noise exchange; a re-handshake caller routes messages
     *  through the encrypted channel and passes the prior hash as [prologue]. */
    suspend fun runNoiseExchange(
        prologue: ByteArray,
        serverId: String,
        serverStaticPublic: ByteArray,
        sendMessage: suspend (String) -> Unit,
        receiveMessage: suspend () -> String,
    ): HandshakeOutcome {
        val handshake = HandshakeState.sendspinResponder(
            crypto = crypto,
            prologue = prologue,
            clientStatic = clientStatic,
            serverStaticPublic = serverStaticPublic,
            psk = null,
        )

        // Noise message 1 (server → client). Its payload is decryptable
        // without the PSK and carries the psk_id selecting one.
        val message1Text = awaitNext("noise handshake message 1") { receiveMessage() }
        val message1 = parseHandshakeMessage(message1Text)
        val payload1 = try {
            handshake.readMessage(message1)
        } catch (e: NoiseException) {
            throw HandshakeFailedException("noise message 1 failed", e)
        }
        val matched = selectCandidate(extractPskId(payload1), serverId)
        handshake.providePsk(matched.psk)

        // Noise message 2 (client → server); inner payload is the literal
        // two-byte JSON object.
        val message2 = try {
            handshake.writeMessage("{}".encodeToByteArray())
        } catch (e: NoiseException) {
            throw HandshakeFailedException("noise message 2 failed", e)
        }
        sendMessage(
            json.encodeToString(
                NoiseHandshakeMessage(payload = NoiseHandshakePayload(SendspinBase64.encode(message2))),
            ),
        )

        val result = handshake.result
            ?: throw HandshakeFailedException("handshake did not complete")
        return HandshakeOutcome(
            transport = result.transport,
            handshakeHash = result.handshakeHash,
            serverId = serverId,
            matched = matched,
        )
    }

    private fun parseHandshakeMessage(text: String): ByteArray {
        val message = try {
            json.decodeFromString<NoiseHandshakeMessage>(text)
        } catch (e: Exception) {
            throw HandshakeFailedException("malformed noise/handshake message", e)
        }
        if (message.type != "noise/handshake") {
            throw HandshakeFailedException("expected noise/handshake, got ${message.type}")
        }
        return SendspinBase64.decodeOrNull(message.payload.data)
            ?: throw HandshakeFailedException("malformed noise/handshake data")
    }

    private fun extractPskId(payload: ByteArray): String = try {
        val element = Json.parseToJsonElement(payload.decodeToString())
        element.jsonObject.getValue("psk_id").jsonPrimitive.content
    } catch (e: Exception) {
        throw HandshakeFailedException("malformed noise message 1 inner payload", e)
    }

    private suspend fun selectCandidate(pskId: String, serverId: String): PskCandidate {
        val matched = pskCandidates().firstOrNull { SendspinPsk.pskId(crypto, it.psk) == pskId }
            ?: throw HandshakeFailedException("no PSK candidate for psk_id")
        if (matched.category == PskCategory.LONG_TERM_STORED && matched.serverId != serverId) {
            // Stored-pubkey model: the matched record must belong to the
            // server that presented itself in server/init.
            throw HandshakeFailedException("matched PSK bound to a different server")
        }
        return matched
    }
}
