package io.music_assistant.client.api

import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.pingInterval
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.appendPathSegments
import io.ktor.http.encodeURLPathPart
import io.ktor.http.encodeURLQueryComponent
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import io.music_assistant.client.data.model.server.AuthorizationResponse
import io.music_assistant.client.data.model.server.LoginResponse
import io.music_assistant.client.data.model.server.ServerInfo
import io.music_assistant.client.data.model.server.events.Event
import io.music_assistant.client.imageloader.ARTWORK_DECODE_SIZE
import io.music_assistant.client.imageloader.ImageCacheInvalidator
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
import io.music_assistant.client.utils.getServerIdentifier
import io.music_assistant.client.utils.myJson
import io.music_assistant.client.utils.update
import io.music_assistant.client.webrtc.model.RemoteId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.qualifier.named
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.seconds

class KtorServiceClient(
    private val settings: SettingsRepository,
    private val errorBus: ErrorMessageBus,
) : ServiceClient, CoroutineScope, KoinComponent {
    private val logger = Logger.withTag("ServiceClient")
    private val supervisorJob = SupervisorJob()

    private val scopeExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        logger.e(throwable) { "Uncaught exception in KtorServiceClient scope (non-transport)" }
    }

    override val coroutineContext: CoroutineContext =
        supervisorJob + Dispatchers.IO + scopeExceptionHandler

    private val client = createPlatformHttpClient {
        install(WebSockets) {
            contentConverter = KotlinxWebsocketSerializationConverter(myJson)
            pingInterval = 10.seconds
        }
    }

    // WebRTC HTTP client - created lazily on first WebRTC connection
    private val webrtcHttpClient: HttpClient by inject(named("webrtcHttpClient"))

    private val networkMonitor: NetworkMonitor by inject()
    private val imageCacheInvalidator: ImageCacheInvalidator by inject()

    // --- Transport ---
    private var transport: Transport? = null
    private var transportObserverJob: Job? = null

    // Safety valve for a connect that neither completes nor fails (e.g. a half-open
    // socket / hung WebRTC signaling with no transport-level connect timeout). Without
    // it the session pins on `Connecting` forever: `connect()` self-guards, `kickRecovery`
    // treats `Connecting` as in-progress, and only a manual disconnect breaks out.
    private var connectWatchdogJob: Job? = null

    // --- Lifecycle / background state ---
    private var isInBackground = false
    private var hasActiveExternalConsumer = false
    private var hasActivePlayback = false
    private var backgroundedAt = 0L

    private val silentReauth = SilentReauth(
        ReauthPolicy(
            maxSilentFailures = MAX_SILENT_REAUTH_FAILURES,
            roundTripTimeoutMs = AUTH_ROUNDTRIP_TIMEOUT_MS,
            retryDelayMs = SILENT_REAUTH_RETRY_DELAY_MS,
        ),
    )

    private sealed class BackgroundedConnectionInfo {
        data class Direct(val connectionInfo: ConnectionInfo) : BackgroundedConnectionInfo()
        data class WebRTC(val remoteId: RemoteId) : BackgroundedConnectionInfo()
    }

    private var backgroundedConnectionInfo: BackgroundedConnectionInfo? = null

    private val _sessionState: MutableStateFlow<SessionState> =
        MutableStateFlow(SessionState.Disconnected.Initial)
    override val sessionState = _sessionState.asStateFlow()

    override val serverBaseUrl: StateFlow<String?> = _sessionState
        .map { state ->
            when (state) {
                is SessionState.Connected.Direct -> state.connectionInfo.webUrl
                is SessionState.Reconnecting.Direct -> state.connectionInfo.webUrl
                else -> null
            }
        }
        .stateIn(this, SharingStarted.Eagerly, null)

    override val isReadyForCommands: StateFlow<Boolean> = _sessionState
        .map { it is SessionState.Connected && it.dataConnectionState is DataConnectionState.Authenticated }
        .stateIn(this, SharingStarted.Eagerly, false)

    private val _externalConsumerActive = MutableStateFlow(false)
    override val externalConsumerActive = _externalConsumerActive.asStateFlow()

    private val _eventsFlow = MutableSharedFlow<Event<out Any>>(extraBufferCapacity = 10)
    override val events: Flow<Event<out Any>> = _eventsFlow.asSharedFlow()

    private val _foregroundEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val foregroundEvents: Flow<Unit> = _foregroundEvents.asSharedFlow()

    /**
     * WebRTC Sendspin data channel.
     * Available when connected via WebRTC, null otherwise.
     */
    override val webrtcSendspinChannel: io.music_assistant.client.webrtc.DataChannelWrapper?
        get() = (transport as? WebRTCTransport)?.sendspinDataChannel

    override val webRTCHttpProxy: io.music_assistant.client.webrtc.WebRTCHttpProxy?
        get() = (transport as? WebRTCTransport)?.httpProxy

    override fun resolveImageUrl(
        path: String,
        provider: String,
        isRemotelyAccessible: Boolean,
        proxyId: String?,
    ): String? {
        if (isRemotelyAccessible && path.startsWith("https")) return path
        // Prefer the opaque proxy_id endpoint on schema-31+ servers; fall back to the
        // legacy path/provider query form otherwise (older servers, or missing proxy_id).
        val opaque = proxyId?.takeIf { supportsOpaqueProxy() }
        return when (val state = _sessionState.value) {
            is SessionState.Connected.Direct -> resolveHttpImageUrl(state.connectionInfo.webUrl, path, provider, opaque)
            is SessionState.Reconnecting.Direct -> resolveHttpImageUrl(
                state.connectionInfo.webUrl,
                path,
                provider,
                opaque,
            )
            is SessionState.Connected.WebRTC,
            is SessionState.Reconnecting.WebRTC,
                -> resolveWebRTCImageUrl(path, provider, opaque)
            else -> null
        }
    }

    private fun supportsOpaqueProxy(): Boolean =
        (_sessionState.value as? HasConnectionData)?.serverInfo?.schemaVersion
            ?.let { it >= IMAGEPROXY_OPAQUE_SCHEMA } == true

    private fun resolveHttpImageUrl(base: String, path: String, provider: String, proxyId: String?): String =
        proxyId?.let { buildHttpOpaqueProxyUrl(base, it) } ?: buildHttpImageProxyUrl(base, path, provider)

    // In WebRTC mode the client has internet access while the server only sees us through
    // signaling/SCTP. Any public `https://` artwork should be fetched directly by Coil instead
    // of relayed through the (slow, single-channel) data-channel proxy. The proxy is reserved
    // for paths the client cannot reach (LAN URLs, server-local file paths, etc.).
    private fun resolveWebRTCImageUrl(path: String, provider: String, proxyId: String?): String =
        when {
            proxyId != null -> buildWebRTCOpaqueProxyUrl(proxyId)
            path.startsWith("https://") -> path
            else -> buildWebRTCImageProxyUrl(path, provider)
        }

    private fun buildHttpImageProxyUrl(base: String, path: String, provider: String): String =
        URLBuilder(base).apply {
            appendPathSegments("imageproxy")
            parameters.apply {
                append("path", path.encodeURLQueryComponent())
                append("provider", provider)
                append("checksum", "")
            }
        }.buildString()

    private fun buildHttpOpaqueProxyUrl(base: String, proxyId: String): String =
        URLBuilder(base).apply {
            appendPathSegments("imageproxy", proxyId)
            parameters.apply {
                append("size", IMAGEPROXY_SIZE.toString())
                append("checksum", "")
            }
        }.buildString()

    // Synthetic URL consumed by WebRTCImageFetcher. Scheme is matched by the Coil fetcher
    // factory; path+query are reconstructed verbatim into the http-proxy-request `path` field.
    private fun buildWebRTCImageProxyUrl(path: String, provider: String): String =
        "$WEBRTC_PROXY_BASE/imageproxy" +
            "?path=${path.encodeURLQueryComponent()}" +
            "&provider=${provider.encodeURLQueryComponent()}" +
            "&checksum="

    private fun buildWebRTCOpaqueProxyUrl(proxyId: String): String =
        "$WEBRTC_PROXY_BASE/imageproxy/${proxyId.encodeURLPathPart()}" +
            "?size=$IMAGEPROXY_SIZE" +
            "&checksum="

    // Rebases a server-issued image URL (which embeds the server's self-view of its origin,
    // typically a LAN address) onto whatever base is reachable from this client. Used for
    // player-current-item artwork, which the server pushes as fully-qualified `image_url`.
    // External (non-imageproxy) URLs pass through untouched.
    override fun rebaseServerImageUrl(rawUrl: String): String? {
        if (rawUrl.isEmpty()) return null
        val parsed = runCatching { Url(rawUrl) }.getOrNull() ?: return rawUrl
        val path = parsed.encodedPath
        if (!path.contains("imageproxy", ignoreCase = true)) return rawUrl
        val tail = parsed.encodedQuery.let { if (it.isEmpty()) path else "$path?$it" }
        val base = when (val state = _sessionState.value) {
            is SessionState.Connected.Direct -> state.connectionInfo.webUrl
            is SessionState.Reconnecting.Direct -> state.connectionInfo.webUrl
            is SessionState.Connected.WebRTC,
            is SessionState.Reconnecting.WebRTC,
                -> WEBRTC_PROXY_BASE
            else -> return null
        }
        return base.trimEnd('/') + tail
    }

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
        logger.i { "App backgrounded (state=${stateLabel(_sessionState.value)})" }
    }

    /**
     * Called when an external consumer (Android Auto / CarPlay) becomes active.
     */
    override fun onExternalConsumerActive() {
        hasActiveExternalConsumer = true
        _externalConsumerActive.value = true
        val state = _sessionState.value
        logger.i { "External consumer active (state=${stateLabel(state)})" }

        if (state is SessionState.Disconnected.Backgrounded) {
            if (!reconnectFromCurrent("external consumer active (was Backgrounded)")) {
                logger.i { "External consumer active: state=Backgrounded but no savedInfo, no reconnect" }
            }
            return
        }

        // AA hookup is functionally equivalent to phone foreground: cache may be
        // empty, AA's first sendRequest assumes the gate handles staleness — but
        // the gate trusts `isReadyForCommands`, which stays true for a half-open
        // WS. Same probe as `onAppForeground` to catch that case.
        val elapsed = currentTimeMillis() - backgroundedAt
        if (elapsed > STALE_CONNECTION_THRESHOLD_MS && state is SessionState.Connected) {
            logger.i { "External consumer active: probing connection after ${elapsed}ms in background" }
            transport?.verifyConnection(probeReason = "external_consumer_active")
        }
    }

    /**
     * Tears down the current transport and reconnects using either the saved
     * [backgroundedConnectionInfo] or, failing that, the connection identity of
     * the current [SessionState.Connected]. Returns false when neither source
     * can supply a target — caller decides what (if anything) to log.
     */
    private fun reconnectFromCurrent(reason: String): Boolean {
        val info = backgroundedConnectionInfo
            ?: when (val s = _sessionState.value) {
                is SessionState.Connected.Direct -> BackgroundedConnectionInfo.Direct(s.connectionInfo)
                is SessionState.Connected.WebRTC -> BackgroundedConnectionInfo.WebRTC(s.remoteId)
                else -> null
            } ?: return false
        backgroundedConnectionInfo = null
        logger.i { "Force reconnect: $reason" }

        // forceConnect / forceConnectWebRTC bypass the public guard and handle
        // their own teardown — no transient state sentinel needed here.
        when (info) {
            is BackgroundedConnectionInfo.Direct -> {
                val connInfo = settings.connectionInfo.value ?: info.connectionInfo
                forceConnect(connInfo)
            }
            is BackgroundedConnectionInfo.WebRTC -> forceConnectWebRTC(info.remoteId)
        }
        return true
    }

    /**
     * Called when an external consumer (Android Auto / CarPlay) becomes inactive.
     */
    override fun onExternalConsumerInactive() {
        hasActiveExternalConsumer = false
        _externalConsumerActive.value = false
        logger.i { "External consumer inactive (state=${stateLabel(_sessionState.value)})" }
    }

    /**
     * Called when any player starts playing. Prevents background teardown.
     */
    override fun onPlaybackActive() {
        hasActivePlayback = true
        logger.i { "Playback active (state=${stateLabel(_sessionState.value)})" }
    }

    /**
     * Called when all players stop playing.
     */
    override fun onPlaybackInactive() {
        hasActivePlayback = false
        logger.i { "Playback inactive (state=${stateLabel(_sessionState.value)})" }
    }

    override fun forceDisconnect(reason: Exception) {
        disconnect(SessionState.Disconnected.Error(reason))
    }

    /**
     * Called when the app returns to the foreground.
     */
    override fun onAppForeground() {
        val wasInBackground = isInBackground
        isInBackground = false
        val state = _sessionState.value
        logger.i { "App foregrounded (state=${stateLabel(state)})" }

        if (wasInBackground) _foregroundEvents.tryEmit(Unit)

        if (backgroundedConnectionInfo != null) {
            reconnectFromCurrent("was Backgrounded")
            return
        }

        // Cheap probe for half-open TCP. Anything more invasive (re-auth, full
        // reconnect) is request-driven via `ensureReadyForCommands` — see
        // `feedback_request_driven_recovery` for the rationale.
        val elapsed = currentTimeMillis() - backgroundedAt
        if (elapsed > STALE_CONNECTION_THRESHOLD_MS && state is SessionState.Connected) {
            logger.i { "App foregrounded: probing connection after ${elapsed}ms in background" }
            transport?.verifyConnection(probeReason = "app_foreground")
        }
    }

    /** Compact one-token label for a [SessionState], stable for log greps. */
    private fun stateLabel(state: SessionState): String = when (state) {
        is SessionState.Connected.Direct -> "Connected.Direct(${dcsLabel(state.dataConnectionState)})"
        is SessionState.Connected.WebRTC -> "Connected.WebRTC(${dcsLabel(state.dataConnectionState)})"
        is SessionState.Reconnecting.Direct -> "Reconnecting.Direct(attempt=${state.attempt})"
        is SessionState.Reconnecting.WebRTC -> "Reconnecting.WebRTC(attempt=${state.attempt})"
        SessionState.Disconnected.Initial -> "Disconnected.Initial"
        SessionState.Disconnected.NoServerData -> "Disconnected.NoServerData"
        SessionState.Disconnected.Backgrounded -> "Disconnected.Backgrounded"
        SessionState.Disconnected.ByUser -> "Disconnected.ByUser"
        is SessionState.Disconnected.Error -> {
            "Disconnected.Error(${state.reason?.message ?: state.reason?.toString()})"
        }
        SessionState.Connecting -> "Connecting"
    }

    /** Compact one-token label for a [DataConnectionState]. */
    private fun dcsLabel(dcs: DataConnectionState): String = when (dcs) {
        DataConnectionState.AwaitingServerInfo -> "AwaitingServerInfo"
        is DataConnectionState.AwaitingAuth -> "AwaitingAuth"
        is DataConnectionState.Authenticated -> "Authenticated"
    }

    private val rpcEngine = RpcEngine(
        onAuthError = {
            _sessionState.update {
                (it as? SessionState.Connected)?.update(
                    user = null,
                    authProcessState = AuthProcessState.NotStarted,
                ) ?: it
            }
        },
        onError = errorBus::emit,
    )

    init {
        launch {
            isReadyForCommands.collect { ready ->
                logger.i { "isReadyForCommands=$ready" }
            }
        }
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
            // State collector. `drop(1)` skips the StateFlow's initial-value replay
            // (`TransportState.Disconnected`, the default), which would otherwise overwrite
            // the `SessionState.Connecting` we just set in `connect()` / `connectWebRTC()`
            // before the transport's own coroutine has had a chance to flip to `Connecting`.
            // For Direct the resulting form-flicker is ~100 ms and invisible; for WebRTC the
            // form stays put for the entire signaling + ICE window.
            launch {
                transport.state.drop(1).collect { transportState ->
                    when (transportState) {
                        TransportState.Connected -> {
                            connectWatchdogJob?.cancel()
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
                            logger.i {
                                "Transport→Connected (wasReconnecting=$wasReconnecting, " +
                                    "needsReauthOnReconnect=$needsReauthOnReconnect, " +
                                    "dcs=${dcsLabel(emitData.dataConnectionState)})"
                            }
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
                                logger.i { "Transport→Reconnecting while backgrounded → Disconnected.Backgrounded" }
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
                            logger.i { "Transport→Reconnecting (attempt=${transportState.attempt})" }
                        }

                        is TransportState.Failed -> {
                            _sessionState.update { SessionState.Disconnected.Error(transportState.error) }
                            logger.i { "Transport→Failed → Disconnected.Error: ${transportState.error.message}" }
                        }

                        TransportState.Disconnected -> {
                            if (_sessionState.value !is SessionState.Disconnected) {
                                _sessionState.update { SessionState.Disconnected.ByUser }
                                logger.i { "Transport→Disconnected → Disconnected.ByUser" }
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
        forceConnect(connection)
    }

    /**
     * Arms a one-shot timeout that forces a wedged `Connecting` session into
     * [SessionState.Disconnected.Error], from which request-driven recovery
     * ([kickRecovery]) can reconnect. Re-armed on every connect attempt and cancelled
     * once the transport leaves `Connecting`. No-op if the session has already
     * progressed by the time it fires.
     */
    private fun startConnectWatchdog() {
        connectWatchdogJob?.cancel()
        connectWatchdogJob = launch {
            delay(CONNECT_TIMEOUT_MS)
            if (_sessionState.value is SessionState.Connecting) {
                logger.w { "Connect watchdog: stuck in Connecting after ${CONNECT_TIMEOUT_MS}ms — failing out" }
                // Error first, then tear down: the observer's `Disconnected` handler only
                // writes `ByUser` when the state isn't already `Disconnected`, so seeding
                // `Error` keeps the transport's teardown from masking it as user intent
                // (which `kickRecovery` would refuse to recover from).
                _sessionState.update {
                    SessionState.Disconnected.Error(
                        Exception("Connect timed out after ${CONNECT_TIMEOUT_MS}ms"),
                    )
                }
                transport?.disconnect()
            }
        }
    }

    /**
     * Bypass of [connect]'s Connecting/Connected guard for callers that
     * intentionally tear down a live transport before rebuilding (e.g. JIT
     * reconnect from a stuck `AwaitingAuth(Failed)` session). Public callers
     * should keep using [connect] so accidental double-connects are still cheap.
     */
    private fun forceConnect(connection: ConnectionInfo) {
        // New connection episode: the silent-failure budget is per-server-session, so
        // a prior server's failures must not pre-charge this one's escape hatch.
        silentReauth.reset()
        // Cancel observer before disconnecting transport to prevent race where the old
        // observer processes TransportState.Disconnected and briefly sets ByUser
        transportObserverJob?.cancel()
        transport?.disconnect()
        transport?.close()
        _sessionState.update { SessionState.Connecting }
        startConnectWatchdog()

        val directTransport = DirectTransport(
            client = client,
            connectionInfoProvider = { connection },
            parentScope = this,
            networkAvailable = networkMonitor.isAvailable,
        )
        transport = directTransport

        observeTransport(
            transport = directTransport,
            createConnected = { data ->
                SessionState.Connected.Direct(connection, data)
            },
            createReconnecting = { attempt, data ->
                SessionState.Reconnecting.Direct(attempt, connection, data, isOnline = networkMonitor.isAvailable.value)
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
        forceConnectWebRTC(remoteId)
    }

    /** WebRTC twin of [forceConnect]. */
    private fun forceConnectWebRTC(remoteId: RemoteId) {
        silentReauth.reset()
        transportObserverJob?.cancel()
        transport?.disconnect()
        transport?.close()
        _sessionState.update { SessionState.Connecting }
        startConnectWatchdog()

        val webrtcTransport = WebRTCTransport(
            httpClient = webrtcHttpClient,
            remoteId = remoteId,
            parentScope = this,
            networkAvailable = networkMonitor.isAvailable,
        )
        transport = webrtcTransport

        observeTransport(
            transport = webrtcTransport,
            createConnected = { data -> SessionState.Connected.WebRTC(remoteId, data) },
            createReconnecting = { attempt, data ->
                SessionState.Reconnecting.WebRTC(attempt, remoteId, data, isOnline = networkMonitor.isAvailable.value)
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
                // But we DO need to evict `mawebrtc://` entries from Coil's memory cache:
                // `WebRTCHttpProxy.cancelAll()` ran on the previous transport's tear-down,
                // failing every in-flight image request — Coil caches those errors and
                // won't retry on its own, so visible tiles stay broken until invalidated.
                imageCacheInvalidator.evictWebRTCEntries()
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
            transport?.close()
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
        try {
            when (
                // Manual login is single-shot (isAutoLogin = false surfaces on the first
                // failure). Unlike re-auth it must proceed from a LoggedOut session, so its
                // guard checks only liveness — not LoggedOut, which would abort the retry.
                val resolution = silentReauth.resolve(
                    isAutoLogin = false,
                    shouldAttempt = { _sessionState.value is SessionState.Connected },
                    onAttempt = { setAuthState(AuthProcessState.InProgress) },
                    send = {
                        sendRequestRaw(Request.Auth.login(username, password, settings.deviceName.value))
                    },
                )
            ) {
                AuthResolution.Aborted -> return
                is AuthResolution.Surface -> setAuthFailed(resolution.message)
                is AuthResolution.Reject -> {
                    clearCurrentServerToken()
                    setAuthFailed(resolution.message)
                }

                is AuthResolution.Authenticated -> handleLoginResponse(resolution.answer)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (_sessionState.value !is SessionState.Connected) return
            setAuthFailed(e.message ?: "Exception happened: $e")
            clearCurrentServerToken()
        }
    }

    private suspend fun handleLoginResponse(answer: Answer) {
        val auth = answer.resultAs<LoginResponse>() ?: run {
            setAuthFailed("Failed to parse auth data")
            return
        }
        when {
            !auth.success -> setAuthFailed(auth.error ?: "Authentication failed")
            auth.token.isNullOrBlank() -> setAuthFailed("No token received")
            auth.user == null -> setAuthFailed("No user data received")
            else -> authorize(auth.token, isAutoLogin = false)
        }
    }

    override fun logout() {
        val currentState = _sessionState.value
        if (currentState is SessionState.Connected) {
            val serverIdentifier = settings.getServerIdentifier(currentState)
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
                sendRequestRaw(Request.Auth.logout())
            } catch (_: Exception) {
            }
        }
    }

    override suspend fun authorize(token: String, isAutoLogin: Boolean) {
        try {
            when (
                val resolution = silentReauth.resolve(
                    isAutoLogin = isAutoLogin,
                    // Keep retrying only while the session is live and hasn't been logged
                    // out from under us — otherwise a late retry could undo a logout.
                    shouldAttempt = {
                        val state = _sessionState.value
                        state is SessionState.Connected &&
                            state.authProcessState != AuthProcessState.LoggedOut
                    },
                    onAttempt = { setAuthState(AuthProcessState.InProgress) },
                    send = { sendRequestRaw(Request.Auth.authorize(token, settings.deviceName.value)) },
                )
            ) {
                AuthResolution.Aborted -> return
                is AuthResolution.Surface -> setAuthFailed(resolution.message)
                is AuthResolution.Reject -> {
                    clearCurrentServerToken()
                    setAuthFailed(resolution.message)
                }
                is AuthResolution.Authenticated ->
                    onAuthorized(token, isAutoLogin, resolution.answer)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (_sessionState.value !is SessionState.Connected) return
            setAuthFailed(e.message ?: "Exception happened: $e")
            clearCurrentServerToken()
        }
    }

    private fun onAuthorized(token: String, isAutoLogin: Boolean, answer: Answer) {
        val user = answer.resultAs<AuthorizationResponse>()?.user ?: run {
            setAuthFailed("Failed to parse user data")
            return
        }
        val currentState = _sessionState.value
        if (currentState is SessionState.Connected) {
            val serverIdentifier = settings.getServerIdentifier(currentState)
            settings.setTokenForServer(serverIdentifier, token)
            logger.d { "Saved token for server" }
        }

        silentReauth.reset()
        _sessionState.update {
            (it as? SessionState.Connected)?.update(
                authProcessState = AuthProcessState.NotStarted,
                user = user,
                wasAutoLogin = isAutoLogin,
                needsServerReauth = false,
            ) ?: it
        }
    }

    private fun clearCurrentServerToken() {
        val currentState = _sessionState.value
        if (currentState is SessionState.Connected) {
            val serverIdentifier = settings.getServerIdentifier(currentState)
            settings.setTokenForServer(serverIdentifier, null)
            logger.i { "Cleared token for server due to auth failure" }
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

    private val recoveryMutex = Mutex()

    private val authHandshakeCommands = setOf(
        APICommands.AUTH_PROVIDERS,
        APICommands.AUTH_AUTHORIZATION_URL,
        APICommands.AUTH_LOGIN,
        APICommands.AUTH_LOGOUT,
        APICommands.AUTH,
    )

    /**
     * Request-driven recovery gate. Suspends until `isReadyForCommands` is true
     * (or [timeoutMs] elapses), kicking the appropriate recovery on entry:
     *
     *   - `Disconnected.Initial/Backgrounded/Error` → reconnect from history.
     *   - `Connected + AwaitingAuth(Failed)` w/ saved token from a prior auto-login
     *     → reset `authProcessState=NotStarted` so AuthenticationManager re-fires.
     *   - User-intent states (`Disconnected.ByUser`, `NoServerData`,
     *     `AwaitingAuth(LoggedOut)`) are NOT auto-recovered — the user opted out.
     *   - In-flight states (`Connecting`, `Reconnecting`, `AwaitingAuth(InProgress)`,
     *     `AwaitingServerInfo`) are left to complete on their own.
     *
     * Returns false if not ready within the timeout — callers should fail their
     * request with a clear "not connected/not authenticated" result.
     */
    private suspend fun ensureReadyForCommands(timeoutMs: Long = ENSURE_READY_TIMEOUT_MS): Boolean {
        if (isReadyForCommands.value) return true
        recoveryMutex.withLock {
            if (isReadyForCommands.value) return true
            kickRecovery()
        }
        return withTimeoutOrNull(timeoutMs) {
            isReadyForCommands.first { it }
            true
        } == true
    }

    private fun kickRecovery() {
        val state = _sessionState.value
        when (state) {
            is SessionState.Disconnected.Initial,
            is SessionState.Disconnected.Backgrounded,
            is SessionState.Disconnected.Error,
                -> reconnectFromHistory(reason = "JIT: state=${stateLabel(state)}")
            SessionState.Disconnected.ByUser,
            SessionState.Disconnected.NoServerData,
                -> logger.i { "JIT: honoring user-intent state=${stateLabel(state)}" }
            SessionState.Connecting,
            is SessionState.Reconnecting,
                -> Unit // already in progress
            is SessionState.Connected -> {
                when (val dcs = state.dataConnectionState) {
                    is DataConnectionState.AwaitingAuth -> {
                        val auth = dcs.authProcessState
                        val hasToken = savedTokenForState(state) != null
                        if (auth is AuthProcessState.Failed && state.wasAutoLogin && hasToken) {
                            logger.i { "JIT: bumping AwaitingAuth(Failed) → NotStarted to retry auto-login" }
                            _sessionState.update {
                                (it as? SessionState.Connected)?.update(
                                    authProcessState = AuthProcessState.NotStarted,
                                ) ?: it
                            }
                        }
                        // NotStarted/InProgress/LoggedOut handled by AuthMgr or user; no-op.
                    }
                    DataConnectionState.AwaitingServerInfo -> Unit // server/hello pending
                    is DataConnectionState.Authenticated -> Unit // ready
                }
            }
        }
    }

    private fun reconnectFromHistory(reason: String) {
        val entry = settings.connectionHistory.value.firstOrNull() ?: run {
            logger.i { "JIT: no history entry — cannot reconnect ($reason)" }
            return
        }
        logger.i { "JIT reconnect via ${entry.type} ($reason)" }
        when (entry.type) {
            ConnectionType.DIRECT -> entry.connectionInfo?.let { forceConnect(it) }
            ConnectionType.WEBRTC -> entry.remoteId?.let { forceConnectWebRTC(RemoteId(it)) }
        }
    }

    private fun savedTokenForState(state: SessionState.Connected): String? {
        val id = when (state) {
            is SessionState.Connected.Direct -> settings.getDirectServerIdentifier(
                state.connectionInfo.host,
                state.connectionInfo.port,
                state.connectionInfo.isTls,
            )
            is SessionState.Connected.WebRTC -> settings.getWebRTCServerIdentifier(state.remoteId.rawId)
        }
        return settings.getTokenForServer(id)
    }

    override suspend fun sendRequest(request: Request): Result<Answer> {
        val controlLog = request.playerControlLogLabel()
        // Auth-handshake commands bypass the gate — they're the mechanism by which
        // `ensureReadyForCommands` is *resolved*, so gating them would deadlock.
        if (request.command in authHandshakeCommands) return sendRequestRaw(request)
        if (!ensureReadyForCommands()) {
            logger.i { "sendRequest gated — not ready (state=${stateLabel(_sessionState.value)})" }
            controlLog?.let { logger.i { "$it command failed: transport not ready" } }
            return Result.failure(IllegalStateException("Not ready for commands"))
        }
        controlLog?.let { logger.i { "$it command sent" } }
        val result = sendRequestRaw(request)
        controlLog?.let {
            if (result.isSuccess) {
                logger.i { "$it command acked" }
            } else {
                logger.i(result.exceptionOrNull()) { "$it command failed" }
            }
        }
        return result
    }

    /**
     * Bypasses [ensureReadyForCommands]. Reserved for the auth handshake itself
     * (login / authorize / logout / providers), which must be allowed to send
     * while the session is still in `AwaitingAuth(*)`.
     */
    private suspend fun sendRequestRaw(request: Request): Result<Answer> =
        suspendCancellableCoroutine { continuation ->
            val msgId = request.messageId
            val cmd = request.command
            val startMs = currentTimeMillis()
            logger.d { "sendRequest[$msgId] cmd=$cmd start" }

            rpcEngine.registerCallback(msgId) { response ->
                logger.d {
                    "sendRequest[$msgId] cmd=$cmd resumed in " +
                        "${currentTimeMillis() - startMs}ms"
                }
                continuation.resume(Result.success(response))
            }
            // Caller cancellation (e.g. withTimeoutOrNull) must release the
            // rpcEngine callback, otherwise it leaks for the session lifetime.
            continuation.invokeOnCancellation {
                logger.i {
                    "sendRequest[$msgId] cmd=$cmd cancelled after " +
                        "${currentTimeMillis() - startMs}ms"
                }
                rpcEngine.removeCallback(msgId)
            }
            launch {
                val t = transport ?: run {
                    logger.i { "sendRequest[$msgId] cmd=$cmd transport=null at send time" }
                    rpcEngine.removeCallback(msgId)
                    continuation.resume(Result.failure(IllegalStateException("Not connected")))
                    return@launch
                }
                try {
                    val jsonObject =
                        myJson.encodeToJsonElement(Request.serializer(), request) as JsonObject
                    t.send(jsonObject)
                    logger.d { "sendRequest[$msgId] cmd=$cmd sent" }
                } catch (e: Exception) {
                    logger.e(e) { "sendRequest[$msgId] cmd=$cmd send FAILED" }
                    rpcEngine.removeCallback(msgId)
                    continuation.resume(Result.failure(e))
                    // A send failure is definitive liveness evidence: the high-level
                    // Connected/Authenticated state can lag behind a closed WebRTC data channel.
                    // Kick a fresh reconnect so callers that queue on failure are replayed when
                    // the transport returns. Logout is user-intent teardown; never resurrect it.
                    val sessionState = _sessionState.value
                    val transportState = transport?.state?.value
                    val canStartReconnect = request.command != APICommands.AUTH_LOGOUT &&
                        sessionState !is SessionState.Connecting &&
                        sessionState !is SessionState.Reconnecting &&
                        transportState !is TransportState.Reconnecting
                    val reconnectStarted = canStartReconnect && reconnectFromCurrent("send failed: ${e.message}")
                    if (canStartReconnect && !reconnectStarted) {
                        disconnect(
                            SessionState.Disconnected.Error(
                                Exception("Error sending command: ${e.message}"),
                            ),
                        )
                    }
                }
            }
        }

    fun close() {
        supervisorJob.cancel()
        client.close()
    }

    private fun Request.playerControlLogLabel(): String? {
        if (!command.startsWith("players/cmd/") && !command.startsWith("player_queues/")) return null
        val targetId = args?.stringArg("player_id")
            ?: args?.stringArg("queue_id")
            ?: args?.stringArg("queue_item_id")
        return buildString {
            append('#').append(messageId.substringBefore('-')).append(' ')
            append(command)
            targetId?.let { append(" target=").append(it) }
        }
    }

    private fun JsonObject.stringArg(name: String): String? = (this[name] as? JsonPrimitive)?.content

    companion object {
        private const val STALE_CONNECTION_THRESHOLD_MS = 30_000L
        private const val ENSURE_READY_TIMEOUT_MS = 10_000L

        // Upper bound on a single connect attempt before it's declared stuck. Generous
        // enough to cover a healthy cold-start handshake (incl. WebRTC signaling + ICE)
        // so it never trips a merely-slow connect, but bounded so `Connecting` can't wedge.
        private const val CONNECT_TIMEOUT_MS = 20_000L

        // Silent reconnect re-auth failures tolerated before surfacing login — rides
        // through a flaky handoff without trapping the user if re-auth never recovers.
        private const val MAX_SILENT_REAUTH_FAILURES = 3

        // Cap on one auth round-trip. Above the 10s ping interval so a merely slow
        // server isn't cut off, but bounded so a dead-after-send socket can't suspend
        // the auth flow forever (the reply rides the same WebSocket as the request).
        private const val AUTH_ROUNDTRIP_TIMEOUT_MS = 15_000L

        // Backoff between silent re-auth retries, so a fast-failing attempt can't spin.
        private const val SILENT_REAUTH_RETRY_DELAY_MS = 1_000L
        private const val WEBRTC_PROXY_BASE = "mawebrtc://proxy"

        // Server schema that introduced the opaque /imageproxy/{proxy_id} endpoint.
        private const val IMAGEPROXY_OPAQUE_SCHEMA = 31

        // Bucketed proxy size we request; matches our fixed decode size so the server
        // can downscale before sending without any visual change. 512 is an allowed bucket.
        private const val IMAGEPROXY_SIZE = ARTWORK_DECODE_SIZE
    }
}
