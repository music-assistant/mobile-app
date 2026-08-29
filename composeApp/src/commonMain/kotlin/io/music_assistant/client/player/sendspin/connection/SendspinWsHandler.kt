package io.music_assistant.client.player.sendspin.connection

import co.touchlab.kermit.Logger
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.pingInterval
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readBytes
import io.ktor.websocket.readReason
import io.ktor.websocket.readText
import io.music_assistant.client.api.DEFAULT_MAX_RECONNECT_ATTEMPTS
import io.music_assistant.client.api.runReconnectionLoop
import io.music_assistant.client.player.sendspin.transport.InboundTransportEvent
import io.music_assistant.client.utils.createPlatformHttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.seconds

/**
 * WebSocket connection for the Sendspin protocol with automatic reconnect.
 *
 * Inbound frames and connection lifecycle share one lossless, source-ordered
 * [events] stream (see [InboundTransportEvent]): each (re)connection starts a
 * new epoch whose `Connected` event is published before any frame the epoch's
 * listener emits, and events ride an unbounded channel so nothing is dropped
 * under backpressure.
 */
class SendspinWsHandler(
    private val serverUrl: String,
    private val networkAvailable: StateFlow<Boolean>? = null,
    private val maxAttempts: Int = DEFAULT_MAX_RECONNECT_ATTEMPTS,
) : CoroutineScope {
    private val logger = Logger.withTag("SendspinWsHandler")
    private val supervisorJob = SupervisorJob()

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.IO + supervisorJob

    private val client = createPlatformHttpClient {
        install(WebSockets) {
            pingInterval = 5.seconds
        }
    }

    private var session: DefaultClientWebSocketSession? = null
    private var listenerJob: Job? = null

    // Auto-reconnect state
    private var explicitDisconnect = false
    private var connecting = false
    private var reconnectJob: Job? = null

    // Monotonic connection-epoch counter; each successful (re)connect bumps it.
    private var epoch = 0

    // Unbounded so emitters never drop: a lost control frame would strand the
    // consumer's protocol state machine.
    private val eventsChannel = Channel<InboundTransportEvent>(Channel.UNLIMITED)

    /** Single-collector ordered inbound stream. */
    val events: Flow<InboundTransportEvent> = eventsChannel.receiveAsFlow()

    private fun emitEvent(event: InboundTransportEvent) {
        eventsChannel.trySend(event)
    }

    suspend fun connect() {
        if (connecting || session != null) {
            logger.w { "Already connected or connecting" }
            return
        }
        connecting = true
        logger.i { "Connecting to server" }

        try {
            val wsSession = client.webSocketSession(serverUrl)
            session = wsSession

            explicitDisconnect = false
            reconnectJob?.cancel()
            reconnectJob = null

            epoch += 1
            logger.i { "Connected to server (epoch $epoch)" }
            // Epoch begins strictly before any of its frames.
            emitEvent(InboundTransportEvent.Connected(epoch, isReconnect = false))
            startListening(wsSession, epoch)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.e(e) { "Failed to connect to server" }
            emitEvent(InboundTransportEvent.Error(epoch, e, permanent = false))
            session = null
        } finally {
            connecting = false
        }
    }

    // Ktor's Frame is `expect sealed`: the metadata compile can't prove the
    // when below is exhaustive without an `else`, but the platform compiles
    // resolve Frame to a concrete sealed and flag the `else` as redundant.
    // Suppress that warning here so both compiles stay clean.
    @Suppress("REDUNDANT_ELSE_IN_WHEN")
    private fun startListening(wsSession: DefaultClientWebSocketSession, listenerEpoch: Int) {
        listenerJob?.cancel()
        listenerJob = launch {
            try {
                for (frame in wsSession.incoming) {
                    when (frame) {
                        is Frame.Text -> {
                            val text = frame.readText()
                            logger.d { "Received text message, length: ${text.length}" }
                            emitEvent(InboundTransportEvent.Text(listenerEpoch, text))
                        }

                        is Frame.Binary -> {
                            val data = frame.readBytes()
                            logger.d { "Received binary message: ${data.size} bytes" }
                            emitEvent(InboundTransportEvent.Binary(listenerEpoch, data))
                        }

                        is Frame.Close -> {
                            logger.i { "WebSocket closed: ${frame.readReason()}" }
                        }

                        is Frame.Ping, is Frame.Pong -> {
                            // Handled automatically by Ktor
                        }

                        else -> Unit
                    }
                }
            } catch (e: Exception) {
                if (explicitDisconnect) {
                    logger.i { "Explicit disconnect, not reconnecting" }
                } else {
                    logger.e(e) { "WS error - will auto-reconnect" }
                }
            } finally {
                if (
                    shouldReconnectAfterListenerExit(
                        explicitDisconnect = explicitDisconnect,
                        listenerIsCurrent = listenerEpoch == epoch,
                    )
                ) {
                    // Close frames, exceptions, and a normally exhausted incoming channel all
                    // converge here so exactly one reconnect loop can be launched per listener.
                    // A listener cancelled after its replacement advanced the epoch is stale.
                    logger.w { "WebSocket listener ended unexpectedly — reconnecting" }
                    emitEvent(InboundTransportEvent.Reconnecting(listenerEpoch, 0))
                    attemptReconnect(listenerEpoch)
                }
            }
        }
    }

    suspend fun sendText(message: String) {
        val currentSession = session
        if (currentSession == null || !currentSession.isActive) {
            error("WebSocket not connected")
        }

        try {
            logger.d { "Sending text message, length: ${message.length}" }
            currentSession.send(Frame.Text(message))
        } catch (e: Exception) {
            logger.e(e) { "Failed to send text message" }
            throw e
        }
    }

    suspend fun sendBinary(data: ByteArray) {
        val currentSession = session
        if (currentSession == null || !currentSession.isActive) {
            error("WebSocket not connected")
        }

        try {
            logger.d { "Sending binary message: ${data.size} bytes" }
            currentSession.send(Frame.Binary(true, data))
        } catch (e: Exception) {
            logger.e(e) { "Failed to send binary message" }
            throw e
        }
    }

    suspend fun disconnect() {
        logger.i { "Disconnecting WebSocket (explicit)" }
        explicitDisconnect = true
        reconnectJob?.cancel()
        reconnectJob = null

        listenerJob?.cancel()
        listenerJob = null

        session?.close(CloseReason(CloseReason.Codes.NORMAL, "Client disconnect"))
        session = null

        emitEvent(InboundTransportEvent.Disconnected(epoch))
    }

    private fun attemptReconnect(previousEpoch: Int) {
        reconnectJob?.cancel()
        reconnectJob = launch {
            val attemptsLabel = if (maxAttempts < 0) "∞" else maxAttempts.toString()
            val reconnected = runReconnectionLoop(
                maxAttempts = maxAttempts,
                networkAvailable = networkAvailable,
                onAttemptStarting = { attempt ->
                    logger.i { "Reconnect attempt $attempt/$attemptsLabel" }
                    emitEvent(InboundTransportEvent.Reconnecting(previousEpoch, attempt))
                },
                tryConnect = { attempt ->
                    try {
                        val wsSession = client.webSocketSession(serverUrl)
                        session = wsSession
                        logger.i { "Reconnected successfully after $attempt attempts" }
                        epoch += 1
                        // The new epoch's Connected event must precede its
                        // first frame, so publish before the listener starts.
                        emitEvent(InboundTransportEvent.Connected(epoch, isReconnect = true))
                        startListening(wsSession, epoch)
                        true
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        logger.w(e) { "Reconnect attempt $attempt failed" }
                        false
                    }
                },
            )
            if (!reconnected) {
                logger.e { "Max reconnect attempts ($maxAttempts) reached, giving up" }
                session = null
                emitEvent(
                    InboundTransportEvent.Error(
                        previousEpoch,
                        Exception("Failed to reconnect after $maxAttempts attempts"),
                        permanent = true,
                    ),
                )
            }
        }
    }

    fun close() {
        logger.i { "Closing WebSocketHandler" }
        explicitDisconnect = true
        reconnectJob?.cancel()
        supervisorJob.cancel()
        client.close()
    }
}

internal fun shouldReconnectAfterListenerExit(
    explicitDisconnect: Boolean,
    listenerIsCurrent: Boolean,
): Boolean = !explicitDisconnect && listenerIsCurrent
