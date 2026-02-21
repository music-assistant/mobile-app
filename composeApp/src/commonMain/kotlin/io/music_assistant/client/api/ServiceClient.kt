package io.music_assistant.client.api

import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.ws
import io.ktor.client.plugins.websocket.wss
import io.ktor.http.HttpMethod
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import io.ktor.websocket.close
import io.music_assistant.client.data.model.server.AuthorizationResponse
import io.music_assistant.client.data.model.server.LoginResponse
import io.music_assistant.client.data.model.server.events.Event
import io.music_assistant.client.settings.ConnectionHistoryEntry
import io.music_assistant.client.settings.ConnectionType
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
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
    private var webrtcStateMonitorJob: Job? = null
    private var webrtcInitialMonitorJob: Job? =
        null  // Temp monitor during connection, cancelled when message listener starts
    private var webrtcReconnectJob: Job? =
        null  // Active reconnection job, cancelled when connection succeeds or user disconnects

    // Cache last successful WebRTC connection for reconnection
    // Needed because state may transition to Error before monitor can extract info (race with MainDataSource)
    private data class WebRTCConnectionCache(
        val remoteId: RemoteId,
        val serverInfo: io.music_assistant.client.data.model.server.ServerInfo?,
        val user: io.music_assistant.client.data.model.server.User?,
        val authProcessState: AuthProcessState,
        val wasAutoLogin: Boolean
    )

    private var lastWebRTCConnection: WebRTCConnectionCache? = null

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

    private val rpcEngine = RpcEngine {
        _sessionState.update {
            (it as? SessionState.Connected)?.update(
                user = null,
                authProcessState = AuthProcessState.NotStarted
            ) ?: it
        }
    }

    init {
        launch {
            _sessionState.collect { state ->
                when (state) {
                    is SessionState.Connected -> {
                        state.connectionInfo?.let { connInfo ->
                            settings.updateConnectionInfo(
                                connInfo
                            )
                        }
                    }

                    is SessionState.Reconnecting -> {
                        // Keep connection info during reconnection (no UI reload)
                        state.connectionInfo?.let { connInfo ->
                            settings.updateConnectionInfo(
                                connInfo
                            )
                        }
                    }

                    is SessionState.Disconnected -> {
                        listeningJob?.cancel()
                        listeningJob = null
                        when (state) {
                            SessionState.Disconnected.ByUser,
                            SessionState.Disconnected.NoServerData,
                            is SessionState.Disconnected.Error -> Unit

                            SessionState.Disconnected.Initial -> {
                                // Auto-connect using the most recent history entry
                                val mostRecent = settings.connectionHistory.value.firstOrNull()
                                when (mostRecent?.type) {
                                    ConnectionType.DIRECT -> {
                                        val connInfo = mostRecent.connectionInfo
                                        if (connInfo != null) {
                                            connect(connInfo)
                                        } else {
                                            _sessionState.update { SessionState.Disconnected.NoServerData }
                                        }
                                    }
                                    ConnectionType.WEBRTC -> {
                                        val remoteId = mostRecent.remoteId?.let { RemoteId.parse(it) }
                                        if (remoteId != null) {
                                            connectWebRTC(remoteId)
                                        } else {
                                            _sessionState.update { SessionState.Disconnected.NoServerData }
                                        }
                                    }
                                    else -> {
                                        // No history - fall back to legacy connection info
                                        settings.connectionInfo.value?.let { connect(it) }
                                            ?: _sessionState.update { SessionState.Disconnected.NoServerData }
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
                                listenForMessages(WebSocketConnectionSession(this))
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
                                listenForMessages(WebSocketConnectionSession(this))
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
                                settings.addOrUpdateHistoryEntry(ConnectionHistoryEntry(
                                    type = ConnectionType.DIRECT,
                                    host = connection.host,
                                    port = connection.port,
                                    isTls = connection.isTls,
                                ))
                                listenForMessages(WebSocketConnectionSession(this))
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
                                settings.addOrUpdateHistoryEntry(ConnectionHistoryEntry(
                                    type = ConnectionType.DIRECT,
                                    host = connection.host,
                                    port = connection.port,
                                    isTls = connection.isTls,
                                ))
                                listenForMessages(WebSocketConnectionSession(this))
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
                        webrtcInitialMonitorJob = launch {
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
                                        // Cache connection info for reconnection (before MainDataSource can transition to Error)
                                        lastWebRTCConnection = WebRTCConnectionCache(
                                            remoteId = remoteId,
                                            serverInfo = currentState.serverInfo,
                                            user = currentState.user,
                                            authProcessState = currentState.authProcessState,
                                            wasAutoLogin = currentState.wasAutoLogin
                                        )

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

                        // Monitor initial connection attempt only
                        // Once connected, startWebRTCMessageListener() takes over state monitoring
                        webrtcInitialMonitorJob = launch {
                            manager.connectionState.collect { managerState ->
                                when (managerState) {
                                    is io.music_assistant.client.webrtc.model.WebRTCConnectionState.Connected -> {
                                        _sessionState.update {
                                            SessionState.Connected.WebRTC(
                                                manager = manager,
                                                remoteId = remoteId
                                            )
                                        }
                                        startWebRTCMessageListener(manager)
                                        settings.setLastConnectionMode("webrtc")
                                        settings.addOrUpdateHistoryEntry(ConnectionHistoryEntry(
                                            type = ConnectionType.WEBRTC,
                                            remoteId = remoteId.rawId,
                                        ))
                                        // Stop this monitor - message listener will take over
                                        return@collect
                                    }

                                    is io.music_assistant.client.webrtc.model.WebRTCConnectionState.Error -> {
                                        // Initial connection failed - just fail, don't reconnect
                                        _sessionState.update {
                                            SessionState.Disconnected.Error(
                                                Exception("WebRTC connection failed: ${managerState.error}")
                                            )
                                        }
                                        return@collect
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

    private suspend fun getOrCreateWebRTCManager(): WebRTCConnectionManager {
        // Clean up old manager completely before creating new one
        webrtcManager?.let { oldManager ->
            Logger.withTag("ServiceClient")
                .d { "🧹 Cleaning up old WebRTC manager [${oldManager.hashCode()}]" }
            webrtcListeningJob?.cancel()
            webrtcListeningJob = null
            webrtcStateMonitorJob?.cancel()
            webrtcStateMonitorJob = null
            webrtcInitialMonitorJob?.cancel()
            webrtcInitialMonitorJob = null
            Logger.withTag("ServiceClient")
                .d { "🧹 Disconnecting old manager [${oldManager.hashCode()}]" }
            oldManager.disconnect()
            Logger.withTag("ServiceClient")
                .d { "🧹 Old manager [${oldManager.hashCode()}] cleaned up" }
        }

        val signalingClient = SignalingClient(webrtcHttpClient, this)
        val manager = WebRTCConnectionManager(signalingClient, this)
        webrtcManager = manager
        Logger.withTag("ServiceClient").d { "✨ Created new WebRTC manager [${manager.hashCode()}]" }
        return manager
    }

    private fun startWebRTCMessageListener(manager: WebRTCConnectionManager) {
        // Cancel previous jobs to prevent leaks and race conditions
        webrtcListeningJob?.cancel()
        webrtcStateMonitorJob?.cancel()
        webrtcInitialMonitorJob?.cancel()  // Critical: stop initial monitor that sets Disconnected.Error

        webrtcListeningJob = launch {
            try {
                listenOnSession(WebRTCConnectionSession(manager))
            } catch (e: Exception) {
                // Don't trigger reconnection here - the state monitor will handle it
                Logger.withTag("ServiceClient")
                    .d { "WebRTC message listener ended: ${e.message}" }
            }
        }

        // Monitor WebRTC connection state for failures
        webrtcStateMonitorJob = launch {
            manager.connectionState.collect { connectionState ->
                Logger.withTag("ServiceClient")
                    .w { "🔍 MONITOR[${manager.hashCode()}]: ${connectionState::class.simpleName}" }
                when (connectionState) {
                    is io.music_assistant.client.webrtc.model.WebRTCConnectionState.Error -> {
                        Logger.withTag("ServiceClient")
                            .w { "🚨 MONITOR[${manager.hashCode()}] triggering reconnection for error: ${connectionState.error}" }
                        Logger.withTag("ServiceClient")
                            .w { "🚨 ERROR: ${connectionState.error}" }
                        val currentState = _sessionState.value
                        Logger.withTag("ServiceClient")
                            .w { "📊 STATE: ${currentState::class.simpleName}" }

                        // Skip reconnection if user manually disconnected
                        if (currentState is SessionState.Disconnected.ByUser) {
                            Logger.withTag("ServiceClient")
                                .w { "🛑 SKIP: User disconnected" }
                            return@collect
                        }

                        // Extract connection info: try current state first, fall back to cache
                        // Cache handles race condition where state transitions to Error before we check
                        val info: WebRTCConnectionCache? = when (currentState) {
                            is SessionState.Connected.WebRTC -> {
                                // State still Connected - extract info directly
                                WebRTCConnectionCache(
                                    remoteId = currentState.remoteId,
                                    serverInfo = currentState.serverInfo,
                                    user = currentState.user,
                                    authProcessState = currentState.authProcessState,
                                    wasAutoLogin = currentState.wasAutoLogin
                                )
                            }

                            else -> {
                                // State already changed (race condition) - use cached info
                                lastWebRTCConnection
                            }
                        }

                        if (info != null) {
                            // Check if reconnection already running
                            if (webrtcReconnectJob?.isActive == true) {
                                Logger.withTag("ServiceClient")
                                    .w { "🛑 SKIP: Reconnection already in progress" }
                                return@collect
                            }

                            val source =
                                if (currentState is SessionState.Connected.WebRTC) "current state" else "cache"
                            Logger.withTag("ServiceClient")
                                .w { "🔄 RECONNECT: Starting (from $source)" }
                            Logger.withTag("ServiceClient")
                                .w { "WebRTC error: ${connectionState.error}. Will auto-reconnect..." }

                            // Enter Reconnecting state
                            _sessionState.update {
                                SessionState.Reconnecting.WebRTC(
                                    attempt = 0,
                                    remoteId = info.remoteId,
                                    serverInfo = info.serverInfo,
                                    user = info.user,
                                    authProcessState = info.authProcessState,
                                    wasAutoLogin = info.wasAutoLogin
                                )
                            }

                            // Cancel any stale reconnection job (defensive)
                            webrtcReconnectJob?.cancel()

                            // Launch reconnection in ServiceClient scope to survive monitor cancellation
                            // (getOrCreateWebRTCManager cancels webrtcStateMonitorJob)
                            // CRITICAL: Use this@ServiceClient.launch, NOT launch (which would create child of monitor job)
                            webrtcReconnectJob = this@ServiceClient.launch {
                                autoReconnectWebRTC(
                                    info.remoteId,
                                    info.serverInfo,
                                    info.user,
                                    info.authProcessState,
                                    info.wasAutoLogin
                                )
                            }
                        } else {
                            Logger.withTag("ServiceClient")
                                .w { "🛑 SKIP: No connection info in state or cache" }
                        }
                    }

                    else -> {
                        // Other states handled in connectWebRTC()
                    }
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
                clearCurrentServerToken()
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
                authorize(auth.token, isAutoLogin = false)
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
            clearCurrentServerToken()
        }
    }

    fun logout() {
        // Clear token for current server
        val currentState = _sessionState.value
        if (currentState is SessionState.Connected) {
            val serverIdentifier = when (currentState) {
                is SessionState.Connected.Direct -> {
                    settings.getDirectServerIdentifier(
                        currentState.connectionInfo.host,
                        currentState.connectionInfo.port,
                        currentState.connectionInfo.isTls
                    )
                }

                is SessionState.Connected.WebRTC -> {
                    settings.getWebRTCServerIdentifier(currentState.remoteId.rawId)
                }
            }
            settings.setTokenForServer(serverIdentifier, null)
            Logger.withTag("ServiceClient").d { "Cleared token for server: $serverIdentifier" }
        }

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
                clearCurrentServerToken()
                _sessionState.update {
                    (it as? SessionState.Connected)?.update(
                        authProcessState = AuthProcessState.Failed(errorMessage)
                    ) ?: it
                }
                return
            }
            response.resultAs<AuthorizationResponse>()?.user?.let { user ->
                // Save token for current server
                val currentState = _sessionState.value
                if (currentState is SessionState.Connected) {
                    val serverIdentifier = when (currentState) {
                        is SessionState.Connected.Direct -> {
                            settings.getDirectServerIdentifier(
                                currentState.connectionInfo.host,
                                currentState.connectionInfo.port,
                                currentState.connectionInfo.isTls
                            )
                        }

                        is SessionState.Connected.WebRTC -> {
                            settings.getWebRTCServerIdentifier(currentState.remoteId.rawId)
                        }
                    }
                    settings.setTokenForServer(serverIdentifier, token)
                    Logger.withTag("ServiceClient")
                        .d { "Saved token for server: $serverIdentifier" }
                }

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
            clearCurrentServerToken()
        }
    }

    /**
     * Auto-reconnect WebRTC connection with exponential backoff.
     * Matches the reconnection behaviour of Direct connections.
     */
    private suspend fun autoReconnectWebRTC(
        remoteId: RemoteId,
        serverInfo: io.music_assistant.client.data.model.server.ServerInfo?,
        user: io.music_assistant.client.data.model.server.User?,
        authProcessState: AuthProcessState,
        wasAutoLogin: Boolean
    ) {
        Logger.withTag("ServiceClient")
            .i { "🔄 AUTO-RECONNECT WebRTC started for ${remoteId.rawId}" }

        runReconnectionLoop(
            tag = "WebRTC",
            maxAttempts = 10,
            waitAfterConnectMs = 30_000L,
            shouldStop = {
                _sessionState.value.let {
                    it is SessionState.Disconnected.ByUser || it !is SessionState.Reconnecting.WebRTC
                }
            },
            isConnected = { _sessionState.value is SessionState.Connected.WebRTC },
            onAttempt = { attempt ->
                _sessionState.update {
                    SessionState.Reconnecting.WebRTC(
                        attempt = attempt + 1,
                        remoteId = remoteId,
                        serverInfo = serverInfo,
                        user = user,
                        authProcessState = authProcessState,
                        wasAutoLogin = wasAutoLogin
                    )
                }
                try {
                    connectWebRTC(remoteId)
                } catch (e: Exception) {
                    Logger.withTag("ServiceClient")
                        .w(e) { "WebRTC reconnect attempt ${attempt + 1} threw exception" }
                }
            },
            onGiveUp = {
                disconnect(SessionState.Disconnected.Error(Exception("Max WebRTC reconnect attempts reached")))
            }
        )

        // WebRTC-specific: re-authenticate with saved token after successful reconnection
        if (_sessionState.value is SessionState.Connected.WebRTC) {
            Logger.withTag("ServiceClient")
                .i { "✅ WebRTC reconnection successful! Re-authenticating..." }
            launch {
                val serverIdentifier = settings.getWebRTCServerIdentifier(remoteId.rawId)
                val token = settings.getTokenForServer(serverIdentifier)

                if (token != null) {
                    Logger.withTag("ServiceClient")
                        .i { "🔐 Re-authenticating after WebRTC reconnection with saved token" }
                    authorize(token, isAutoLogin = true)
                } else {
                    Logger.withTag("ServiceClient")
                        .w { "⚠️ No saved token to re-authenticate with for WebRTC server: $serverIdentifier" }
                }
            }
        }
        Logger.withTag("ServiceClient").i { "🏁 autoReconnectWebRTC() returning" }
    }

    /**
     * Clear authentication token for the currently connected server.
     * Also clears legacy global token for backward compatibility.
     */
    private fun clearCurrentServerToken() {
        val currentState = _sessionState.value
        if (currentState is SessionState.Connected) {
            val serverIdentifier = when (currentState) {
                is SessionState.Connected.Direct -> {
                    settings.getDirectServerIdentifier(
                        currentState.connectionInfo.host,
                        currentState.connectionInfo.port,
                        currentState.connectionInfo.isTls
                    )
                }

                is SessionState.Connected.WebRTC -> {
                    settings.getWebRTCServerIdentifier(currentState.remoteId.rawId)
                }
            }
            settings.setTokenForServer(serverIdentifier, null)
            Logger.withTag("ServiceClient")
                .d { "Cleared token for server: $serverIdentifier due to auth failure" }
        }
    }

    private suspend fun handleIncomingMessage(message: JsonObject) {
        when {
            rpcEngine.handleResponse(message) -> return

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

    /** Collect messages from a [ConnectionSession] and dispatch each to [handleIncomingMessage]. */
    private suspend fun listenOnSession(session: ConnectionSession) {
        session.messages.collect { handleIncomingMessage(it) }
    }

    /**
     * Listen on a Direct WebSocket session. When the connection drops, enters the
     * Reconnecting state and retries with exponential backoff.
     */
    private suspend fun listenForMessages(session: ConnectionSession) {
        try {
            listenOnSession(session)
        } catch (e: Exception) {
            Logger.withTag("ServiceClient").w { "WebSocket error: ${e.message}" }
        }

        // Connection ended (normal close or error) - check if reconnection is needed.
        val state = _sessionState.value
        if (state is SessionState.Disconnected.ByUser) return
        if (state !is SessionState.Connected.Direct) return

        Logger.withTag("ServiceClient").w { "Connection lost. Will auto-reconnect..." }
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

        runReconnectionLoop(
            tag = "Direct",
            waitAfterConnectMs = 2000L,
            shouldStop = {
                _sessionState.value.let {
                    it is SessionState.Disconnected.ByUser || it !is SessionState.Reconnecting
                }
            },
            isConnected = { _sessionState.value is SessionState.Connected },
            onAttempt = { attempt ->
                // Read current connection info from settings to allow IP changes during reconnection
                val info = settings.connectionInfo.value ?: connectionInfo
                _sessionState.update {
                    SessionState.Reconnecting.Direct(
                        attempt = attempt + 1,
                        connectionInfo = info,
                        serverInfo = serverInfo,
                        user = user,
                        authProcessState = authProcessState,
                        wasAutoLogin = wasAutoLogin
                    )
                }
                connect(info)
            },
            onGiveUp = {
                disconnect(SessionState.Disconnected.Error(Exception("Failed to reconnect after 10 attempts")))
            }
        )
    }

    suspend fun sendRequest(request: Request): Result<Answer> = suspendCoroutine { continuation ->
        rpcEngine.registerCallback(request.messageId) { response ->
            continuation.resume(Result.success(response))
        }
        launch {
            val state = _sessionState.value as? SessionState.Connected
                ?: run {
                    rpcEngine.removeCallback(request.messageId)
                    continuation.resume(Result.failure(IllegalStateException("Not connected")))
                    return@launch
                }
            try {
                val jsonObject =
                    myJson.encodeToJsonElement(Request.serializer(), request) as JsonObject
                state.sendMessage(jsonObject)
            } catch (e: Exception) {
                rpcEngine.removeCallback(request.messageId)
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
                    webrtcInitialMonitorJob?.cancel()
                    webrtcInitialMonitorJob = null
                    webrtcReconnectJob?.cancel()
                    webrtcReconnectJob = null
                    currentState.manager.disconnect()
                    _sessionState.update { newState }

                    // Clear cache on manual disconnect to prevent unwanted reconnection
                    if (newState is SessionState.Disconnected.ByUser) {
                        lastWebRTCConnection = null
                    }
                }

                is SessionState.Reconnecting -> {
                    // Cancel any active reconnection attempt
                    webrtcReconnectJob?.cancel()
                    webrtcReconnectJob = null
                    _sessionState.update { newState }
                }

                else -> {
                    // Already disconnected or in some other state (including Connecting)
                    // Cancel any active WebRTC jobs
                    webrtcInitialMonitorJob?.cancel()
                    webrtcInitialMonitorJob = null
                    webrtcReconnectJob?.cancel()
                    webrtcReconnectJob = null
                    _sessionState.update { newState }
                }
            }
            // Clear pending requests and partial result accumulations to prevent leaks
            rpcEngine.clear()
        }
    }

    fun close() {
        supervisorJob.cancel()
        client.close()
    }
}
