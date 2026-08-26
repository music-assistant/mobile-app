package io.music_assistant.client.player.sendspin.session

import co.touchlab.kermit.Logger
import io.music_assistant.client.player.sendspin.identity.SendspinTrustStore
import io.music_assistant.client.player.sendspin.management.ManagementHandler
import io.music_assistant.client.player.sendspin.model.ClientGoodbyeMessage
import io.music_assistant.client.player.sendspin.model.EncryptedClientHelloMessage
import io.music_assistant.client.player.sendspin.model.EncryptedClientHelloPayload
import io.music_assistant.client.player.sendspin.model.EncryptedDeviceInfo
import io.music_assistant.client.player.sendspin.model.EncryptedServerHelloMessage
import io.music_assistant.client.player.sendspin.model.GoodbyePayload
import io.music_assistant.client.player.sendspin.model.PairAbortMessage
import io.music_assistant.client.player.sendspin.model.PairAbortPayload
import io.music_assistant.client.player.sendspin.model.PairMethodDescriptor
import io.music_assistant.client.player.sendspin.model.PlayerSupport
import io.music_assistant.client.player.sendspin.model.ServerActivateMessage
import io.music_assistant.client.player.sendspin.model.ServerActivatePayload
import io.music_assistant.client.player.sendspin.model.UnpairedAccess
import io.music_assistant.client.player.sendspin.model.VersionedRole
import io.music_assistant.client.player.sendspin.noise.DH_LEN
import io.music_assistant.client.player.sendspin.noise.HandshakeFailedException
import io.music_assistant.client.player.sendspin.noise.HandshakeFrame
import io.music_assistant.client.player.sendspin.noise.HandshakeIo
import io.music_assistant.client.player.sendspin.noise.HandshakeOutcome
import io.music_assistant.client.player.sendspin.noise.NoiseException
import io.music_assistant.client.player.sendspin.noise.NoiseFraming
import io.music_assistant.client.player.sendspin.noise.NoiseTransport
import io.music_assistant.client.player.sendspin.noise.PskCandidate
import io.music_assistant.client.player.sendspin.noise.PskCategory
import io.music_assistant.client.player.sendspin.noise.SendspinBase64
import io.music_assistant.client.player.sendspin.noise.SendspinHandshake
import io.music_assistant.client.player.sendspin.noise.crypto.NoiseCrypto
import io.music_assistant.client.player.sendspin.pairing.PairingHandler
import io.music_assistant.client.player.sendspin.transport.InboundTransportEvent
import io.music_assistant.client.player.sendspin.transport.SendspinTransport
import io.music_assistant.client.utils.myJson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Connection-establishment inputs for the encrypted protocol. */
class EncryptedSessionConfig(
    val requiresAuth: Boolean,
    /** Pre-encoded proxy `auth` message, when [requiresAuth] is set. */
    val authJson: String?,
    val deviceName: String,
    val supportedRoles: List<VersionedRole>,
    val playerSupport: PlayerSupport?,
    val deviceInfo: EncryptedDeviceInfo?,
    /** Bound on a pairing attempt from its first message; spec recommends 2 min. */
    val pairingAttemptTimeoutMillis: Long = 120_000,
)

/**
 * The Noise-encrypted Sendspin session (`KKpsk2`, `25519_ChaChaPoly_SHA256`;
 * the server is the Noise initiator). Per epoch: optional proxy auth, init
 * exchange + Noise handshake, hello exchange (ProtocolReady), then the outbound
 * gate stays closed until the first admissible `server/activate`.
 */
internal class EncryptedSession(
    transport: SendspinTransport,
    private val config: EncryptedSessionConfig,
    private val crypto: NoiseCrypto,
    private val trustStore: SendspinTrustStore,
) : AbstractSendspinSession(transport) {
    private val logger = Logger.withTag("EncryptedSession")

    private enum class GateState { CLOSED, OPEN, FAILED }

    private val gate = MutableStateFlow(GateState.CLOSED)
    private val sendMutex = Mutex()

    private class SecureChannel(
        val noise: NoiseTransport,
        val handshakeHash: ByteArray,
        val serverId: String,
        val matched: PskCandidate,
    ) {
        val decoder = NoiseFraming.Decoder()
    }

    private var channel: SecureChannel? = null
    private var handshake: SendspinHandshake? = null

    private val pairingHandler = PairingHandler(crypto, trustStore)
    private var pairingTimeoutJob: Job? = null

    private val managementHandler = ManagementHandler(trustStore)

    // Activities declared by the latest activation on the current channel.
    private var currentActivities: Set<String> = emptySet()

    // active_roles persists across activations that omit it.
    private var persistedActiveRoles: List<String> = emptyList()

    /** Thrown when a control event interrupts an in-progress establishment. */
    private class EpochInterrupted(val control: InboundTransportEvent) : Exception()

    override val sender: SendspinOutboundSender = GatedSender()

    private inner class GatedSender : SendspinOutboundSender {
        override suspend fun sendJson(json: String) {
            val payload = json.encodeToByteArray()
            while (true) {
                gate.first { it != GateState.CLOSED }
                // Re-check under the mutex: the driver closes the gate and swaps keys
                // while holding it, so a woken sender can't encrypt under stale keys.
                sendMutex.withLock {
                    when (gate.value) {
                        GateState.OPEN -> {
                            val secure = requireChannel()
                            NoiseFraming.encode(NoiseFraming.TYPE_JSON, payload).forEach { frame ->
                                transport.sendBinary(secure.noise.encrypt(frame))
                            }
                            return
                        }

                        GateState.FAILED -> error("encrypted session is closed")

                        GateState.CLOSED -> Unit // Raced a gate transition; wait again.
                    }
                }
            }
        }
    }

    /** Gate moves happen under the send mutex; the sender's re-check relies on it. */
    private suspend fun setGate(state: GateState) {
        sendMutex.withLock { gate.value = state }
    }

    private suspend fun sendEncryptedJson(text: String) {
        sendEncryptedFrame(NoiseFraming.TYPE_JSON, text.encodeToByteArray())
    }

    private fun requireChannel(): SecureChannel =
        channel ?: throw NoiseException("secure channel not established")

    /** Session-internal send: bypasses the gate but shares the mutex, so one
     *  message's frames (incl. fragments) never interleave with another's. */
    private suspend fun sendEncryptedFrame(type: Int, payload: ByteArray) {
        sendMutex.withLock {
            val secure = requireChannel()
            NoiseFraming.encode(type, payload).forEach { frame ->
                transport.sendBinary(secure.noise.encrypt(frame))
            }
        }
    }

    override suspend fun onEpochFailed(cause: Exception) {
        setGate(GateState.FAILED)
        super.onEpochFailed(cause)
    }

    override fun close() {
        gate.value = GateState.FAILED
        super.close()
    }

    override suspend fun runEpoch(
        connected: InboundTransportEvent.Connected,
        queue: ReceiveChannel<InboundTransportEvent>,
    ): InboundTransportEvent? {
        sendMutex.withLock {
            gate.value = GateState.CLOSED
            channel = null
        }
        persistedActiveRoles = emptyList()
        currentActivities = emptySet()
        // A connection drop ends any in-flight pairing attempt; nothing is persisted.
        pairingTimeoutJob?.cancel()
        pairingHandler.discardAttempt()

        val authJson = config.authJson.takeIf { config.requiresAuth }
        emitEvent(SessionEvent.Negotiating(authenticating = authJson != null))
        return try {
            if (authJson != null) {
                transport.sendText(authJson)
                awaitAuthOk(queue, connected.epoch)
            }

            val epochHandshake = SendspinHandshake(
                crypto = crypto,
                clientStatic = trustStore.identity.keyPair,
                pskCandidates = { trustStore.pskCandidates() },
            )
            handshake = epochHandshake
            val outcome = epochHandshake.runInitial(EpochHandshakeIo(queue, connected.epoch))
            sendMutex.withLock { installChannel(outcome) }
            helloExchange(queue, connected)

            mainLoop(queue, connected)
        } catch (e: EpochInterrupted) {
            e.control
        } finally {
            // Epoch over: end any pairing attempt and quiesce senders until the
            // next epoch decides the gate's fate.
            pairingTimeoutJob?.cancel()
            if (gate.value != GateState.FAILED) {
                setGate(GateState.CLOSED)
            }
        }
    }

    private suspend fun mainLoop(
        queue: ReceiveChannel<InboundTransportEvent>,
        connected: InboundTransportEvent.Connected,
    ): InboundTransportEvent? {
        while (true) {
            when (val step = nextStep(queue, connected.epoch)) {
                is EpochStep.Text ->
                    throw HandshakeFailedException("unexpected text frame in transport mode")

                is EpochStep.Binary -> handleCiphertext(step.bytes, queue, connected)

                is EpochStep.Control -> return step.event
            }
        }
    }

    private suspend fun handleCiphertext(
        bytes: ByteArray,
        queue: ReceiveChannel<InboundTransportEvent>,
        connected: InboundTransportEvent.Connected,
    ) {
        val secure = channel ?: throw NoiseException("ciphertext before secure channel")
        val plaintext = secure.noise.decrypt(bytes)
        val message = secure.decoder.decode(plaintext) ?: return
        when {
            message.type == NoiseFraming.TYPE_JSON ->
                routeJson(message.payload.decodeToString(), queue, connected)

            NoiseFraming.isPlayerAudioType(message.type) ->
                // Complete frame bytes, byte-compatible with the legacy audio format.
                forwardAudio(message.toFrameBytes())

            else -> logger.d { "Ignoring binary message type ${message.type}" }
        }
    }

    private suspend fun routeJson(
        text: String,
        queue: ReceiveChannel<InboundTransportEvent>,
        connected: InboundTransportEvent.Connected,
    ) {
        val type = try {
            myJson.parseToJsonElement(text).jsonObject["type"]?.jsonPrimitive?.contentOrNull
        } catch (_: Exception) {
            null
        }
        when {
            type == "server/activate" -> handleActivate(text, connected.isReconnect)
            type == "noise/handshake" -> runRehandshake(text, queue, connected)
            type == "server/unpair" -> handleServerUnpair()
            type == null -> logger.w { "Unparseable encrypted JSON message (${text.length} chars)" }
            type == "server/pair-finalize" -> {
                pairingTimeoutJob?.cancel()
                pairingHandler.completeAttempt()
            }

            type == "pair/abort" -> {
                pairingTimeoutJob?.cancel()
                pairingHandler.discardAttempt()
            }

            // PIN pairing is unimplemented, so any other pairing message is out of
            // sequence — a protocol error that closes the connection.
            type.startsWith("pair/") || type.startsWith("server/pair-") ->
                throw HandshakeFailedException("out-of-sequence pairing message $type")

            type.startsWith("management/") -> handleManagement(type, text)

            else -> forwardApplication(text)
        }
    }

    // --- Establishment helpers ---

    private inner class EpochHandshakeIo(
        private val queue: ReceiveChannel<InboundTransportEvent>,
        private val epoch: Int,
    ) : HandshakeIo {
        override suspend fun sendText(text: String) = transport.sendText(text)

        override suspend fun receive(): HandshakeFrame =
            when (val step = nextStep(queue, epoch)) {
                is EpochStep.Text -> HandshakeFrame.Text(step.text)
                is EpochStep.Binary -> HandshakeFrame.Binary(step.bytes)
                is EpochStep.Control -> throw EpochInterrupted(step.event)
            }
    }

    private suspend fun awaitAuthOk(
        queue: ReceiveChannel<InboundTransportEvent>,
        epoch: Int,
    ) {
        val step = try {
            withTimeout(HANDSHAKE_STEP_TIMEOUT_MILLIS) { nextStep(queue, epoch) }
        } catch (e: TimeoutCancellationException) {
            throw HandshakeFailedException("timed out waiting for auth_ok", e)
        }
        when (step) {
            is EpochStep.Control -> throw EpochInterrupted(step.event)
            is EpochStep.Binary -> throw HandshakeFailedException("unexpected binary frame during auth")
            is EpochStep.Text -> {
                val type = try {
                    myJson.parseToJsonElement(step.text)
                        .jsonObject["type"]?.jsonPrimitive?.contentOrNull
                } catch (_: Exception) {
                    null
                }
                if (type != "auth_ok") {
                    throw HandshakeFailedException("expected auth_ok, got $type")
                }
            }
        }
    }

    private fun installChannel(outcome: HandshakeOutcome) {
        channel = SecureChannel(
            noise = outcome.transport,
            handshakeHash = outcome.handshakeHash,
            serverId = outcome.serverId,
            matched = outcome.matched,
        )
        // Activities, role grants, and any in-flight pairing attempt don't carry
        // across a key swap.
        currentActivities = emptySet()
        persistedActiveRoles = emptyList()
        pairingTimeoutJob?.cancel()
        pairingHandler.discardAttempt()
    }

    /** Next decrypted JSON message during establishment, where nothing else may flow. */
    private suspend fun awaitEncryptedJson(
        queue: ReceiveChannel<InboundTransportEvent>,
        epoch: Int,
        what: String,
    ): String {
        val secure = requireChannel()
        while (true) {
            val step = try {
                withTimeout(HANDSHAKE_STEP_TIMEOUT_MILLIS) { nextStep(queue, epoch) }
            } catch (e: TimeoutCancellationException) {
                throw HandshakeFailedException("timed out waiting for $what", e)
            }
            when (step) {
                is EpochStep.Control -> throw EpochInterrupted(step.event)
                is EpochStep.Text ->
                    throw HandshakeFailedException("unexpected text frame waiting for $what")

                is EpochStep.Binary -> {
                    val message = secure.decoder.decode(secure.noise.decrypt(step.bytes))
                        ?: continue
                    return extractEstablishmentJson(message, what)
                }
            }
        }
    }

    private fun extractEstablishmentJson(message: NoiseFraming.Message, what: String): String {
        if (message.type != NoiseFraming.TYPE_JSON) {
            throw HandshakeFailedException(
                "unexpected message type ${message.type} waiting for $what",
            )
        }
        return message.payload.decodeToString()
    }

    private suspend fun helloExchange(
        queue: ReceiveChannel<InboundTransportEvent>,
        connected: InboundTransportEvent.Connected,
    ) {
        val secure = channel ?: throw NoiseException("no secure channel")
        val helloText = awaitEncryptedJson(queue, connected.epoch, what = "server/hello")
        val serverHello = try {
            myJson.decodeFromString<EncryptedServerHelloMessage>(helloText)
        } catch (e: Exception) {
            throw HandshakeFailedException("malformed encrypted server/hello", e)
        }
        if (serverHello.type != "server/hello") {
            throw HandshakeFailedException("expected server/hello, got ${serverHello.type}")
        }

        val trustLevel = trustLevelFor(secure.matched.category)
        val clientHello = EncryptedClientHelloMessage(
            payload = EncryptedClientHelloPayload(
                name = config.deviceName,
                deviceInfo = config.deviceInfo,
                trustLevel = trustLevel.wire,
                supportedRoles = config.supportedRoles,
                playerV1Support = config.playerSupport,
                supportedPairMethods = if (trustStore.pairingPskEnabled) {
                    listOf(PairMethodDescriptor(method = "pairing_psk"))
                } else {
                    emptyList()
                },
                unpairedAccess = UnpairedAccess(enabled = trustStore.unpairedAccessEnabled),
            ),
        )
        sendEncryptedFrame(
            NoiseFraming.TYPE_JSON,
            myJson.encodeToString(clientHello).encodeToByteArray(),
        )

        if (trustLevel == TrustLevel.USER) {
            trustStore.markRecordUsed(secure.matched.psk)
        }

        emitEvent(
            SessionEvent.ProtocolReady(
                serverId = secure.serverId,
                serverName = serverHello.payload.name,
                matchedPskCategory = secure.matched.category,
                trustLevel = trustLevel,
                isReconnectEpoch = connected.isReconnect,
            ),
        )
    }

    private fun trustLevelFor(category: PskCategory): TrustLevel = when (category) {
        PskCategory.SENTINEL, PskCategory.PAIRING -> TrustLevel.NONE
        PskCategory.LONG_TERM_STORED, PskCategory.LONG_TERM_SHARED -> TrustLevel.USER
    }

    // --- Activation ---

    private fun isAllowedActivitySet(category: PskCategory, activities: Set<String>): Boolean =
        when (category) {
            PskCategory.LONG_TERM_STORED, PskCategory.LONG_TERM_SHARED ->
                activities == setOf(ACTIVITY_PAIRING) ||
                    (ACTIVITY_PLAYBACK_MANAGEMENT_SET.containsAll(activities))

            PskCategory.PAIRING -> activities == setOf(ACTIVITY_PAIRING)

            PskCategory.SENTINEL ->
                activities.isEmpty() ||
                    activities == setOf(ACTIVITY_PAIRING) ||
                    (activities == setOf(ACTIVITY_PLAYBACK) && trustStore.unpairedAccessEnabled)
        }

    /** As [isAllowedActivitySet] but under a hypothetical enabled unpaired access. */
    private fun wouldBeAllowedWithUnpairedAccess(
        category: PskCategory,
        activities: Set<String>,
    ): Boolean = when (category) {
        PskCategory.SENTINEL ->
            activities.isEmpty() ||
                activities == setOf(ACTIVITY_PAIRING) ||
                activities == setOf(ACTIVITY_PLAYBACK)

        else -> isAllowedActivitySet(category, activities)
    }

    private fun isPlaybackCapable(
        category: PskCategory,
        activities: Set<String>,
        unpairedAccessHypothetical: Boolean = false,
    ): Boolean {
        val withPlayback = activities + ACTIVITY_PLAYBACK
        return if (unpairedAccessHypothetical) {
            wouldBeAllowedWithUnpairedAccess(category, withPlayback)
        } else {
            isAllowedActivitySet(category, withPlayback)
        }
    }

    @Suppress("ReturnCount", "LongMethod")
    private suspend fun handleActivate(text: String, isReconnectEpoch: Boolean) {
        val secure = channel ?: return
        // Receiving any server/activate ends an in-flight pairing attempt —
        // including activations this method goes on to reject — so nothing from
        // a superseded attempt can be persisted or aborted later.
        pairingTimeoutJob?.cancel()
        pairingHandler.discardAttempt()
        val payload: ServerActivatePayload = try {
            myJson.decodeFromString<ServerActivateMessage>(text).payload
        } catch (e: Exception) {
            throw HandshakeFailedException("malformed server/activate", e)
        }
        val category = secure.matched.category
        val trustLevel = trustLevelFor(category)
        val activities = payload.activities.toSet()

        val allowed = isAllowedActivitySet(category, activities)
        val playbackCapable = allowed && isPlaybackCapable(category, activities)
        val explicitRoles = payload.activeRoles
        val effectiveRoles = explicitRoles
            ?: if (playbackCapable) persistedActiveRoles else emptyList()

        // Would enabling unpaired access make this activation admissible?
        val admissibleWithUnpairedAccess =
            wouldBeAllowedWithUnpairedAccess(category, activities) &&
                (
                    explicitRoles.isNullOrEmpty() ||
                        isPlaybackCapable(category, activities, unpairedAccessHypothetical = true)
                    ) &&
                !rolesViolateTrust(effectiveRoles, trustLevel)

        val admissible = allowed &&
            (explicitRoles.isNullOrEmpty() || playbackCapable) &&
            !rolesViolateTrust(effectiveRoles, trustLevel)

        if (!admissible) {
            // Spec-ordered rejections: pairing_required only when unpaired access
            // would have admitted this; everything else is unauthorized.
            if (category == PskCategory.SENTINEL &&
                !trustStore.unpairedAccessEnabled &&
                admissibleWithUnpairedAccess
            ) {
                rejectWithGoodbye(GOODBYE_PAIRING_REQUIRED)
            }
            rejectWithGoodbye(GOODBYE_UNAUTHORIZED)
        }

        // Pairing-method check (third ordered rule; leaves the connection open).
        if (ACTIVITY_PAIRING in activities) {
            val method = payload.pairing?.method
            val methodMatchesPsk =
                (method == PAIR_METHOD_PSK) == (category == PskCategory.PAIRING)
            val methodOffered = method == PAIR_METHOD_PSK && trustStore.pairingPskEnabled
            if (method == null || !methodMatchesPsk || !methodOffered) {
                logger.w { "Rejecting pairing activation: method=$method matched=$category" }
                sendEncryptedJson(
                    myJson.encodeToString(
                        PairAbortMessage(payload = PairAbortPayload(reason = "method_not_supported")),
                    ),
                )
                return
            }
        }

        persistedActiveRoles = effectiveRoles
        currentActivities = activities

        // Stay quiesced through a pairing activity: any other client message would
        // interleave with the pairing exchange and abort it server-side.
        setGate(
            if (ACTIVITY_PAIRING in activities) {
                GateState.CLOSED
            } else {
                GateState.OPEN
            },
        )

        emitEvent(
            SessionEvent.Activated(
                activities = payload.activities,
                activeRoles = effectiveRoles,
                isReconnectEpoch = isReconnectEpoch,
            ),
        )

        if (ACTIVITY_PAIRING in activities) {
            startPairingAttempt()
        }
    }

    private suspend fun handleManagement(type: String, text: String) {
        val secure = requireChannel()
        val trustLevel = trustLevelFor(secure.matched.category)
        val managementActive = ACTIVITY_MANAGEMENT in currentActivities &&
            trustLevel == TrustLevel.USER
        val payload = try {
            myJson.parseToJsonElement(text).jsonObject["payload"]?.jsonObject
        } catch (_: Exception) {
            null
        }
        val outcome = managementHandler.handle(
            type = type,
            payload = payload,
            managementActive = managementActive,
            sessionPsk = secure.matched.psk,
        )
        sendEncryptedJson(outcome.resultJson)
        if (outcome.closeUnauthorizedAfterResponse) {
            // The requester removed its own record: respond first, then close.
            rejectWithGoodbye(GOODBYE_UNAUTHORIZED)
        }
    }

    /** Stored-pubkey record: delete. Shared PSK: retain (it may back other
     *  servers) but still close with goodbye `unpaired`. Unpaired session: ignore. */
    private suspend fun handleServerUnpair() {
        val secure = requireChannel()
        when (secure.matched.category) {
            PskCategory.LONG_TERM_STORED -> {
                if (!trustStore.removeRecord(secure.matched.psk)) {
                    // Already re-paired or removed: pairing state transiently
                    // disagrees with the server; the next connect resolves it.
                    logger.w { "server/unpair for a record no longer in the trust store" }
                }
                sendGoodbyeAndDisconnect(GOODBYE_UNPAIRED)
            }

            PskCategory.LONG_TERM_SHARED -> {
                sendGoodbyeAndDisconnect(GOODBYE_UNPAIRED)
            }

            PskCategory.SENTINEL, PskCategory.PAIRING ->
                logger.i { "Ignoring server/unpair on an unpaired session" }
        }
    }

    /** Graceful close (not a failure): goodbye, then disconnect the transport. */
    private suspend fun sendGoodbyeAndDisconnect(reason: String) {
        try {
            sendEncryptedJson(
                myJson.encodeToString(
                    ClientGoodbyeMessage(payload = GoodbyePayload(reason = reason)),
                ),
            )
        } catch (e: Exception) {
            logger.w { "Failed to send goodbye: ${e.message}" }
        }
        setGate(GateState.FAILED)
        try {
            transport.disconnect()
        } catch (e: Exception) {
            logger.w { "Failed to disconnect: ${e.message}" }
        }
    }

    private suspend fun startPairingAttempt() {
        val serverId = requireChannel().serverId
        val attempt = pairingHandler.startAttempt(serverId) { sendEncryptedJson(it) }
        pairingTimeoutJob = launch {
            delay(config.pairingAttemptTimeoutMillis)
            try {
                pairingHandler.abortAttempt(attempt, "attempt_timeout") { sendEncryptedJson(it) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.w { "Failed to send pair/abort: ${e.message}" }
            }
        }
    }

    private fun rolesViolateTrust(roles: List<String>, trustLevel: TrustLevel): Boolean =
        trustLevel == TrustLevel.NONE && roles.contains(ROLE_SOURCE_V1)

    private suspend fun rejectWithGoodbye(reason: String): Nothing {
        logger.w { "Rejecting server/activate with client/goodbye $reason" }
        try {
            sendEncryptedJson(
                myJson.encodeToString(
                    ClientGoodbyeMessage(payload = GoodbyePayload(reason = reason)),
                ),
            )
        } catch (e: Exception) {
            logger.w { "Failed to send goodbye: ${e.message}" }
        }
        throw HandshakeFailedException("activation rejected: $reason")
    }

    // --- Re-handshake ---

    private suspend fun runRehandshake(
        message1Text: String,
        queue: ReceiveChannel<InboundTransportEvent>,
        connected: InboundTransportEvent.Connected,
    ) {
        val secure = channel ?: throw NoiseException("re-handshake before secure channel")
        val epochHandshake = handshake
            ?: throw HandshakeFailedException("re-handshake without prior handshake")
        logger.i { "Re-handshake initiated by server" }

        // Quiesced until the post-re-handshake server/activate.
        setGate(GateState.CLOSED)

        var message1Consumed = false
        val outcome = epochHandshake.runNoiseExchange(
            // The new handshake's prologue is the prior handshake's hash.
            prologue = secure.handshakeHash,
            serverId = secure.serverId,
            serverStaticPublic = SendspinBase64.decodeOrNull(secure.serverId)
                ?.takeIf { it.size == DH_LEN }
                ?: throw HandshakeFailedException("malformed server id"),
            // Noise message 2 still travels under the old transport keys.
            sendMessage = { sendEncryptedFrame(NoiseFraming.TYPE_JSON, it.encodeToByteArray()) },
            receiveMessage = {
                if (message1Consumed) {
                    throw HandshakeFailedException("unexpected extra re-handshake receive")
                }
                message1Consumed = true
                message1Text
            },
        )
        sendMutex.withLock { installChannel(outcome) }
        emitEvent(SessionEvent.RehandshakeCompleted)

        // The hello sequence repeats under the new keys.
        helloExchange(queue, connected)
    }

    private companion object {
        const val HANDSHAKE_STEP_TIMEOUT_MILLIS: Long = 30_000
        const val ACTIVITY_PAIRING = "pairing"
        const val ACTIVITY_PLAYBACK = "playback"
        const val ACTIVITY_MANAGEMENT = "management"
        val ACTIVITY_PLAYBACK_MANAGEMENT_SET = setOf(ACTIVITY_PLAYBACK, ACTIVITY_MANAGEMENT)
        const val PAIR_METHOD_PSK = "pairing_psk"
        const val ROLE_SOURCE_V1 = "source@v1"
        const val GOODBYE_PAIRING_REQUIRED = "pairing_required"
        const val GOODBYE_UNAUTHORIZED = "unauthorized"
        const val GOODBYE_UNPAIRED = "unpaired"
    }
}
