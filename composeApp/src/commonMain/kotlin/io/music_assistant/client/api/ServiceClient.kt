package io.music_assistant.client.api

import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.receiveDeserialized
import io.ktor.client.plugins.websocket.ws
import io.ktor.client.plugins.websocket.wss
import io.ktor.http.HttpMethod
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import io.ktor.websocket.close
import io.music_assistant.client.data.model.server.AuthorizationResponse
import io.music_assistant.client.data.model.server.LoginResponse
import io.music_assistant.client.data.model.server.events.Event
import io.music_assistant.client.settings.SettingsRepository
import io.music_assistant.client.utils.AuthProcessState
import io.music_assistant.client.utils.SessionState
import io.music_assistant.client.utils.connectionInfo
import io.music_assistant.client.utils.myJson
import io.music_assistant.client.utils.resultAs
import io.music_assistant.client.utils.sendMessage
import io.music_assistant.client.utils.update
import io.music_assistant.client.webrtc.SignalingClient
import io.music_assistant.client.webrtc.WebRTCConnectionManager
import io.music_assistant.client.webrtc.model.RemoteId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.qualifier.named
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class ServiceClient(private val settings: SettingsRepository) : CoroutineScope, KoinComponent {

    private val supervisorJob = SupervisorJob()
    override val coroutineContext: CoroutineContext = supervisorJob + Dispatchers.IO

    private val client = HttpClient(CIO) {
        install(WebSockets) { contentConverter = KotlinxWebsocketSerializationConverter(myJson) }
    }
    private var listeningJob: Job? = null

    // WebRTC components - created lazily on first WebRTC connection
    private val webrtcHttpClient: HttpClient by inject(named("webrtcHttpClient"))
    private var webrtcManager: WebRTCConnectionManager? = null
    private var webrtcListeningJob: Job? = null

    private var _sessionState: MutableStateFlow<SessionState> =
        MutableStateFlow(SessionState.Disconnected.Initial)
    val sessionState = _sessionState.asStateFlow()

    private val _eventsFlow = MutableSharedFlow<Event<out Any>>(extraBufferCapacity = 10)
    val events: Flow<Event<out Any>> = _eventsFlow.asSharedFlow()

    /**
     * WebRTC Sendspin data channel.
     * Available when connected via WebRTC, null otherwise.
     * Used by SendspinClientFactory to create WebRTC transport for Sendspin.
     */
    val webrtcSendspinChannel: io.music_assistant.client.webrtc.DataChannelWrapper?
        get() = webrtcManager?.sendspinDataChannel

    private val pendingResponses = mutableMapOf<String, (Answer) -> Unit>()

    init {
        launch {
            _sessionState.collect { state ->
                when (state) {
                    is SessionState.Connected -> {
                        state.connectionInfo?.let { connInfo -> settings.updateConnectionInfo(connInfo) }
                    }

                    is SessionState.Reconnecting -> {
                        // Keep connection info during reconnection (no UI reload)
                        state.connectionInfo?.let { connInfo -> settings.updateConnectionInfo(connInfo) }
                    }

                    is SessionState.Disconnected -> {
                        listeningJob?.cancel()
                        listeningJob = null
                        when (state) {
                            SessionState.Disconnected.ByUser,
                            SessionState.Disconnected.NoServerData,
                            is SessionState.Disconnected.Error -> Unit

                            SessionState.Disconnected.Initial -> {
                                // Auto-connect based on last successful connection mode
                                when (settings.lastConnectionMode.value) {
                                    "webrtc" -> {
                                        val remoteIdStr = settings.webrtcRemoteId.value
                                        val remoteId = remoteIdStr
                                            .takeIf { s -> s.isNotBlank() }
                                            ?.let { s -> RemoteId.parse(s) }
                                        if (remoteId != null) {
                                            connectWebRTC(remoteId)
                                        } else {
                                            // WebRTC selected but no valid remoteId - don't auto-connect
                                            _sessionState.update { SessionState.Disconnected.NoServerData }
                                        }
                                    }

                                    "direct", null -> {
                                        // Default to Direct for existing users (null) or explicit "direct"
                                        settings.connectionInfo.value?.let { connectionInfo ->
                                            connect(connectionInfo)
                                        }
                                            ?: _sessionState.update { SessionState.Disconnected.NoServerData }
                                    }

                                    else -> {
                                        // Unknown mode - no auto-connect
                                        _sessionState.update { SessionState.Disconnected.NoServerData }
                                    }
                                }
                            }
                        }
                    }

                    SessionState.Connecting -> Unit
                }
            }
        }
    }

    fun connect(connection: ConnectionInfo) {
        when (val currentState = _sessionState.value) {
            SessionState.Connecting,
            is SessionState.Connected -> return

            is SessionState.Reconnecting -> {
                // Don't change state during reconnection - stay in Reconnecting!
                // This prevents MainDataSource from calling stopSendspin()
                Logger.withTag("ServiceClient")
                    .i { "🔄 RECONNECT ATTEMPT - staying in Reconnecting state (no stopSendspin!)" }
                launch {
                    try {
                        if (connection.isTls) {
                            client.wss(
                                HttpMethod.Get,
                                connection.host,
                                connection.port,
                                "/ws",
                            ) {
                                // Preserve server/user/auth from Reconnecting state
                                _sessionState.update {
                                    SessionState.Connected.Direct(
                                        session = this,
                                        connectionInfo = connection,
                                        serverInfo = currentState.serverInfo,
                                        user = currentState.user,
                                        authProcessState = currentState.authProcessState,
                                        wasAutoLogin = currentState.wasAutoLogin
                                    )
                                }
                                listenForMessages()
                            }
                        } else {
                            client.ws(
                                HttpMethod.Get,
                                connection.host,
                                connection.port,
                                "/ws",
                            ) {
                                // Preserve server/user/auth from Reconnecting state
                                _sessionState.update {
                                    SessionState.Connected.Direct(
                                        session = this,
                                        connectionInfo = connection,
                                        serverInfo = currentState.serverInfo,
                                        user = currentState.user,
                                        authProcessState = currentState.authProcessState,
                                        wasAutoLogin = currentState.wasAutoLogin
                                    )
                                }
                                listenForMessages()
                            }
                        }
                    } catch (e: Exception) {
                        // CRITICAL: Don't transition to Disconnected.Error during reconnection!
                        // Stay in Reconnecting state and let the outer loop handle retries
                        // Transitioning to Disconnected.Error would:
                        // 1. Trigger navigation to Settings (lost auth)
                        // 2. Clear stale data in MainDataSource
                        // 3. Show Loading screen on next attempt
                        Logger.withTag("ServiceClient")
                            .w { "Reconnect attempt failed (staying in Reconnecting): ${e.message}" }
                        // Don't update state - stay in Reconnecting!
                    }
                }
            }

            is SessionState.Disconnected -> {
                // Fresh connection - transition to Connecting
                _sessionState.update { SessionState.Connecting }
                launch {
                    try {
                        if (connection.isTls) {
                            client.wss(
                                HttpMethod.Get,
                                connection.host,
                                connection.port,
                                "/ws",
                            ) {
                                _sessionState.update {
                                    SessionState.Connected.Direct(
                                        this,
                                        connection
                                    )
                                }
                                settings.setLastConnectionMode("direct")
                                listenForMessages()
                            }
                        } else {
                            client.ws(
                                HttpMethod.Get,
                                connection.host,
                                connection.port,
                                "/ws",
                            ) {
                                _sessionState.update {
                                    SessionState.Connected.Direct(
                                        this,
                                        connection
                                    )
                                }
                                settings.setLastConnectionMode("direct")
                                listenForMessages()
                            }
                        }
                    } catch (e: Exception) {
                        _sessionState.update {
                            SessionState.Disconnected.Error(Exception("Connection failed: ${e.message}"))
                        }
                    }
                }
            }
        }
    }

    fun connectWebRTC(remoteId: RemoteId) {
        when (val currentState = _sessionState.value) {
            SessionState.Connecting,
            is SessionState.Connected -> return

            is SessionState.Reconnecting.WebRTC -> {
                Logger.withTag("ServiceClient")
                    .i { "🔄 RECONNECT ATTEMPT (WebRTC) - staying in Reconnecting state" }
                launch {
                    try {
                        val manager = getOrCreateWebRTCManager()
                        manager.connect(remoteId)

                        // Wait for connection to establish
                        launch {
                            manager.connectionState.collect { state ->
                                when (state) {
                                    is io.music_assistant.client.webrtc.model.WebRTCConnectionState.Connected -> {
                                        _sessionState.update {
                                            SessionState.Connected.WebRTC(
                                                manager = manager,
                                                remoteId = remoteId,
                                                serverInfo = currentState.serverInfo,
                                                user = currentState.user,
                                                authProcessState = currentState.authProcessState,
                                                wasAutoLogin = currentState.wasAutoLogin
                                            )
                                        }
                                        startWebRTCMessageListener(manager)
                                        settings.setLastConnectionMode("webrtc")
                                    }

                                    is io.music_assistant.client.webrtc.model.WebRTCConnectionState.Error -> {
                                        Logger.withTag("ServiceClient")
                                            .w { "WebRTC reconnect failed: ${state.error}" }
                                    }

                                    else -> {}
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Logger.withTag("ServiceClient")
                            .w { "WebRTC reconnect attempt failed: ${e.message}" }
                    }
                }
            }

            is SessionState.Reconnecting.Direct -> {
                // User switched modes during reconnection - not supported
                Logger.withTag("ServiceClient")
                    .w { "Cannot switch to WebRTC during Direct reconnection" }
                return
            }

            is SessionState.Disconnected -> {
                _sessionState.update { SessionState.Connecting }
                launch {
                    try {
                        val manager = getOrCreateWebRTCManager()
                        manager.connect(remoteId)

                        // Wait for connection state changes
                        launch {
                            manager.connectionState.collect { state ->
                                when (state) {
                                    is io.music_assistant.client.webrtc.model.WebRTCConnectionState.Connected -> {
                                        _sessionState.update {
                                            SessionState.Connected.WebRTC(
                                                manager = manager,
                                                remoteId = remoteId
                                            )
                                        }
                                        startWebRTCMessageListener(manager)
                                        settings.setLastConnectionMode("webrtc")
                                    }

                                    is io.music_assistant.client.webrtc.model.WebRTCConnectionState.Error -> {
                                        _sessionState.update {
                                            SessionState.Disconnected.Error(
                                                Exception("WebRTC connection failed: ${state.error}")
                                            )
                                        }
                                    }

                                    else -> {}
                                }
                            }
                        }
                    } catch (e: Exception) {
                        _sessionState.update {
                            SessionState.Disconnected.Error(Exception("WebRTC connection failed: ${e.message}"))
                        }
                    }
                }
            }
        }
    }

    private fun getOrCreateWebRTCManager(): WebRTCConnectionManager {
        // Recreate manager on each connection (as per user requirement #1)
        webrtcManager?.let {
            launch { it.disconnect() }
        }

        val signalingClient = SignalingClient(webrtcHttpClient, this)
        val manager = WebRTCConnectionManager(signalingClient, this)
        webrtcManager = manager
        return manager
    }

    private fun startWebRTCMessageListener(manager: WebRTCConnectionManager) {
        webrtcListeningJob?.cancel()
        webrtcListeningJob = launch {
            manager.incomingMessages.collect { jsonString ->
                Logger.d { "WebRTC received: ${jsonString.take(200)}..." }
                try {
                    val message = myJson.decodeFromString<JsonObject>(jsonString)
                    Logger.d { "WebRTC parsed, keys: ${message.keys}" }
                    handleIncomingMessage(message)
                } catch (e: Exception) {
                    Logger.e(e) { "Failed to parse WebRTC message: $jsonString" }
                }
            }
        }
    }

    suspend fun login(
        username: String,
        password: String,
    ) {
        if (_sessionState.value !is SessionState.Connected) {
            return
        }
        _sessionState.update {
            (it as? SessionState.Connected)?.update(authProcessState = AuthProcessState.InProgress)
                ?: it
        }

        try {
            val response =
                sendRequest(Request.Auth.login(username, password, settings.deviceName.value))
            if (_sessionState.value !is SessionState.Connected) {
                return
            }

            if (response.isFailure) {
                _sessionState.update {
                    (it as? SessionState.Connected)?.update(
                        authProcessState = AuthProcessState.Failed("No response from server")
                    ) ?: it
                }
                return
            }

            // Check for error in response
            if (response.getOrNull()?.json?.containsKey("error_code") == true) {
                val errorMessage =
                    response.getOrNull()?.json["error"]?.jsonPrimitive?.content
                        ?: "Authentication failed"
                settings.updateToken(null)
                _sessionState.update {
                    (it as? SessionState.Connected)?.update(
                        authProcessState = AuthProcessState.Failed(errorMessage)
                    ) ?: it
                }
                return
            }

            response.resultAs<LoginResponse>()?.let { auth ->
                if (!auth.success) {
                    _sessionState.update {
                        (it as? SessionState.Connected)?.update(
                            authProcessState = AuthProcessState.Failed(
                                auth.error ?: "Authentication failed"
                            )
                        ) ?: it
                    }
                    return
                }
                if (auth.token.isNullOrBlank()) {
                    _sessionState.update {
                        (it as? SessionState.Connected)?.update(
                            authProcessState = AuthProcessState.Failed("No token received")
                        ) ?: it
                    }
                    return
                }
                if (auth.user == null) {
                    _sessionState.update {
                        (it as? SessionState.Connected)?.update(
                            authProcessState = AuthProcessState.Failed("No user data received")
                        ) ?: it
                    }
                    return
                }
                authorize(auth.token)
            } ?: run {
                _sessionState.update {
                    (it as? SessionState.Connected)?.update(
                        authProcessState = AuthProcessState.Failed("Failed to parse auth data")
                    ) ?: it
                }
            }
        } catch (e: Exception) {
            if (_sessionState.value !is SessionState.Connected) {
                return
            }
            _sessionState.update {
                (it as? SessionState.Connected)?.update(
                    authProcessState = AuthProcessState.Failed(
                        e.message ?: "Exception happened: $e"
                    )
                ) ?: it
            }
            settings.updateToken(null)
        }
    }

    fun logout() {
        settings.updateToken(null)
        if (_sessionState.value !is SessionState.Connected) {
            return
        }
        // Update state synchronously
        _sessionState.update {
            (it as? SessionState.Connected)?.update(
                authProcessState = AuthProcessState.LoggedOut,
                user = null
            ) ?: it
        }
        // Fire and forget - send logout to server without waiting for response
        launch {
            try {
                sendRequest(Request.Auth.logout())
            } catch (_: Exception) {
                // Ignore errors - we're already logged out locally
            }
        }
    }

    suspend fun authorize(token: String, isAutoLogin: Boolean = false) {
        try {
            if (_sessionState.value !is SessionState.Connected) {
                return
            }
            _sessionState.update {
                (it as? SessionState.Connected)?.update(authProcessState = AuthProcessState.InProgress)
                    ?: it
            }
            val response = sendRequest(Request.Auth.authorize(token, settings.deviceName.value))
            if (_sessionState.value !is SessionState.Connected) {
                return
            }
            if (response.isFailure) {
                Logger.e(response.exceptionOrNull().toString())
                _sessionState.update {
                    (it as? SessionState.Connected)?.update(
                        authProcessState = AuthProcessState.Failed("No response from server")
                    ) ?: it
                }
                return
            }
            if (response.getOrNull()?.json?.containsKey("error_code") == true) {
                val errorMessage =
                    response.getOrNull()?.json["error"]?.jsonPrimitive?.content
                        ?: "Authentication failed"
                settings.updateToken(null)
                _sessionState.update {
                    (it as? SessionState.Connected)?.update(
                        authProcessState = AuthProcessState.Failed(errorMessage)
                    ) ?: it
                }
                return
            }
            response.resultAs<AuthorizationResponse>()?.user?.let { user ->
                settings.updateToken(token)
                _sessionState.update {
                    (it as? SessionState.Connected)?.update(
                        authProcessState = AuthProcessState.NotStarted,
                        user = user,
                        wasAutoLogin = isAutoLogin
                    ) ?: it
                }
            } ?: run {
                _sessionState.update {
                    (it as? SessionState.Connected)?.update(
                        authProcessState = AuthProcessState.Failed("Failed to parse user data")
                    ) ?: it
                }
            }
        } catch (e: Exception) {
            if (_sessionState.value !is SessionState.Connected) {
                return
            }
            _sessionState.update {
                (it as? SessionState.Connected)?.update(
                    authProcessState = AuthProcessState.Failed(
                        e.message ?: "Exception happened: $e"
                    )
                ) ?: it
            }
            settings.updateToken(null)
        }
    }

    private suspend fun handleIncomingMessage(message: JsonObject) {
        when {
            message.containsKey("message_id") -> {
                val commandAnswer = Answer(message)
                pendingResponses.remove(commandAnswer.messageId)?.invoke(commandAnswer)
            }

            message.containsKey("server_id") -> {
                _sessionState.update {
                    (it as? SessionState.Connected)?.update(
                        serverInfo = myJson.decodeFromJsonElement(message)
                    ) ?: it
                }
            }

            message.containsKey("event") -> {
                Event(message).event()?.let { _eventsFlow.emit(it) }
            }

            else -> Logger.withTag("ServiceClient").i { "Unknown message: $message" }
        }
    }

    private suspend fun listenForMessages() {
        try {
            while (true) {
                val state = _sessionState.value
                if (state !is SessionState.Connected.Direct) {
                    continue
                }
                val message = state.session.receiveDeserialized<JsonObject>()
                handleIncomingMessage(message)
            }
        } catch (e: Exception) {
            val state = _sessionState.value
            if (state is SessionState.Disconnected.ByUser) {
                return
            }
            if (state is SessionState.Connected.Direct) {
                Logger.withTag("ServiceClient")
                    .w { "Connection lost: ${e.message}. Will auto-reconnect..." }
                val connectionInfo = state.connectionInfo
                val serverInfo = state.serverInfo
                val user = state.user
                val authProcessState = state.authProcessState
                val wasAutoLogin = state.wasAutoLogin

                // Enter Reconnecting state (preserves server/user/auth state - no UI reload!)
                _sessionState.update {
                    SessionState.Reconnecting.Direct(
                        attempt = 0,
                        connectionInfo = connectionInfo,
                        serverInfo = serverInfo,
                        user = user,
                        authProcessState = authProcessState,
                        wasAutoLogin = wasAutoLogin
                    )
                }

                // Auto-reconnect with custom backoff schedule
                var reconnectAttempt = 0
                val maxAttempts = 10
                while (reconnectAttempt < maxAttempts) {
                    // Check if user manually disconnected - if so, stop reconnection loop
                    val currentState = _sessionState.value
                    if (currentState is SessionState.Disconnected.ByUser) {
                        Logger.withTag("ServiceClient")
                            .i { "User manually disconnected - stopping reconnection loop" }
                        return
                    }
                    if (currentState !is SessionState.Reconnecting) {
                        Logger.withTag("ServiceClient")
                            .i { "Session state changed to ${currentState::class.simpleName} - stopping reconnection loop" }
                        return
                    }

                    val delay = when (reconnectAttempt) {
                        0 -> 500L
                        1 -> 1000L
                        2 -> 2000L
                        3 -> 3000L
                        else -> 5000L
                    }

                    Logger.withTag("ServiceClient")
                        .i { "Reconnect attempt ${reconnectAttempt + 1}/$maxAttempts in ${delay}ms" }
                    delay(delay)

                    // Check again after delay in case user disconnected during sleep
                    if (_sessionState.value is SessionState.Disconnected.ByUser) {
                        Logger.withTag("ServiceClient")
                            .i { "User manually disconnected during delay - stopping reconnection loop" }
                        return
                    }

                    // CRITICAL: Read current connection info from settings
                    // This allows user to change server IP during reconnection
                    val currentConnectionInfo = settings.connectionInfo.value ?: connectionInfo

                    _sessionState.update {
                        SessionState.Reconnecting.Direct(
                            attempt = reconnectAttempt + 1,
                            connectionInfo = currentConnectionInfo,
                            serverInfo = serverInfo,
                            user = user,
                            authProcessState = authProcessState,
                            wasAutoLogin = wasAutoLogin
                        )
                    }

                    reconnectAttempt++

                    // Trigger connection attempt (async)
                    Logger.withTag("ServiceClient")
                        .i { "Attempting reconnection to ${currentConnectionInfo.host}:${currentConnectionInfo.port}..." }
                    connect(currentConnectionInfo)

                    // Wait a bit for connection to establish (or fail)
                    // The connect() method launches async, so we give it time to complete
                    delay(2000L)

                    // Check if we successfully connected
                    if (_sessionState.value is SessionState.Connected) {
                        Logger.withTag("ServiceClient").i { "Reconnection successful!" }
                        return
                    }

                    // Still in Reconnecting state - connection attempt failed, continue loop
                    Logger.withTag("ServiceClient")
                        .w { "Reconnect attempt $reconnectAttempt failed, will retry..." }

                    if (reconnectAttempt >= maxAttempts) {
                        Logger.withTag("ServiceClient")
                            .e { "Max reconnect attempts reached, giving up" }
                        disconnect(SessionState.Disconnected.Error(Exception("Failed to reconnect after $maxAttempts attempts")))
                        return
                    }
                }
            }
        }
    }

    suspend fun sendRequest(request: Request): Result<Answer> = suspendCoroutine { continuation ->
        pendingResponses[request.messageId] = { response ->
            if (response.json.contains("error_code")) {
                Logger.withTag("ServiceClient")
                    .e { "Error response for command ${request.command}: $response" }
                if (response.json["error_code"]?.jsonPrimitive?.int == 20) {
                    _sessionState.update {
                        (it as? SessionState.Connected)?.update(
                            user = null,
                            authProcessState = AuthProcessState.NotStarted
                        ) ?: it
                    }
                }
            }
            continuation.resume(Result.success(response))
        }
        launch {
            val state = _sessionState.value as? SessionState.Connected
                ?: run {
                    pendingResponses.remove(request.messageId)
                    continuation.resume(Result.failure(IllegalStateException("Not connected")))
                    return@launch
                }
            try {
                val jsonObject =
                    myJson.encodeToJsonElement(Request.serializer(), request) as JsonObject
                state.sendMessage(jsonObject)
            } catch (e: Exception) {
                pendingResponses.remove(request.messageId)
                continuation.resume(Result.failure(e))
                disconnect(SessionState.Disconnected.Error(Exception("Error sending command: ${e.message}")))
            }
        }
    }

    fun disconnectByUser() {
        disconnect(SessionState.Disconnected.ByUser)
    }


    private fun disconnect(newState: SessionState.Disconnected) {
        launch {
            when (val currentState = _sessionState.value) {
                is SessionState.Connected.Direct -> {
                    currentState.session.close()
                    _sessionState.update { newState }
                }

                is SessionState.Connected.WebRTC -> {
                    webrtcListeningJob?.cancel()
                    webrtcListeningJob = null
                    currentState.manager.disconnect()
                    _sessionState.update { newState }
                }

                is SessionState.Reconnecting -> {
                    // Already disconnected, just update state
                    _sessionState.update { newState }
                }

                else -> {
                    // Already disconnected or in some other state
                    _sessionState.update { newState }
                }
            }
        }
    }

    fun close() {
        supervisorJob.cancel()
        client.close()
    }
}
