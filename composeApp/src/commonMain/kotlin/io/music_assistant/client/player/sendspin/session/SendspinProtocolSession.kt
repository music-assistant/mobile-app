package io.music_assistant.client.player.sendspin.session

import io.music_assistant.client.player.sendspin.noise.PskCategory
import io.music_assistant.client.player.sendspin.transport.InboundTransportEvent
import io.music_assistant.client.player.sendspin.transport.SendspinTransport
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException

/** Trust level the client extends to the connected server. */
enum class TrustLevel(val wire: String) {
    NONE("none"),
    USER("user"),
}

/** Lifecycle events a protocol session reports to [SendspinClient]. */
sealed interface SessionEvent {
    /** Establishment in progress past the raw transport (proxy auth or handshake). */
    data class Negotiating(val authenticating: Boolean) : SessionEvent

    /**
     * Hello exchange complete, pre-activation (legacy: `server/hello`;
     * encrypted: after `client/hello` is sent, before any `server/activate`).
     */
    data class ProtocolReady(
        val serverId: String,
        val serverName: String,
        val matchedPskCategory: PskCategory?,
        val trustLevel: TrustLevel,
        val isReconnectEpoch: Boolean,
    ) : SessionEvent

    /** An admissible `server/activate` arrived (encrypted protocol only). */
    data class Activated(
        val activities: List<String>,
        val activeRoles: List<String>,
        val isReconnectEpoch: Boolean,
    ) : SessionEvent

    /** The transport is auto-reconnecting. */
    data class Reconnecting(val attempt: Int) : SessionEvent

    data object Disconnected : SessionEvent

    /** An in-band re-handshake completed and new session keys are in place. */
    data object RehandshakeCompleted : SessionEvent

    /** The session failed after best-effort transport cleanup; [permanent] mirrors the transport's judgment. */
    data class Failed(val cause: Throwable, val permanent: Boolean) : SessionEvent
}

/** Definitive first outcome of a session's initial attach. */
sealed interface SessionOutcome {
    data object Ready : SessionOutcome
    data class Failed(val cause: Throwable) : SessionOutcome
}

/**
 * The session's single serialized outbound path for application JSON.
 * Encrypted sessions gate sends on activation and quiesce them mid re-handshake.
 */
interface SendspinOutboundSender {
    suspend fun sendJson(json: String)
}

/**
 * Owns connection establishment and framing over a [SendspinTransport] and is
 * its only collector; consumers read the demultiplexed streams. Implemented by
 * [LegacySession] (byte-identical legacy wire) and [EncryptedSession] (Noise KKpsk2).
 */
interface SendspinProtocolSession {
    val events: Flow<SessionEvent>

    /** Ordered application JSON messages for the dispatcher. */
    val applicationMessages: Flow<String>

    /** Demuxed audio chunks, byte-compatible with `BinaryMessage.decode`. */
    val audioFrames: Flow<ByteArray>

    val sender: SendspinOutboundSender

    /** Subscribes first, then connects, so a synchronous `Connected` isn't lost. */
    suspend fun start()

    /**
     * First [SessionEvent.ProtocolReady] or terminal failure of the initial
     * attach, capped at the handshake timeout. Reconnects never re-arm this.
     */
    suspend fun awaitInitialOutcome(): SessionOutcome

    suspend fun stop()

    fun close()
}

/** Shared skeleton: epoch-tracking transport collector + ordered output channels. */
internal abstract class AbstractSendspinSession(
    protected val transport: SendspinTransport,
) : SendspinProtocolSession, CoroutineScope {
    private val supervisorJob = SupervisorJob()
    final override val coroutineContext: CoroutineContext
        get() = Dispatchers.Default + supervisorJob

    // Unbounded: dropping a control frame would strand the consumer's state machine.
    private val eventsChannel = Channel<SessionEvent>(Channel.UNLIMITED)
    final override val events: Flow<SessionEvent> = eventsChannel.receiveAsFlow()

    private val applicationChannel = Channel<String>(Channel.UNLIMITED)
    final override val applicationMessages: Flow<String> = applicationChannel.receiveAsFlow()

    private val audioChannel = Channel<ByteArray>(Channel.UNLIMITED)
    final override val audioFrames: Flow<ByteArray> = audioChannel.receiveAsFlow()

    private val initialOutcome = CompletableDeferred<SessionOutcome>()

    private var started = false
    private var driverJob: Job? = null
    private var inboundQueue: ReceiveChannel<InboundTransportEvent>? = null

    protected fun emitEvent(event: SessionEvent) {
        when (event) {
            is SessionEvent.ProtocolReady -> initialOutcome.complete(SessionOutcome.Ready)
            is SessionEvent.Failed -> initialOutcome.complete(SessionOutcome.Failed(event.cause))
            else -> Unit
        }
        eventsChannel.trySend(event)
    }

    protected suspend fun forwardApplication(text: String) = applicationChannel.send(text)

    protected suspend fun forwardAudio(bytes: ByteArray) = audioChannel.send(bytes)

    final override suspend fun start() {
        check(!started) { "session already started" }
        started = true
        val queue = transport.events.produceIn(this)
        inboundQueue = queue
        driverJob = launch { drive(queue) }
        try {
            transport.connect()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A synchronously failing connect would otherwise leave the initial
            // outcome pending until its timeout.
            emitEvent(SessionEvent.Failed(e, permanent = false))
            throw e
        }
    }

    final override suspend fun awaitInitialOutcome(): SessionOutcome =
        withTimeoutOrNull(INITIAL_OUTCOME_TIMEOUT_MILLIS) { initialOutcome.await() }
            ?: SessionOutcome.Failed(IllegalStateException("session did not become ready in time"))

    private suspend fun drive(queue: ReceiveChannel<InboundTransportEvent>) {
        var pending: InboundTransportEvent? = null
        var currentEpoch = 0
        while (true) {
            val event = pending ?: queue.receiveCatching().getOrNull() ?: return
            pending = null
            // A cancelled listener can still emit after a newer epoch's Connected.
            val stale = event !is InboundTransportEvent.Connected && event.epoch < currentEpoch
            when {
                event is InboundTransportEvent.Connected -> {
                    currentEpoch = event.epoch
                    pending = try {
                        runEpoch(event, queue)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        onEpochFailed(e)
                        null
                    }
                }

                stale -> Unit

                event is InboundTransportEvent.Reconnecting ->
                    emitEvent(SessionEvent.Reconnecting(event.attempt))

                event is InboundTransportEvent.Disconnected ->
                    emitEvent(SessionEvent.Disconnected)

                event is InboundTransportEvent.Error ->
                    emitEvent(SessionEvent.Failed(event.cause, event.permanent))

                else -> Unit // Frames of an epoch whose Connected we never saw.
            }
        }
    }

    /**
     * Runs one connection epoch, consuming the shared queue. Returns the control
     * event that ended it, or null when the queue closed.
     */
    protected abstract suspend fun runEpoch(
        connected: InboundTransportEvent.Connected,
        queue: ReceiveChannel<InboundTransportEvent>,
    ): InboundTransportEvent?

    /** Epoch establishment failed with [cause]; close the connection silently. */
    protected open suspend fun onEpochFailed(cause: Exception) {
        try {
            transport.disconnect()
        } catch (_: Exception) {
            // Best-effort close; the socket may already be gone.
        }
        // Publish failure after cleanup so consumers can safely react to it.
        emitEvent(SessionEvent.Failed(cause, permanent = false))
    }

    /** One step of an epoch: a frame of this epoch, or the ending control event. */
    protected sealed interface EpochStep {
        class Text(val text: String) : EpochStep
        class Binary(val bytes: ByteArray) : EpochStep
        class Control(val event: InboundTransportEvent) : EpochStep
    }

    /** Next event for [epoch]; frames from stale epochs are dropped. */
    protected suspend fun nextStep(
        queue: ReceiveChannel<InboundTransportEvent>,
        epoch: Int,
    ): EpochStep {
        while (true) {
            val event = queue.receiveCatching().getOrNull()
                ?: return EpochStep.Control(
                    InboundTransportEvent.Disconnected(epoch),
                )
            when (event) {
                is InboundTransportEvent.Text ->
                    if (event.epoch == epoch) return EpochStep.Text(event.text)

                is InboundTransportEvent.Binary ->
                    if (event.epoch == epoch) return EpochStep.Binary(event.bytes)

                // A newer Connected always ends this epoch; a stale listener's late
                // Disconnected/Error must not kill a healthy successor.
                is InboundTransportEvent.Connected -> return EpochStep.Control(event)

                else -> if (event.epoch >= epoch) return EpochStep.Control(event)
            }
        }
    }

    override suspend fun stop() {
        driverJob?.cancel()
        // Cancel the producer too, or it would keep collecting the transport
        // with no receiver. A stopped session cannot be restarted.
        inboundQueue?.cancel()
        try {
            transport.disconnect()
        } catch (_: Exception) {
            // Best-effort close.
        }
    }

    override fun close() {
        supervisorJob.cancel()
        // Complete the output streams so collectors finish instead of hanging.
        eventsChannel.close()
        applicationChannel.close()
        audioChannel.close()
        transport.close()
    }

    protected companion object {
        /** Caps the whole initial attach, including the transport's own connect wait. */
        const val INITIAL_OUTCOME_TIMEOUT_MILLIS: Long = 30_000
    }
}
