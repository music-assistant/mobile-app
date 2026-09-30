package io.music_assistant.sendspin.session

import co.touchlab.kermit.Logger
import io.music_assistant.sendspin.api.Frame
import io.music_assistant.sendspin.api.SendspinTransport
import io.music_assistant.sendspin.identity.SendspinTrustStore
import io.music_assistant.sendspin.management.ManagementHandler
import io.music_assistant.sendspin.noise.DH_LEN
import io.music_assistant.sendspin.noise.HandshakeFailedException
import io.music_assistant.sendspin.noise.HandshakeFrame
import io.music_assistant.sendspin.noise.HandshakeIo
import io.music_assistant.sendspin.noise.HandshakeOutcome
import io.music_assistant.sendspin.noise.NoiseException
import io.music_assistant.sendspin.noise.NoiseFraming
import io.music_assistant.sendspin.noise.NoiseTransport
import io.music_assistant.sendspin.noise.PskCandidate
import io.music_assistant.sendspin.noise.PskCategory
import io.music_assistant.sendspin.noise.SendspinBase64
import io.music_assistant.sendspin.noise.SendspinHandshake
import io.music_assistant.sendspin.noise.crypto.NoiseCrypto
import io.music_assistant.sendspin.pairing.PairingHandler
import io.music_assistant.sendspin.wire.BinaryFrame
import io.music_assistant.sendspin.wire.BinaryFrames
import io.music_assistant.sendspin.wire.ClientGoodbyeMessage
import io.music_assistant.sendspin.wire.EncryptedClientHelloMessage
import io.music_assistant.sendspin.wire.EncryptedClientHelloPayload
import io.music_assistant.sendspin.wire.GoodbyePayload
import io.music_assistant.sendspin.wire.GoodbyeReason
import io.music_assistant.sendspin.wire.PairAbortMessage
import io.music_assistant.sendspin.wire.PairAbortPayload
import io.music_assistant.sendspin.wire.PairMethodDescriptor
import io.music_assistant.sendspin.wire.ServerActivatePayload
import io.music_assistant.sendspin.wire.ServerMessage
import io.music_assistant.sendspin.wire.UnpairedAccess
import io.music_assistant.sendspin.wire.VersionedRole
import io.music_assistant.sendspin.wire.WireCodec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * One Noise-encrypted Sendspin session (`KKpsk2`, `25519_ChaChaPoly_SHA256`;
 * the server is the Noise initiator) over one already-authenticated transport.
 *
 * [run] drives everything on the calling coroutine: init exchange, Noise
 * handshake, hello exchange, then the main loop until the connection ends.
 * The outbound gate stays closed until the first admissible `server/activate`
 * and closes again through pairing activities and re-handshakes.
 */
internal class NoiseSession(
    private val transport: SendspinTransport,
    private val config: SessionConfig,
    private val crypto: NoiseCrypto,
    private val trustStore: SendspinTrustStore,
) {
    private val logger = Logger.withTag("NoiseSession")

    private enum class Gate { CLOSED, OPEN, FAILED }

    private class SecureChannel(
        val noise: NoiseTransport,
        val handshakeHash: ByteArray,
        val serverId: String,
        val matched: PskCandidate,
    ) {
        val decoder = NoiseFraming.Decoder()
    }

    private val gate = MutableStateFlow(Gate.CLOSED)

    // Serializes encryption (Noise nonces are ordered) and gate transitions.
    private val sendMutex = Mutex()
    private var channel: SecureChannel? = null
    private var handshake: SendspinHandshake? = null

    private val pairingHandler = PairingHandler(crypto, trustStore)
    private val managementHandler = ManagementHandler(trustStore)
    private var pairingTimeout: Job? = null
    private var activities: Set<String> = emptySet()
    private var persistedRoles: List<String> = emptyList()

    /**
     * Application send. Suspends until the session is activated; quiesces
     * through pairing and re-handshakes. Throws once the session is over.
     */
    suspend fun send(json: String) {
        val payload = json.encodeToByteArray()
        while (true) {
            gate.first { it != Gate.CLOSED }
            // Re-check under the mutex: the reader closes the gate and swaps keys
            // while holding it, so a woken sender cannot encrypt under stale keys.
            sendMutex.withLock {
                when (gate.value) {
                    Gate.OPEN -> return sendFrameLocked(NoiseFraming.TYPE_JSON, payload)
                    Gate.FAILED -> error("session closed")
                    Gate.CLOSED -> Unit // Raced a gate transition; wait again.
                }
            }
        }
    }

    /** Graceful client-initiated close: `client/goodbye` when possible, then the transport. */
    suspend fun goodbye(reason: GoodbyeReason) {
        sendGoodbyeQuietly(reason.wire)
        transport.close()
    }

    /**
     * Runs the session to completion. Returns when the server closes the
     * connection cleanly. Throws [TransportLost], [SessionRejected],
     * [HandshakeFailedException], or [NoiseException] otherwise.
     */
    suspend fun run(handler: SessionHandler): Unit = coroutineScope {
        // On cancellation the owner says goodbye and closes the transport itself.
        var cancelled = false
        try {
            val initial = SendspinHandshake(
                crypto = crypto,
                clientStatic = trustStore.identity.keyPair,
                pskCandidates = { trustStore.pskCandidates() },
                messageTimeoutMillis = config.stepTimeoutMillis,
            )
            handshake = initial
            installChannel(initial.runInitial(TransportHandshakeIo()))
            helloExchange(handler)
            for (frame in transport.inbound) {
                when (frame) {
                    is Frame.Text -> throw HandshakeFailedException("text frame in transport mode")
                    is Frame.Binary -> handleCiphertext(frame.bytes, this, handler)
                }
            }
        } catch (e: CancellationException) {
            cancelled = true
            throw e
        } catch (e: SessionRejected) {
            throw e
        } catch (e: TransportLost) {
            throw e
        } catch (e: HandshakeFailedException) {
            throw e
        } catch (e: NoiseException) {
            throw e
        } catch (e: NoiseFraming.ProtocolException) {
            throw e
        } catch (e: Throwable) {
            throw TransportLost(e)
        } finally {
            pairingTimeout?.cancel()
            pairingHandler.discardAttempt()
            setGate(Gate.FAILED)
            if (!cancelled) closeTransportQuietly()
        }
    }

    private suspend fun closeTransportQuietly() {
        try {
            transport.close()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.w { "Failed to close transport: ${e.message}" }
        }
    }

    // --- Inbound ---

    private suspend fun handleCiphertext(bytes: ByteArray, scope: CoroutineScope, handler: SessionHandler) {
        val secure = channel ?: throw NoiseException("ciphertext before secure channel")
        val message = secure.decoder.decode(secure.noise.decrypt(bytes)) ?: return
        when {
            message.type == NoiseFraming.TYPE_JSON ->
                route(WireCodec.parse(message.payload.decodeToString()), scope, handler)

            NoiseFraming.isPlayerAudioType(message.type) ->
                when (val parsed = BinaryFrames.parse(message.type, message.payload)) {
                    is BinaryFrame.Audio -> handler.onAudio(parsed.chunk)
                    is BinaryFrame.Other, BinaryFrame.Malformed -> logger.w { "Ignoring binary frame $parsed" }
                }

            else -> logger.d { "Ignoring binary message type ${message.type}" }
        }
    }

    private suspend fun route(message: ServerMessage, scope: CoroutineScope, handler: SessionHandler) {
        when (message) {
            is ServerMessage.Activate -> handleActivate(message.payload, scope, handler)
            is ServerMessage.NoiseHandshake -> rehandshake(message.data, handler)
            is ServerMessage.Unpair -> handleUnpair()
            is ServerMessage.PairFinalize -> endPairingAttempt { pairingHandler.completeAttempt() }
            is ServerMessage.PairAbort -> endPairingAttempt { pairingHandler.discardAttempt() }
            is ServerMessage.Management -> handleManagement(message)
            is ServerMessage.Malformed -> logger.w { "Unparseable encrypted JSON message" }
            is ServerMessage.Unknown -> if (message.type.isPairingType()) {
                // PIN pairing is unimplemented: any other pairing message is out of sequence.
                throw HandshakeFailedException("out-of-sequence pairing message ${message.type}")
            } else {
                handler.onMessage(message)
            }

            else -> handler.onMessage(message)
        }
    }

    private fun String.isPairingType() = startsWith("pair/") || startsWith("server/pair-")

    private fun endPairingAttempt(action: () -> Unit) {
        pairingTimeout?.cancel()
        action()
    }

    // --- Establishment ---

    private inner class TransportHandshakeIo : HandshakeIo {
        override suspend fun sendText(text: String) = transport.send(Frame.Text(text))

        override suspend fun receive(): HandshakeFrame = when (val frame = receiveOrLost()) {
            is Frame.Text -> HandshakeFrame.Text(frame.text)
            is Frame.Binary -> HandshakeFrame.Binary(frame.bytes)
        }
    }

    private suspend fun receiveOrLost(): Frame {
        val result = transport.inbound.receiveCatching()
        return result.getOrNull() ?: throw TransportLost(result.exceptionOrNull())
    }

    private suspend fun installChannel(outcome: HandshakeOutcome) = sendMutex.withLock {
        channel = SecureChannel(outcome.transport, outcome.handshakeHash, outcome.serverId, outcome.matched)
        // Activities, role grants, and any in-flight pairing attempt do not survive a key swap.
        activities = emptySet()
        persistedRoles = emptyList()
        pairingTimeout?.cancel()
        pairingHandler.discardAttempt()
    }

    /** Next decrypted JSON message during establishment, where nothing else may flow. */
    private suspend fun awaitEstablishmentJson(what: String): ServerMessage {
        val secure = requireChannel()
        while (true) {
            val frame = withTimeoutOrNull(config.stepTimeoutMillis) { receiveOrLost() }
                ?: throw HandshakeFailedException("timed out waiting for $what")
            val bytes = (frame as? Frame.Binary)?.bytes
                ?: throw HandshakeFailedException("unexpected text frame waiting for $what")
            val message = secure.decoder.decode(secure.noise.decrypt(bytes)) ?: continue
            if (message.type != NoiseFraming.TYPE_JSON) {
                throw HandshakeFailedException("unexpected message type ${message.type} waiting for $what")
            }
            return WireCodec.parse(message.payload.decodeToString())
        }
    }

    private suspend fun helloExchange(handler: SessionHandler) {
        val secure = requireChannel()
        val hello = awaitEstablishmentJson("server/hello") as? ServerMessage.Hello
            ?: throw HandshakeFailedException("expected server/hello")
        val trustLevel = TrustLevel.of(secure.matched.category)
        val clientHello = EncryptedClientHelloMessage(
            payload = EncryptedClientHelloPayload(
                name = config.deviceName,
                deviceInfo = config.deviceInfo,
                trustLevel = trustLevel.wire,
                supportedRoles = listOf(VersionedRole.PLAYER_V1),
                playerV1Support = config.playerSupport,
                supportedPairMethods = if (trustStore.pairingPskEnabled) {
                    listOf(PairMethodDescriptor(ActivationPolicy.PAIR_METHOD_PSK))
                } else {
                    emptyList()
                },
                unpairedAccess = UnpairedAccess(enabled = trustStore.unpairedAccessEnabled),
            ),
        )
        sendJsonUngated(WireCodec.encode(clientHello))
        if (trustLevel == TrustLevel.USER) trustStore.markRecordUsed(secure.matched.psk)
        handler.onReady(SessionInfo(secure.serverId, hello.serverName, secure.matched.category, trustLevel))
    }

    private suspend fun rehandshake(message1: String, handler: SessionHandler) {
        val secure = requireChannel()
        val current = handshake ?: throw HandshakeFailedException("re-handshake without prior handshake")
        logger.i { "Re-handshake initiated by server" }
        setGate(Gate.CLOSED)
        var consumed = false
        val outcome = current.runNoiseExchange(
            prologue = secure.handshakeHash,
            serverId = secure.serverId,
            serverStaticPublic = SendspinBase64.decodeOrNull(secure.serverId)
                ?.takeIf { it.size == DH_LEN }
                ?: throw HandshakeFailedException("malformed server id"),
            // Noise message 2 still travels under the old keys.
            sendMessage = { sendJsonUngated(it) },
            receiveMessage = {
                if (consumed) throw HandshakeFailedException("unexpected extra re-handshake receive")
                consumed = true
                WireCodec.encode(
                    io.music_assistant.sendspin.wire.NoiseHandshakeMessage(
                        payload = io.music_assistant.sendspin.wire.NoiseHandshakePayload(message1),
                    ),
                )
            },
        )
        installChannel(outcome)
        helloExchange(handler)
    }

    // --- Activation, pairing, management, unpair ---

    private suspend fun handleActivate(
        payload: ServerActivatePayload,
        scope: CoroutineScope,
        handler: SessionHandler,
    ) {
        val secure = requireChannel()
        // Any server/activate ends an in-flight pairing attempt, including ones rejected below.
        endPairingAttempt { pairingHandler.discardAttempt() }
        val decision = ActivationPolicy.decide(
            ActivationPolicy.Input(
                category = secure.matched.category,
                activities = payload.activities.toSet(),
                explicitRoles = payload.activeRoles,
                persistedRoles = persistedRoles,
                pairingMethod = payload.pairing?.method,
                unpairedAccessEnabled = trustStore.unpairedAccessEnabled,
                pairingPskEnabled = trustStore.pairingPskEnabled,
            ),
        )
        when (decision) {
            is ActivationPolicy.Decision.Reject -> reject(decision.goodbyeReason)
            ActivationPolicy.Decision.AbortPairing -> {
                logger.w { "Rejecting pairing activation: unsupported method" }
                sendJsonUngated(WireCodec.encode(PairAbortMessage(payload = PairAbortPayload("method_not_supported"))))
            }

            is ActivationPolicy.Decision.Admit -> {
                persistedRoles = decision.activeRoles
                activities = payload.activities.toSet()
                // Stay quiesced through a pairing activity: any other client message
                // would interleave with the pairing exchange and abort it server-side.
                setGate(if (decision.pairing) Gate.CLOSED else Gate.OPEN)
                handler.onActivated(Activation(payload.activities, decision.activeRoles))
                if (decision.pairing) startPairingAttempt(scope, secure.serverId)
            }
        }
    }

    private suspend fun startPairingAttempt(scope: CoroutineScope, serverId: String) {
        val attempt = pairingHandler.startAttempt(serverId) { sendJsonUngated(it) }
        pairingTimeout = scope.launch {
            delay(config.pairingAttemptTimeoutMillis)
            try {
                pairingHandler.abortAttempt(attempt, "attempt_timeout") { sendJsonUngated(it) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.w { "Failed to send pair/abort: ${e.message}" }
            }
        }
    }

    private suspend fun handleManagement(message: ServerMessage.Management) {
        val secure = requireChannel()
        val managementActive = ActivationPolicy.ACTIVITY_MANAGEMENT in activities &&
            TrustLevel.of(secure.matched.category) == TrustLevel.USER
        val outcome = managementHandler.handle(
            type = message.type,
            payload = message.payload,
            managementActive = managementActive,
            sessionPsk = secure.matched.psk,
        )
        sendJsonUngated(outcome.resultJson)
        // The requester removed its own record: respond first, then close.
        if (outcome.closeUnauthorizedAfterResponse) reject(ActivationPolicy.GOODBYE_UNAUTHORIZED)
    }

    /** Stored record: delete. Shared PSK: keep (other servers may use it). Both close `unpaired`. */
    private suspend fun handleUnpair() {
        val secure = requireChannel()
        when (secure.matched.category) {
            PskCategory.LONG_TERM_STORED -> {
                if (!trustStore.removeRecord(secure.matched.psk)) {
                    logger.w { "server/unpair for a record no longer in the trust store" }
                }
                reject(GOODBYE_UNPAIRED)
            }

            PskCategory.LONG_TERM_SHARED -> reject(GOODBYE_UNPAIRED)
            PskCategory.SENTINEL, PskCategory.PAIRING -> logger.i { "Ignoring server/unpair on an unpaired session" }
        }
    }

    private suspend fun reject(reason: String): Nothing {
        logger.w { "Closing with client/goodbye $reason" }
        goodbye(reason)
        throw SessionRejected(reason)
    }

    private suspend fun goodbye(reason: String) {
        sendGoodbyeQuietly(reason)
        setGate(Gate.FAILED)
        closeTransportQuietly()
    }

    private suspend fun sendGoodbyeQuietly(reason: String) {
        if (channel == null) return
        try {
            sendJsonUngated(WireCodec.encode(ClientGoodbyeMessage(payload = GoodbyePayload(reason))))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.w { "Failed to send goodbye: ${e.message}" }
        }
    }

    // --- Outbound ---

    private fun requireChannel(): SecureChannel = channel ?: throw NoiseException("secure channel not established")

    private suspend fun setGate(state: Gate) = sendMutex.withLock { gate.value = state }

    /** Session-internal send: bypasses the gate but shares the mutex, so frames never interleave. */
    private suspend fun sendJsonUngated(json: String) = sendMutex.withLock {
        sendFrameLocked(NoiseFraming.TYPE_JSON, json.encodeToByteArray())
    }

    private suspend fun sendFrameLocked(type: Int, payload: ByteArray) {
        val secure = requireChannel()
        NoiseFraming.encode(type, payload).forEach { frame ->
            transport.send(Frame.Binary(secure.noise.encrypt(frame)))
        }
    }

    private companion object {
        const val GOODBYE_UNPAIRED = "unpaired"
    }
}
