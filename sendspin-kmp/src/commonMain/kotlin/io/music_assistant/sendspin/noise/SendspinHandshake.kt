package io.music_assistant.sendspin.noise

import io.music_assistant.sendspin.api.WireVersion
import io.music_assistant.sendspin.noise.crypto.NoiseCrypto
import io.music_assistant.sendspin.noise.crypto.X25519KeyPair
import io.music_assistant.sendspin.wire.ClientInitMessage
import io.music_assistant.sendspin.wire.ClientInitPayload
import io.music_assistant.sendspin.wire.NoiseHandshakeMessage
import io.music_assistant.sendspin.wire.NoiseHandshakePayload
import io.music_assistant.sendspin.wire.ServerInitMessage
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** The only cipher suite this client implements. */
internal const val SENDSPIN_SUITE_CHACHAPOLY = "25519_ChaChaPoly_SHA256"

/** Core message-format version this client speaks (exact-match field). */
internal const val SENDSPIN_CORE_VERSION = 1

/**
 * Any failure while establishing the encrypted channel. Per the spec's
 * failure-handling rules the caller closes the WebSocket without sending any
 * application-level error message.
 */
internal open class HandshakeFailedException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

/** An explicit protocol mismatch cannot be repaired by reconnecting. */
internal class UnsupportedCoreVersionException(detail: String) :
    HandshakeFailedException("incompatible Sendspin protocol: $detail")

/** Which handshake [SendspinHandshake.runNoiseExchange] runs; only the initial one may fall back to the Sentinel PSK. */
internal enum class HandshakePhase { INITIAL, REHANDSHAKE }

/** Frames the handshake driver can receive during the cleartext phase. */
internal sealed interface HandshakeFrame {
    class Text(val text: String) : HandshakeFrame
    class Binary(val bytes: ByteArray) : HandshakeFrame
}

/** Minimal transport view the initial (cleartext) handshake runs over. */
internal interface HandshakeIo {
    suspend fun sendText(text: String)
    suspend fun receive(): HandshakeFrame
}

/** The established encrypted channel plus its authentication context. */
internal class HandshakeOutcome(
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
internal class SendspinHandshake(
    private val crypto: NoiseCrypto,
    private val clientStatic: X25519KeyPair,
    private val pskCandidates: suspend () -> List<PskCandidate>,
    private val wire: WireVersion,
    private val messageTimeoutMillis: Long = 30_000,
) {
    /** The decrypted inner payload of Noise message 1; `psk_category` exists on spec 1.0.0-rc1 only. */
    private class Message1(val pskId: String, val categoryCode: String?)

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
        rejectServerError(serverInitText)
        val serverInit = try {
            json.decodeFromString<ServerInitMessage>(serverInitText)
        } catch (e: Exception) {
            throw HandshakeFailedException("malformed server/init", e)
        }
        if (serverInit.type != "server/init") {
            throw HandshakeFailedException("expected server/init, got ${serverInit.type}")
        }
        if (serverInit.payload.version != SENDSPIN_CORE_VERSION) {
            throw UnsupportedCoreVersionException(
                "server core version ${serverInit.payload.version}; client supports $SENDSPIN_CORE_VERSION",
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
            phase = HandshakePhase.INITIAL,
            sendMessage = { io.sendText(it) },
            receiveMessage = { receiveText(io) },
        )
    }

    /**
     * A spec 1.0.0-rc1 server answers an unacceptable `client/init` with
     * `server/error` instead of `server/init`. A version or suite rejection is
     * final; anything else is retried like any malformed reply.
     */
    private fun rejectServerError(text: String) {
        val envelope = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return
        if (envelope["type"]?.jsonPrimitive?.content != "server/error") return
        val reason = envelope["payload"]?.jsonObject?.get("reason")?.jsonPrimitive?.content
        throw when (reason) {
            "unsupported_version", "unsupported_suite" -> UnsupportedCoreVersionException("server/error $reason")
            else -> HandshakeFailedException("server/error ${reason ?: "without reason"}")
        }
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
        phase: HandshakePhase,
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
        // without the PSK and carries the psk_id (and category) selecting one.
        val message1Text = awaitNext("noise handshake message 1") { receiveMessage() }
        val message1 = parseHandshakeMessage(message1Text)
        val payload1 = try {
            handshake.readMessage(message1)
        } catch (e: NoiseException) {
            throw HandshakeFailedException("noise message 1 failed", e)
        }
        val matched = selectCandidate(parseMessage1(payload1), serverId, phase)
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

    private fun parseMessage1(payload: ByteArray): Message1 = try {
        val element = Json.parseToJsonElement(payload.decodeToString()).jsonObject
        Message1(
            pskId = element.getValue("psk_id").jsonPrimitive.content,
            categoryCode = element["psk_category"]?.jsonPrimitive?.content,
        )
    } catch (e: Exception) {
        throw HandshakeFailedException("malformed noise message 1 inner payload", e)
    }

    /**
     * The 9.x wire names the PSK by id alone. Spec 1.0.0-rc1 also declares the
     * category, and a `psk_id` held under another category is a miss; on a miss
     * in the initial handshake the client answers with the Sentinel PSK, so the
     * server sees an authenticated credential mismatch and can offer re-pairing.
     */
    private suspend fun selectCandidate(message1: Message1, serverId: String, phase: HandshakePhase): PskCandidate {
        val categoryCode = message1.categoryCode.takeIf { wire == WireVersion.SPEC_1_0 }
        val matched = pskCandidates().firstOrNull {
            SendspinPsk.pskId(crypto, it.psk) == message1.pskId &&
                (categoryCode == null || it.category.wireCode == categoryCode)
        } ?: when {
            wire == WireVersion.SPEC_1_0 && phase == HandshakePhase.INITIAL ->
                PskCandidate(SendspinPsk.SENTINEL_PSK, PskCategory.SENTINEL)

            else -> throw HandshakeFailedException("no PSK candidate for psk_id")
        }
        if (matched.category == PskCategory.LONG_TERM_STORED && matched.serverId != serverId) {
            // Stored-pubkey model: the matched record must belong to the
            // server that presented itself in server/init.
            throw HandshakeFailedException("matched PSK bound to a different server")
        }
        return matched
    }
}
