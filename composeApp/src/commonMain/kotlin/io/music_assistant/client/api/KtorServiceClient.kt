package io.music_assistant.client.api

import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.pingInterval
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import io.music_assistant.client.data.model.server.AuthorizationResponse
import io.music_assistant.client.data.model.server.LoginResponse
import io.music_assistant.client.data.model.server.ServerInfo
import io.music_assistant.client.data.model.server.events.Event
import io.music_assistant.client.settings.ConnectionHistoryEntry
import io.music_assistant.client.settings.ConnectionType
import io.music_assistant.client.settings.SettingsRepository
import io.music_assistant.client.utils.AuthProcessState
import io.music_assistant.client.utils.ConnectionData
import io.music_assistant.client.utils.DataConnectionState
import io.music_assistant.client.utils.HasConnectionData
import io.music_assistant.client.utils.NetworkMonitor
import io.music_assistant.client.utils.SessionState
import io.music_assistant.client.utils.connectionInfo
import io.music_assistant.client.utils.createPlatformHttpClient
import io.music_assistant.client.utils.currentTimeMillis
import io.music_assistant.client.utils.myJson
import io.music_assistant.client.utils.resultAs
import io.music_assistant.client.utils.update
import io.music_assistant.client.webrtc.model.RemoteId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.qualifier.named
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.seconds

class KtorServiceClient(private val settings: SettingsRepository) : ServiceClient, CoroutineScope, KoinComponent {
    private val supervisorJob = SupervisorJob()
    override val coroutineContext: CoroutineContext = supervisorJob + Dispatchers.IO

    private val client = createPlatformHttpClient {
        install(WebSockets) {
            contentConverter = KotlinxWebsocketSerializationConverter(myJson)
            pingInterval = 10.seconds
        }
    }

    // WebRTC HTTP client - created lazily on first WebRTC connection
    private val webrtcHttpClient: HttpClient by inject(named("webrtcHttpClient"))

    private val networkMonitor: NetworkMonitor by inject()

    // --- Transport ---
    private var transport: Transport? = null
    private var transportObserverJob: Job? = null

    // --- Lifecycle / background state ---
    private var isInBackground = false
    private var hasActiveExternalConsumer = false
    private var hasActivePlayback = false
    private var backgroundedAt = 0L

    private sealed class BackgroundedConnectionInfo {
        data class Direct(val connectionInfo: ConnectionInfo) : BackgroundedConnectionInfo()
        data class WebRTC(val remoteId: RemoteId) : BackgroundedConnectionInfo()
    }

    private var backgroundedConnectionInfo: BackgroundedConnectionInfo? = null

    private var _sessionState: MutableStateFlow<SessionState> =
        MutableStateFlow(SessionState.Disconnected.Initial)
    override val sessionState = _sessionState.asStateFlow()

    override val serverBaseUrl: StateFlow<String?> = _sessionState
        .map { (it as? HasConnectionData)?.serverInfo?.baseUrl }
        .stateIn(this, SharingStarted.Eagerly, null)

    override val isReadyForCommands: StateFlow<Boolean> = _sessionState
        .map { it is SessionState.Connected && it.dataConnectionState == DataConnectionState.Authenticated }
        .stateIn(this, SharingStarted.Eagerly, false)

    private val _eventsFlow = MutableSharedFlow<Event<out Any>>(extraBufferCapacity = 10)
    override val events: Flow<Event<out Any>> = _eventsFlow.asSharedFlow()

    /**
     * WebRTC Sendspin data channel.
     * Available when connected via WebRTC, null otherwise.
     */
    override val webrtcSendspinChannel: io.music_assistant.client.webrtc.DataChannelWrapper?
        get() = (transport as? WebRTCTransport)?.sendspinDataChannel

    private val logger = Logger.withTag("ServiceClient")

    /**
     * Force a full WebRTC reconnection to get fresh SDP-negotiated data channels.
     */
    override fun forceWebRTCReconnect() {
        val currentState = _sessionState.value as? SessionState.Connected.WebRTC ?: return
        logger.i { "Forcing WebRTC reconnect for fresh sendspin channel" }

        // `connectionData` is carried verbatim so `Reconnecting.WebRTC` keeps the
        // current `user` for UI display during the reconnect window. The reauth-
        // sensitive fields (`serverInfo`, `needsServerReauth`, `authProcessState`)
        // get rebuilt by `observeTransport` when the new peer connection lands and
        // emits Connected; until then they're irrelevant because no collector acts
        // on Reconnecting state.
        _sessionState.update {
            SessionState.Reconnecting.WebRTC(
                attempt = 0,
                remoteId = currentState.remoteId,
                connectionData = currentState.connectionData,
            )
        }

        (transport as? WebRTCTransport)?.forceReconnect()
    }

    /**
     * Called when the app moves to the background.
     */
    override fun onAppBackground() {
        isInBackground = true
        backgroundedAt = currentTimeMillis()
        logger.i { "App backgrounded" }
    }

    /**
     * Called when an external consumer (Android Auto / CarPlay) becomes active.
     */
    override fun onExternalConsumerActive() {
        hasActiveExternalConsumer = true
        logger.i { "External consumer active" }

        if (_sessionState.value is SessionState.Disconnected.Backgrounded) {
            val savedInfo = backgroundedConnectionInfo ?: return
            backgroundedConnectionInfo = null
            logger.i { "Reconnecting for external consumer (was backgrounded)" }
            when (savedInfo) {
                is BackgroundedConnectionInfo.Direct -> {
                    val connInfo = settings.connectionInfo.value ?: savedInfo.connectionInfo
                    connect(connInfo)
                }

                is BackgroundedConnectionInfo.WebRTC -> {
                    connectWebRTC(savedInfo.remoteId)
                }
            }
        }
    }

    /**
     * Called when an external consumer (Android Auto / CarPlay) becomes inactive.
     */
    override fun onExternalConsumerInactive() {
        hasActiveExternalConsumer = false
        logger.i { "External consumer inactive" }
    }

    /**
     * Called when any player starts playing. Prevents background teardown.
     */
    override fun onPlaybackActive() {
        hasActivePlayback = true
        logger.i { "Playback active" }
    }

    /**
     * Called when all players stop playing.
     */
    override fun onPlaybackInactive() {
        hasActivePlayback = false
        logger.i { "Playback inactive" }
    }

    /**
     * Called when the app returns to the foreground.
     */
    override fun onAppForeground() {
        isInBackground = false
        logger.i { "App foregrounded" }

        val savedInfo = backgroundedConnectionInfo
        if (savedInfo != null) {
            backgroundedConnectionInfo = null
            logger.i { "Reconnecting after foreground (was backgrounded)" }
            when (savedInfo) {
                is BackgroundedConnectionInfo.Direct -> {
                    val connInfo = settings.connectionInfo.value ?: savedInfo.connectionInfo
                    connect(connInfo)
                }

                is BackgroundedConnectionInfo.WebRTC -> {
                    connectWebRTC(savedInfo.remoteId)
                }
            }
            return
        }

        // Connection appears alive — probe it if we've been in background long enough
        // for a half-open TCP zombie to form.
        val elapsed = currentTimeMillis() - backgroundedAt
        if (elapsed > STALE_CONNECTION_THRESHOLD_MS && _sessionState.value is SessionState.Connected) {
            logger.i { "Probing connection after ${elapsed}ms in background" }
            transport?.verifyConnection()
        }
    }

    private val rpcEngine = RpcEngine {
        _sessionState.update {
            (it as? SessionState.Connected)?.update(
                user = null,
                authProcessState = AuthProcessState.NotStarted,
            ) ?: it
        }
    }

    init {
        launch {
            _sessionState.collect { state ->
                when (state) {
                    is SessionState.Connected -> {
                        state.connectionInfo?.let { connInfo ->
                            settings.updateConnectionInfo(connInfo)
                        }
                    }

                    is SessionState.Reconnecting -> {
                        state.connectionInfo?.let { connInfo ->
                            settings.updateConnectionInfo(connInfo)
                        }
                    }

                    is SessionState.Disconnected -> {
                        when (state) {
                            SessionState.Disconnected.ByUser,
                            SessionState.Disconnected.NoServerData,
                            SessionState.Disconnected.Backgrounded,
                            is SessionState.Disconnected.Error,
                            -> Unit

                            SessionState.Disconnected.Initial -> {
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
                                        val remoteId =
                                            mostRecent.remoteId?.let { RemoteId.parse(it) }
                                        if (remoteId != null) {
                                            connectWebRTC(remoteId)
                                        } else {
                                            _sessionState.update { SessionState.Disconnected.NoServerData }
                                        }
                                    }

                                    else -> {
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

    // --- Transport state observation ---

    private fun observeTransport(
        transport: Transport,
        createConnected: (ConnectionData) -> SessionState.Connected,
        createReconnecting: (Int, ConnectionData) -> SessionState.Reconnecting,
        backgroundInfo: () -> BackgroundedConnectionInfo,
        onFreshConnect: () -> Unit,
        onReconnected: suspend () -> Unit,
        needsReauthOnReconnect: Boolean = false,
        clearsServerInfoOnReconnect: Boolean = false,
    ) {
        transportObserverJob?.cancel()
        transportObserverJob = launch {
            // State collector
            launch {
                transport.state.collect { transportState ->
                    when (transportState) {
                        TransportState.Connected -> {
                            val preserved =
                                (_sessionState.value as? HasConnectionData)?.connectionData
                                    ?: ConnectionData()
                            val wasReconnecting = _sessionState.value is SessionState.Reconnecting
                            // Both transports use per-connection auth state on the server,
                            // so every reconnect needs a fresh `client/auth`. Rather than
                            // call `authorize` from `onReconnected` (which races with the
                            // session-state emit — MainDataSource can see Authenticated
                            // and fire RPCs before the auth round-trip lands), flag
                            // `needsServerReauth` and let AuthenticationManager drive
                            // re-auth from the resulting `AwaitingAuth(NotStarted)` state.
                            // While the flag is set, `dataConnectionState` keeps gating
                            // RPCs even if `authorize` later fails — a `Failed` state with
                            // `needsServerReauth=true` still reports `AwaitingAuth(Failed)`,
                            // so MainDataSource's stale-data path holds the line until the
                            // user retries login or the transport bounces again.
                            //
                            // `serverInfo` handling differs by transport. WebRTC clears
                            // it because the gateway proxies the data channel onto a
                            // fresh local WebSocket and races the two legs: if
                            // `client/auth` is sent before the gateway has forwarded the
                            // new `server/hello` back through the (not-yet-`open`)
                            // channel, auth is silently lost. Clearing `serverInfo`
                            // forces `AwaitingServerInfo`, holding `authorize` until the
                            // fresh `server/hello` arrives. Direct WebSocket has no such
                            // ordering gate — the new `server/hello` will refresh the
                            // cached value over the same FIFO channel that carries
                            // `client/auth` next, so we leave `serverInfo` populated and
                            // let `AwaitingAuth` trigger immediately.
                            //
                            // `authProcessState` resets to `NotStarted` so AuthMgr's
                            // collector actually fires; a stale `InProgress` from a
                            // mid-flight auth at disconnect time would otherwise park
                            // the collector. `user` is preserved so the UI keeps
                            // showing the user as logged in throughout the reconnect.
                            val emitData = if (wasReconnecting && needsReauthOnReconnect) {
                                preserved.copy(
                                    serverInfo = if (clearsServerInfoOnReconnect) null else preserved.serverInfo,
                                    needsServerReauth = true,
                                    authProcessState = AuthProcessState.NotStarted,
                                )
                            } else {
                                preserved
                            }
                            _sessionState.update { createConnected(emitData) }
                            if (wasReconnecting) {
                                onReconnected()
                            } else {
                                onFreshConnect()
                            }
                        }

                        is TransportState.Reconnecting -> {
                            if (isInBackground && !hasActiveExternalConsumer && !hasActivePlayback) {
                                backgroundedConnectionInfo = backgroundInfo()
                                transport.disconnect()
                                _sessionState.update { SessionState.Disconnected.Backgrounded }
                                return@collect
                            }
                            val preserved =
                                (_sessionState.value as? HasConnectionData)?.connectionData
                                    ?: ConnectionData()
                            _sessionState.update {
                                createReconnecting(
                                    transportState.attempt,
                                    preserved,
                                )
                            }
                        }

                        is TransportState.Failed -> {
                            _sessionState.update { SessionState.Disconnected.Error(transportState.error) }
                        }

                        TransportState.Disconnected -> {
                            if (_sessionState.value !is SessionState.Disconnected) {
                                _sessionState.update { SessionState.Disconnected.ByUser }
                            }
                        }

                        TransportState.Connecting -> {} // already handled
                    }
                }
            }
            // Message collector
            launch {
                transport.messages.collect { handleIncomingMessage(it) }
            }
        }
    }

    // --- Connect / Disconnect ---

    override fun connect(connection: ConnectionInfo) {
        if (_sessionState.value is SessionState.Connecting || _sessionState.value is SessionState.Connected) return

        // Cancel observer before disconnecting transport to prevent race where the old
        // observer processes TransportState.Disconnected and briefly sets ByUser
        transportObserverJob?.cancel()
        transport?.disconnect()
        _sessionState.update { SessionState.Connecting }

        val directTransport = DirectTransport(
            client = client,
            connectionInfoProvider = { settings.connectionInfo.value ?: connection },
            scope = this,
            networkAvailable = networkMonitor.isAvailable,
        )
        transport = directTransport

        observeTransport(
            transport = directTransport,
            createConnected = { data ->
                val info = settings.connectionInfo.value ?: connection
                SessionState.Connected.Direct(info, data)
            },
            createReconnecting = { attempt, data ->
                val info = settings.connectionInfo.value ?: connection
                SessionState.Reconnecting.Direct(attempt, info, data)
            },
            backgroundInfo = { BackgroundedConnectionInfo.Direct(connection) },
            onFreshConnect = {
                settings.setLastConnectionMode("direct")
                settings.addOrUpdateHistoryEntry(
                    ConnectionHistoryEntry(
                        type = ConnectionType.DIRECT,
                        host = connection.host,
                        port = connection.port,
                        isTls = connection.isTls,
                    ),
                )
            },
            onReconnected = {
                // Re-auth is owned by AuthenticationManager (driven by the
                // `needsReauthOnReconnect = true` gate above); nothing to do here.
                logger.i { "Direct reconnection successful — awaiting AuthenticationManager re-auth" }
            },
            needsReauthOnReconnect = true,
            // serverInfo stays populated: Direct WS has no `server/hello`-before-`client/auth`
            // ordering gate (unlike the WebRTC gateway), and the next `server/hello` will
            // overwrite any stale cached value over the same FIFO channel that carries auth.
            clearsServerInfoOnReconnect = false,
        )
        directTransport.connect()
    }

    override fun connectWebRTC(remoteId: RemoteId) {
        if (_sessionState.value is SessionState.Connecting || _sessionState.value is SessionState.Connected) return

        transportObserverJob?.cancel()
        transport?.disconnect()
        _sessionState.update { SessionState.Connecting }

        val webrtcTransport = WebRTCTransport(
            httpClient = webrtcHttpClient,
            remoteId = remoteId,
            scope = this,
            networkAvailable = networkMonitor.isAvailable,
        )
        transport = webrtcTransport

        observeTransport(
            transport = webrtcTransport,
            createConnected = { data -> SessionState.Connected.WebRTC(remoteId, data) },
            createReconnecting = { attempt, data ->
                SessionState.Reconnecting.WebRTC(attempt, remoteId, data)
            },
            backgroundInfo = { BackgroundedConnectionInfo.WebRTC(remoteId) },
            onFreshConnect = {
                settings.setLastConnectionMode("webrtc")
                settings.addOrUpdateHistoryEntry(
                    ConnectionHistoryEntry(
                        type = ConnectionType.WEBRTC,
                        remoteId = remoteId.rawId,
                    ),
                )
            },
            onReconnected = {
                // Re-auth is owned by AuthenticationManager (driven by the
                // `needsReauthOnReconnect = true` gate above); nothing to do here.
                logger.i { "WebRTC reconnection successful — awaiting AuthenticationManager re-auth" }
            },
            needsReauthOnReconnect = true,
            // serverInfo cleared: WebRTC gateway races data channel `on_message`
            // for `client/auth` against forwarding the new `server/hello`. Forcing
            // `AwaitingServerInfo` until the fresh hello arrives sidesteps the race.
            clearsServerInfoOnReconnect = true,
        )
        webrtcTransport.connect()
    }

    override fun disconnectByUser() {
        disconnect(SessionState.Disconnected.ByUser)
    }

    private fun disconnect(newState: SessionState.Disconnected) {
        launch {
            if (newState is SessionState.Disconnected.Backgrounded &&
                (!isInBackground || hasActiveExternalConsumer || hasActivePlayback)
            ) {
                logger.i { "Backgrounded disconnect aborted — app already foregrounded" }
                return@launch
            }

            transportObserverJob?.cancel()
            transportObserverJob = null
            transport?.disconnect()
            transport = null
            _sessionState.update { newState }
            rpcEngine.clear()
        }
    }

    // --- Auth ---

    private fun setAuthState(newState: AuthProcessState) {
        _sessionState.update { state ->
            (state as? SessionState.Connected)?.update(authProcessState = newState) ?: state
        }
    }

    /**
     * Marks the current auth attempt as failed. Intentionally does NOT clear
     * `needsServerReauth` — if the failure happened on a transport reconnect,
     * the underlying server-side session is still invalid and only a successful
     * `authorize` round-trip can clear the flag. With the flag set, the state
     * stays `AwaitingAuth(Failed)` (per `ConnectionData.dataConnectionState`),
     * which holds MainDataSource on stale data instead of letting it fire RPCs
     * that would 401 against the unauthenticated session. Recovery is either a
     * manual re-login (success path clears the flag) or another transport
     * bounce (which resets `authProcessState` to `NotStarted` so AuthMgr
     * re-fires).
     */
    private fun setAuthFailed(reason: String) = setAuthState(AuthProcessState.Failed(reason))

    override suspend fun login(
        username: String,
        password: String,
    ) {
        if (_sessionState.value !is SessionState.Connected) return
        setAuthState(AuthProcessState.InProgress)

        try {
            val response =
                sendRequest(Request.Auth.login(username, password, settings.deviceName.value))
            if (_sessionState.value !is SessionState.Connected) return

            if (response.isFailure) {
                setAuthFailed("No response from server")
                return
            }

            if (response.getOrNull()?.json?.containsKey("error_code") == true) {
                val errorMessage =
                    response.getOrNull()?.json["error"]?.jsonPrimitive?.content
                        ?: "Authentication failed"
                clearCurrentServerToken()
                setAuthFailed(errorMessage)
                return
            }

            response.resultAs<LoginResponse>()?.let { auth ->
                if (!auth.success) {
                    setAuthFailed(auth.error ?: "Authentication failed")
                    return
                }
                if (auth.token.isNullOrBlank()) {
                    setAuthFailed("No token received")
                    return
                }
                if (auth.user == null) {
                    setAuthFailed("No user data received")
                    return
                }
                authorize(auth.token, isAutoLogin = false)
            } ?: run {
                setAuthFailed("Failed to parse auth data")
            }
        } catch (e: Exception) {
            if (_sessionState.value !is SessionState.Connected) return
            setAuthFailed(e.message ?: "Exception happened: $e")
            clearCurrentServerToken()
        }
    }

    override fun logout() {
        val currentState = _sessionState.value
        if (currentState is SessionState.Connected) {
            val serverIdentifier = when (currentState) {
                is SessionState.Connected.Direct -> {
                    settings.getDirectServerIdentifier(
                        currentState.connectionInfo.host,
                        currentState.connectionInfo.port,
                        currentState.connectionInfo.isTls,
                    )
                }

                is SessionState.Connected.WebRTC -> {
                    settings.getWebRTCServerIdentifier(currentState.remoteId.rawId)
                }
            }
            settings.setTokenForServer(serverIdentifier, null)
            logger.d { "Cleared token for server" }
        }

        if (_sessionState.value !is SessionState.Connected) return
        _sessionState.update {
            (it as? SessionState.Connected)?.update(
                authProcessState = AuthProcessState.LoggedOut,
                user = null,
            ) ?: it
        }
        launch {
            try {
                sendRequest(Request.Auth.logout())
            } catch (_: Exception) {
            }
        }
    }

    override suspend fun authorize(token: String, isAutoLogin: Boolean) {
        try {
            if (_sessionState.value !is SessionState.Connected) return
            setAuthState(AuthProcessState.InProgress)
            val response = sendRequest(Request.Auth.authorize(token, settings.deviceName.value))
            if (_sessionState.value !is SessionState.Connected) return
            if (response.isFailure) {
                Logger.e(response.exceptionOrNull().toString())
                setAuthFailed("No response from server")
                return
            }
            if (response.getOrNull()?.json?.containsKey("error_code") == true) {
                val errorMessage =
                    response.getOrNull()?.json["error"]?.jsonPrimitive?.content
                        ?: "Authentication failed"
                clearCurrentServerToken()
                setAuthFailed(errorMessage)
                return
            }
            response.resultAs<AuthorizationResponse>()?.user?.let { user ->
                val currentState = _sessionState.value
                if (currentState is SessionState.Connected) {
                    val serverIdentifier = when (currentState) {
                        is SessionState.Connected.Direct -> {
                            settings.getDirectServerIdentifier(
                                currentState.connectionInfo.host,
                                currentState.connectionInfo.port,
                                currentState.connectionInfo.isTls,
                            )
                        }

                        is SessionState.Connected.WebRTC -> {
                            settings.getWebRTCServerIdentifier(currentState.remoteId.rawId)
                        }
                    }
                    settings.setTokenForServer(serverIdentifier, token)
                    logger.d { "Saved token for server" }
                }

                _sessionState.update {
                    (it as? SessionState.Connected)?.update(
                        authProcessState = AuthProcessState.NotStarted,
                        user = user,
                        wasAutoLogin = isAutoLogin,
                        needsServerReauth = false,
                    ) ?: it
                }
            } ?: run {
                setAuthFailed("Failed to parse user data")
            }
        } catch (e: Exception) {
            if (_sessionState.value !is SessionState.Connected) return
            setAuthFailed(e.message ?: "Exception happened: $e")
            clearCurrentServerToken()
        }
    }

    private fun clearCurrentServerToken() {
        val currentState = _sessionState.value
        if (currentState is SessionState.Connected) {
            val serverIdentifier = when (currentState) {
                is SessionState.Connected.Direct -> {
                    settings.getDirectServerIdentifier(
                        currentState.connectionInfo.host,
                        currentState.connectionInfo.port,
                        currentState.connectionInfo.isTls,
                    )
                }

                is SessionState.Connected.WebRTC -> {
                    settings.getWebRTCServerIdentifier(currentState.remoteId.rawId)
                }
            }
            settings.setTokenForServer(serverIdentifier, null)
            logger.d { "Cleared token for server due to auth failure" }
        }
    }

    // --- Messaging ---

    private suspend fun handleIncomingMessage(message: JsonObject) {
        when {
            rpcEngine.handleResponse(message) -> return

            message.containsKey("server_id") -> {
                val serverInfo = try {
                    myJson.decodeFromJsonElement<ServerInfo>(message)
                } catch (e: SerializationException) {
                    logger.w(e) {
                        "Failed to decode ServerInfo handshake: ${message.toString().take(500)}"
                    }
                    null
                }
                if (serverInfo != null) {
                    _sessionState.update {
                        (it as? SessionState.Connected)?.update(serverInfo = serverInfo) ?: it
                    }
                }
            }

            message.containsKey("event") -> {
                Event(message).event()?.let { _eventsFlow.emit(it) }
            }

            else -> logger.i { "Unknown message: $message" }
        }
    }

    override suspend fun sendRequest(request: Request): Result<Answer> =
        suspendCancellableCoroutine { continuation ->
            rpcEngine.registerCallback(request.messageId) { response ->
                continuation.resume(Result.success(response))
            }
            launch {
                val t = transport ?: run {
                    rpcEngine.removeCallback(request.messageId)
                    continuation.resume(Result.failure(IllegalStateException("Not connected")))
                    return@launch
                }
                try {
                    val jsonObject =
                        myJson.encodeToJsonElement(Request.serializer(), request) as JsonObject
                    t.send(jsonObject)
                } catch (e: Exception) {
                    logger.e(e) { "sendRequest FAILED cmd=${request.command}" }
                    rpcEngine.removeCallback(request.messageId)
                    continuation.resume(Result.failure(e))
                    // Don't trigger full disconnect if transport is already reconnecting
                    val transportState = transport?.state?.value
                    if (transportState !is TransportState.Reconnecting) {
                        disconnect(SessionState.Disconnected.Error(Exception("Error sending command: ${e.message}")))
                    }
                }
            }
        }

    fun close() {
        supervisorJob.cancel()
        client.close()
    }

    companion object {
        private const val STALE_CONNECTION_THRESHOLD_MS = 30_000L
    }
}
