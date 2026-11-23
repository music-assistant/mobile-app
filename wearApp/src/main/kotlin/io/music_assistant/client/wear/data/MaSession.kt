package io.music_assistant.client.wear.data

import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.pingInterval
import io.ktor.client.plugins.websocket.receiveDeserialized
import io.ktor.client.plugins.websocket.sendSerialized
import io.ktor.client.plugins.websocket.ws
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import io.ktor.websocket.close
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds

/** The server answered with an `error_code`: it saw the request and refused it. */
class RpcError(val code: Int?, message: String) : Exception(message)

/** The request left the watch but the link died before an answer came back. */
class ConnectionLost : Exception("connection lost")

/** The request left the watch and nothing came back in time. */
class RequestTimeout(command: String) : Exception("$command timed out")

/** What the watch can tell the user about its link to the server. */
sealed interface SessionState {
    data object Idle : SessionState
    data object Connecting : SessionState
    data class Connected(val info: ServerInfoDto) : SessionState

    /** The token was rejected. Retrying cannot help, so the loop stops. */
    data class Unauthorized(val message: String) : SessionState

    /** The link dropped or never came up. [attempt] counts the retries so far. */
    data class Reconnecting(val attempt: Int) : SessionState

    /** The phone app has sent no server yet, or it signed out. */
    data object NotConfigured : SessionState

    /** The phone reaches the server through WebRTC only, which the watch cannot use. */
    data object RemoteOnly : SessionState
}

/**
 * One Music Assistant WebSocket session: the handshake, the token round-trip, request
 * correlation by `message_id`, and the server's event stream.
 *
 * The session runs while the watch screen is in the foreground and is torn down when it
 * leaves, so the watch holds no socket while the user is not looking at it. Reconnects are
 * capped-exponential; an authentication rejection is terminal, because a retry would only
 * hammer the server with the same bad token.
 *
 * The server and the token come from the phone ([PhoneLinkStore]). A new value ends the
 * running connection and starts one to the new server, or reports why there is none.
 */
class MaSession(
    private val phoneLink: StateFlow<PhoneLink>,
    private val scope: CoroutineScope,
    private val deviceName: String,
) {
    private val logger = Logger.withTag("WearSession")

    private val client = HttpClient(OkHttp) {
        engine {
            config {
                // The WebSockets plugin's own `pingInterval` does not reach OkHttp, which owns
                // the socket. Without this a half-open link stays "connected" until the OS
                // notices — on a watch that can take minutes.
                pingInterval(PING_SECONDS, TimeUnit.SECONDS)
            }
        }
        install(WebSockets) {
            contentConverter = KotlinxWebsocketSerializationConverter(maJson)
            pingInterval = PING_SECONDS.seconds
        }
    }

    private val _state = MutableStateFlow<SessionState>(SessionState.Idle)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<JsonObject>(extraBufferCapacity = EVENT_BUFFER)
    val events: SharedFlow<JsonObject> = _events.asSharedFlow()

    /** Emits once per completed handshake, so the repository reloads its snapshots. */
    private val _sessionReady = MutableSharedFlow<ServerInfoDto>(extraBufferCapacity = 1)
    val sessionReady: SharedFlow<ServerInfoDto> = _sessionReady.asSharedFlow()

    private val pending = mutableMapOf<String, CompletableDeferred<JsonObject>>()
    private val pendingLock = Mutex()
    private val sendLock = Mutex()

    private var socket: DefaultClientWebSocketSession? = null
    private var loop: Job? = null
    private var messageCounter = 0L

    /** True between [start] and [stop]: the screen wants a session. */
    private var wanted = false

    init {
        // On the main thread, like start() and stop(), so the three never race on the loop.
        scope.launch(Dispatchers.Main.immediate) {
            phoneLink.drop(1).collect { if (wanted) restart() }
        }
    }

    fun start() {
        wanted = true
        if (loop?.isActive == true) return
        restart()
    }

    fun stop() {
        wanted = false
        endLoop()
        _state.value = SessionState.Idle
    }

    private fun restart() {
        endLoop()
        when (val link = phoneLink.value) {
            is PhoneLink.Configured -> loop = scope.launch { runConnectionLoop(link.config) }
            PhoneLink.RemoteOnly -> _state.value = SessionState.RemoteOnly
            PhoneLink.SignedOut, PhoneLink.Unknown -> _state.value = SessionState.NotConfigured
        }
    }

    private fun endLoop() {
        loop?.cancel()
        loop = null
        socket = null
    }

    /**
     * Sends [command] and waits for its answer. A failure means "the server did not accept or
     * answer this" — the caller keeps the state it has rather than blanking the screen.
     */
    suspend fun request(command: String, args: JsonObject? = null): Result<JsonElement?> {
        val live = socket ?: return Result.failure(ConnectionLost())
        val messageId = nextMessageId()
        val waiter = CompletableDeferred<JsonObject>()
        pendingLock.withLock { pending[messageId] = waiter }
        return try {
            sendLock.withLock { live.sendSerialized(RpcRequestDto(command, args, messageId)) }
            val answer = withTimeoutOrNull(REQUEST_TIMEOUT_MS) { waiter.await() }
                ?: return Result.failure(RequestTimeout(command))
            answer.rpcError(command)?.let { Result.failure(it) } ?: Result.success(answer["result"])
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            logger.w(e) { "Request $command failed" }
            Result.failure(e)
        } finally {
            pendingLock.withLock { pending.remove(messageId) }
        }
    }

    private suspend fun runConnectionLoop(config: WearServerConfig) {
        var attempt = 0
        while (currentCoroutineContext().isActive) {
            _state.value =
                if (attempt == 0) SessionState.Connecting else SessionState.Reconnecting(attempt)
            if (openSession(config)) return
            attempt += 1
            delay(backoffMillis(attempt))
        }
    }

    /** Runs one connection to completion. Returns true when the server rejected the token. */
    private suspend fun openSession(config: WearServerConfig): Boolean {
        var authRejected = false
        var mine: DefaultClientWebSocketSession? = null
        try {
            client.ws(urlString = config.wsUrl) {
                mine = this
                socket = this
                val hello = receiveDeserialized<JsonObject>()
                val info = maJson.decodeFromJsonElement(ServerInfoDto.serializer(), hello)
                val authJob = launch {
                    when (val outcome = authenticate(config.token)) {
                        AuthOutcome.Granted -> {
                            _state.value = SessionState.Connected(info)
                            _sessionReady.emit(info)
                        }

                        is AuthOutcome.Rejected -> {
                            authRejected = true
                            _state.value = SessionState.Unauthorized(outcome.message)
                            close()
                        }

                        AuthOutcome.NoAnswer -> close()
                    }
                }
                try {
                    while (true) {
                        dispatch(receiveDeserialized())
                    }
                } finally {
                    authJob.cancel()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            logger.w { "Session ended: ${e.message}" }
        } finally {
            // A loop that a new server replaced ends after the new one started: leave its socket.
            if (socket === mine) socket = null
            failPending()
        }
        return authRejected
    }

    private suspend fun dispatch(message: JsonObject) {
        when {
            message.containsKey("message_id") -> {
                val id = message["message_id"]?.jsonPrimitive?.contentOrNull ?: return
                pendingLock.withLock { pending[id] }?.complete(message)
            }

            message.containsKey("event") -> _events.emit(message)
            message.containsKey("server_id") -> Unit
            else -> logger.d { "Ignoring message with keys ${message.keys}" }
        }
    }

    private suspend fun authenticate(token: String): AuthOutcome {
        val args = buildJsonObject {
            put("token", JsonPrimitive(token))
            put("device_name", JsonPrimitive(deviceName))
        }
        return request(APICommands.AUTH, args).fold(
            onSuccess = { AuthOutcome.Granted },
            // Only a server-side refusal is terminal. A timeout or a dropped socket says
            // nothing about the token, so those stay retryable.
            onFailure = { error ->
                (error as? RpcError)
                    ?.let { AuthOutcome.Rejected(it.message ?: "Authentication failed") }
                    ?: AuthOutcome.NoAnswer
            },
        )
    }

    /**
     * Completes every waiting request with a failure. Deliberately not `cancel()`: a cancelled
     * [CompletableDeferred] makes `await()` throw [CancellationException], which would cancel
     * the calling coroutine instead of failing its one request.
     */
    private suspend fun failPending() {
        pendingLock.withLock {
            pending.values.forEach { it.completeExceptionally(ConnectionLost()) }
            pending.clear()
        }
    }

    private fun nextMessageId(): String = "wear-${messageCounter++}"

    private fun backoffMillis(attempt: Int): Long =
        (INITIAL_BACKOFF_MS shl (attempt - 1).coerceAtMost(MAX_BACKOFF_SHIFT))
            .coerceAtMost(MAX_BACKOFF_MS)

    private fun JsonObject.rpcError(command: String): RpcError? {
        if (!containsKey("error_code")) return null
        val code = this["error_code"]?.jsonPrimitive?.intOrNull
        val details = this["details"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
        return RpcError(code, "$command refused: ${details ?: "error $code"}")
    }

    private sealed interface AuthOutcome {
        data object Granted : AuthOutcome
        data object NoAnswer : AuthOutcome
        data class Rejected(val message: String) : AuthOutcome
    }

    private companion object {
        const val PING_SECONDS = 10L
        const val EVENT_BUFFER = 32
        const val REQUEST_TIMEOUT_MS = 20_000L
        const val INITIAL_BACKOFF_MS = 1_000L
        const val MAX_BACKOFF_MS = 15_000L
        const val MAX_BACKOFF_SHIFT = 4
    }
}
