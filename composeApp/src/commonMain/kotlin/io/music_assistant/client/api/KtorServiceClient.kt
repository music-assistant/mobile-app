package io.music_assistant.client.api

import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.pingInterval
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.encodeURLPathPart
import io.ktor.http.encodeURLQueryComponent
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import io.music_assistant.client.data.model.server.AuthorizationResponse
import io.music_assistant.client.data.model.server.LoginResponse
import io.music_assistant.client.data.model.server.ServerInfo
import io.music_assistant.client.data.model.server.events.CoreStateUpdatedEvent
import io.music_assistant.client.data.model.server.events.Event
import io.music_assistant.client.imageloader.ARTWORK_DECODE_SIZE
import io.music_assistant.client.settings.SettingsRepository
import io.music_assistant.client.utils.AuthProcessState
import io.music_assistant.client.utils.DataConnectionState
import io.music_assistant.client.utils.HasConnectionData
import io.music_assistant.client.utils.HttpClientFactory
import io.music_assistant.client.utils.NetworkMonitor
import io.music_assistant.client.utils.SessionState
import io.music_assistant.client.utils.currentTimeMillis
import io.music_assistant.client.utils.myJson
import io.music_assistant.client.utils.platformLocale
import io.music_assistant.client.utils.serverLocalizationLocale
import io.music_assistant.client.utils.update
import io.music_assistant.client.utils.withRefreshedServerInfo
import io.music_assistant.client.webrtc.model.RemoteId
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
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
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class KtorServiceClient(
    private val settings: SettingsRepository,
    private val errorBus: ErrorMessageBus,
) : ServiceClient, CoroutineScope, KoinComponent {
    private val logger = Logger.withTag("Session.ServiceClient")
    private val supervisorJob = SupervisorJob()

    private val scopeExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        logger.e(throwable) { "Uncaught exception in KtorServiceClient scope (non-transport)" }
    }

    override val coroutineContext: CoroutineContext =
        supervisorJob + Dispatchers.IO + scopeExceptionHandler

    private val clientMutex = Mutex()

    // Declared before currentClient, whose initializer reads it.
    private val httpClientFactory: HttpClientFactory by inject()

    @kotlin.concurrent.Volatile
    private var currentClient: HttpClient = httpClientFactory.create {
        install(WebSockets) {
            contentConverter = KotlinxWebsocketSerializationConverter(myJson)
            pingInterval = 10.seconds
        }
    }

    /**
     * Creates a fresh HttpClient and closes the old one.
     * Called when the network transitions from unavailable → available while reconnecting,
     * to discard any cached DNS failures / connection-pool timeouts in the old engine.
     */
    private suspend fun rotateHttpClient() {
        clientMutex.withLock {
            val oldClient = currentClient
            currentClient = httpClientFactory.create {
                install(WebSockets) {
                    contentConverter = KotlinxWebsocketSerializationConverter(myJson)
                    pingInterval = 10.seconds
                }
            }
            oldClient.close()
        }
    }

    // Observes network availability and rotates the HttpClient when the network
    // comes back after an outage. This prevents stale DNS/connection-pool state
    // (e.g. NSURLErrorCannotFindHost with WireGuard tunnels) from poisoning
    // subsequent reconnect attempts.
    private fun startNetworkObserver() {
        launch {
            networkMonitor.isAvailable
                .collect { available ->
                    if (available && _sessionState.value !is SessionState.Connected) {
                        logger.i { "Network became available while not fully connected — rotating HttpClient" }
                        rotateHttpClient()
                    }
                }
        }
    }

    // A connection kept from an attempt without the new client certificate would be
    // reused and rejected again. The Darwin engine owns its session pool, so the only
    // way to drop such a connection is a fresh client.
    private fun startClientCertificateObserver() {
        launch {
            settings.clientCertificateAlias
                .drop(1)
                .collect {
                    if (_sessionState.value !is SessionState.Connected) {
                        logger.i { "Client certificate changed while not connected — rotating HttpClient" }
                        rotateHttpClient()
                    }
                }
        }
    }

    // WebRTC HTTP client - created lazily on first WebRTC connection
    private val webrtcHttpClient: HttpClient by inject(named("webrtcHttpClient"))

    private val networkMonitor: NetworkMonitor by inject()

    // --- Transport (written only by the lifecycle effects, under lifecycleLock) ---
    @kotlin.concurrent.Volatile
    private var transport: Transport? = null
    private var transportObserverJob: Job? = null

    // Safety valve for a connect that neither completes nor fails (e.g. a half-open
    // socket / hung WebRTC signaling with no transport-level connect timeout). Without
    // it the session pins on `Connecting` forever: `connect()` self-guards, `kickRecovery`
    // treats `Connecting` as in-progress, and only a manual disconnect breaks out.
    private var connectWatchdogJob: Job? = null

    // --- Lifecycle: the state machine in SessionMachine.kt ---
    private val lifecycleLock = SynchronizedObject()
    private var lifecycle = Lifecycle()

    // A command is trying to reach the server: the reconnect it asked for must not
    // be torn down under it. Bounded by the same budget the request gate waits on.
    private val sessionHold = SessionHold(ENSURE_READY_TIMEOUT_MS.milliseconds)

    private val silentReauth = SilentReauth(
        ReauthPolicy(
            maxSilentFailures = MAX_SILENT_REAUTH_FAILURES,
            roundTripTimeoutMs = AUTH_ROUNDTRIP_TIMEOUT_MS,
            retryDelayMs = SILENT_REAUTH_RETRY_DELAY_MS,
        ),
    )

    private val _sessionState: MutableStateFlow<SessionState> =
        MutableStateFlow(SessionState.Disconnected.Initial)
    override val sessionState = _sessionState.asStateFlow()

    private val SessionState.readyForCommands: Boolean
        get() = this is SessionState.Connected && dataConnectionState is DataConnectionState.Authenticated

    override val isReadyForCommands: StateFlow<Boolean> = _sessionState
        .map { it.readyForCommands }
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

    override suspend fun openWebRTCDataChannel(label: String): io.music_assistant.client.webrtc.DataChannelWrapper? =
        (transport as? WebRTCTransport)?.openDataChannel(label)

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

    // `base` is a complete origin plus any reverse-proxy base path, with no trailing slash, so the
    // endpoint path is appended as a string. Parsing a component-less base and calling
    // appendPathSegments() would leave the leading slash up to Ktor's normalization.
    private fun buildHttpImageProxyUrl(base: String, path: String, provider: String): String =
        URLBuilder("$base/imageproxy").apply {
            parameters.apply {
                append("path", path.encodeURLQueryComponent())
                append("provider", provider)
                append("checksum", "")
            }
        }.buildString()

    private fun buildHttpOpaqueProxyUrl(base: String, proxyId: String): String =
        URLBuilder("$base/imageproxy/${proxyId.encodeURLPathPart()}").apply {
            parameters.apply {
                append("size", IMAGEPROXY_SIZE.toString())
                append("checksum", "")
            }
        }.buildString()

    // Synthetic URL routed through the shared artwork repository and WebRTC HTTP proxy.
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
    // External (non-imageproxy) URLs go through [resolveImageUrl], like queue artwork: `https` stays
    // direct, cleartext `http` is fetched by the server imageproxy (iOS ATS blocks it on the client —
    // live-source covers such as Spotify Connect arrive this way). Anything else passes untouched.
    override fun rebaseServerImageUrl(rawUrl: String): String? {
        if (rawUrl.isEmpty()) return null
        val parsed = runCatching { Url(rawUrl) }.getOrNull() ?: return rawUrl
        val path = parsed.encodedPath
        if (!path.contains("imageproxy", ignoreCase = true)) {
            // Checked on the raw string: Ktor defaults a missing scheme to http, which must not count.
            return rawUrl.takeUnless { it.startsWith("http://", ignoreCase = true) }
                ?: resolveImageUrl(rawUrl, provider = "builtin", isRemotelyAccessible = true, proxyId = null)
        }
        val tail = parsed.encodedQuery.let { if (it.isEmpty()) path else "$path?$it" }
        val (base, prefix) = when (val state = _sessionState.value) {
            is SessionState.Connected.Direct -> state.connectionInfo.webUrl to state.connectionInfo.basePath
            is SessionState.Reconnecting.Direct -> state.connectionInfo.webUrl to state.connectionInfo.basePath
            is SessionState.Connected.WebRTC,
            is SessionState.Reconnecting.WebRTC,
                -> WEBRTC_PROXY_BASE to ""
            else -> return null
        }
        // The base already carries the reverse-proxy prefix. If the server was configured with its
        // public URL it reports the prefix too, so strip it here instead of emitting "/ma/ma/...".
        val relative = if (prefix.isNotEmpty() && tail.startsWith("$prefix/")) {
            tail.removePrefix(prefix)
        } else {
            tail
        }
        return base.trimEnd('/') + relative
    }

    /**
     * Force a full WebRTC reconnection to get fresh SDP-negotiated data channels.
     */
    override fun forceWebRTCReconnect() = dispatch(SessionEvent.ForceWebRTCReconnect)

    override fun onAppBackground() = dispatch(SessionEvent.Background)

    override fun onAppForeground() = dispatch(SessionEvent.Foreground)

    /**
     * Called when an external consumer (Android Auto / CarPlay) becomes active or inactive.
     * The flow is written first, as before; the flag in [Lifecycle] prevents background teardown.
     */
    override fun onExternalConsumerActive() {
        _externalConsumerActive.value = true
        dispatch(SessionEvent.ExternalConsumerActive)
    }

    override fun onExternalConsumerInactive() {
        _externalConsumerActive.value = false
        dispatch(SessionEvent.ExternalConsumerInactive)
    }

    override fun requestCommandRecovery() {
        // Hold before the gate runs: a stale-ready socket can report its drop as soon
        // as the queued command tries to use it, and that drop must not tear down the
        // reconnect this call is asking for.
        sessionHold.hold()
        launch { ensureReadyForCommands() }
    }

    /**
     * Called while the local player plays, or while a local pause is not yet confirmed by the
     * server (the adapter's `wantsSession`). Prevents background teardown. A stream that the
     * server starts on the local player while the session is torn down in the background
     * brings it back, like a car host does: the app must show what the phone plays.
     */
    override fun onPlaybackActive() = dispatch(SessionEvent.PlaybackActive)

    /**
     * Called when neither holds any more.
     */
    override fun onPlaybackInactive() = dispatch(SessionEvent.PlaybackInactive)

    override fun forceDisconnect(reason: Exception) {
        launch { dispatch(SessionEvent.Disconnect(SessionState.Disconnected.Error(reason))) }
    }

    override fun noServer() {
        sessionHold.clear()
        dispatch(SessionEvent.NoServer)
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
        startNetworkObserver()
        startClientCertificateObserver()
        launch {
            isReadyForCommands.collect { ready ->
                logger.i { "isReadyForCommands=$ready" }
            }
        }
    }

    // --- Lifecycle dispatch ---

    /**
     * Runs one lifecycle event: [transition], then its effects in order, under one lock, on the
     * caller's thread. The lock is reentrant: an effect that fails pending requests can resume a
     * caller that dispatches again, and that caller already sees the published state.
     */
    private fun dispatch(event: SessionEvent) = synchronized(lifecycleLock) {
        val step = transition(lifecycle, _sessionState.value, event, env())
        lifecycle = step.lifecycle
        step.effects.forEach(::perform)
    }

    private fun env() = Env(
        isOnline = networkMonitor.isAvailable.value,
        isHeld = sessionHold.isHeld,
        transportState = transport?.state?.value,
        savedConnectionInfo = settings.connectionInfo.value,
        lastHistory = settings.connectionHistory.value.firstOrNull(),
    )

    /** Carries out one effect. An effect never suspends or blocks: the lifecycle lock is held. */
    private fun perform(effect: Effect) {
        when (effect) {
            is Effect.Publish -> _sessionState.value = effect.state
            is Effect.FailAll -> rpcEngine.failAll(ConnectionLostException(effect.cause))
            Effect.ResetSilentReauth -> silentReauth.reset()
            Effect.DropTransport -> {
                // Cancelling the observer first keeps the dropped transport's own `Disconnected`
                // from being read as user intent.
                transportObserverJob?.cancel()
                transportObserverJob = null
                transport?.disconnect()
                transport?.close()
                transport = null
            }
            Effect.DisconnectTransport -> transport?.disconnect()
            is Effect.OpenTransport -> openTransport(effect)
            Effect.CancelWatchdog -> connectWatchdogJob?.cancel()
            is Effect.RecordConnection -> {
                settings.setLastConnectionMode(effect.target.mode)
                settings.addOrUpdateHistoryEntry(effect.target.historyEntry)
            }
            Effect.EmitForeground -> _foregroundEvents.tryEmit(Unit)
            Effect.ForceTransportReconnect -> (transport as? WebRTCTransport)?.forceReconnect()
            is Effect.Defer -> launch { dispatch(effect.event) }
            is Effect.Log -> logger.log(effect.severity, logger.tag, null, effect.message)
        }
    }

    /**
     * [OpenTransport.resume][Effect.OpenTransport.resume] continues a session that worked before: a
     * failed first attempt then retries as `Reconnecting` instead of failing to `Disconnected.Error`.
     */
    private fun openTransport(open: Effect.OpenTransport) {
        startConnectWatchdog(open.gen)
        val newTransport = when (val target = open.target) {
            is SessionTarget.Direct -> DirectTransport(
                clientProvider = { currentClient },
                connectionInfoProvider = { target.connectionInfo },
                parentScope = this,
                networkAvailable = networkMonitor.isAvailable,
                retryInitialFailure = open.resume,
            )

            is SessionTarget.WebRTC -> WebRTCTransport(
                httpClient = webrtcHttpClient,
                remoteId = target.remoteId,
                parentScope = this,
                networkAvailable = networkMonitor.isAvailable,
                retryInitialFailure = open.resume,
            )
        }
        transport = newTransport
        observeTransport(newTransport, open.target, open.gen)
        newTransport.connect()
    }

    private fun observeTransport(transport: Transport, target: SessionTarget, gen: Int) {
        transportObserverJob?.cancel()
        transportObserverJob = launch {
            // State collector. `drop(1)` skips the StateFlow's initial-value replay
            // (`TransportState.Disconnected`, the default), which would otherwise overwrite
            // the `SessionState.Connecting` we just set before the transport's own coroutine
            // has had a chance to flip to `Connecting`.
            launch {
                transport.state.drop(1).collect { dispatch(SessionEvent.Transport(gen, target, it)) }
            }
            launch {
                transport.messages.collect { handleIncomingMessage(it) }
            }
        }
    }

    /**
     * Arms a one-shot timeout that forces a wedged `Connecting` session into
     * [SessionState.Disconnected.Error], from which request-driven recovery
     * ([kickRecovery]) can reconnect. Re-armed on every connect attempt and cancelled
     * once the transport connects. A no-op if the session has progressed by then.
     */
    private fun startConnectWatchdog(gen: Int) {
        connectWatchdogJob?.cancel()
        connectWatchdogJob = launch {
            delay(CONNECT_TIMEOUT_MS)
            dispatch(SessionEvent.WatchdogFired(gen))
        }
    }

    // --- Connect / Disconnect ---

    override fun connect(connection: ConnectionInfo) = dispatch(SessionEvent.Connect(SessionTarget.Direct(connection)))

    override fun connectWebRTC(remoteId: RemoteId) = dispatch(SessionEvent.Connect(SessionTarget.WebRTC(remoteId)))

    override fun disconnectByUser() {
        sessionHold.clear()
        launch { dispatch(SessionEvent.Disconnect(SessionState.Disconnected.ByUser)) }
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
                        sendRequestRaw(
                            Request.Auth.login(username, password, settings.deviceName.value),
                        )
                    },
                )
            ) {
                AuthResolution.Aborted -> return
                is AuthResolution.Surface -> setAuthFailed(resolution.message)
                is AuthResolution.Reject -> {
                    setAuthFailed(resolution.message)
                }

                is AuthResolution.Authenticated -> handleLoginResponse(resolution.answer)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (_sessionState.value !is SessionState.Connected) return
            setAuthFailed(e.message ?: "Exception happened: $e")
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
        sessionHold.clear()
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
                    // out from under us — otherwise a late retry could undo a logout. A
                    // manual token login never retries, and like a credential login it
                    // must proceed from a LoggedOut session.
                    shouldAttempt = {
                        val state = _sessionState.value
                        state is SessionState.Connected &&
                            (!isAutoLogin || state.authProcessState != AuthProcessState.LoggedOut)
                    },
                    onAttempt = { setAuthState(AuthProcessState.InProgress) },
                    send = {
                        sendRequestRaw(
                            Request.Auth.authorize(
                                token,
                                settings.deviceName.value,
                                locale = serverLocalizationLocale(
                                    (_sessionState.value as? HasConnectionData)?.serverInfo?.schemaVersion,
                                    platformLocale(),
                                ),
                            ),
                        )
                    },
                )
            ) {
                AuthResolution.Aborted -> return
                is AuthResolution.Surface -> setAuthFailed(resolution.message)
                is AuthResolution.Reject -> {
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
        }
    }

    private fun onAuthorized(token: String, isAutoLogin: Boolean, answer: Answer) {
        val user = answer.resultAs<AuthorizationResponse>()?.user ?: run {
            setAuthFailed("Failed to parse user data")
            return
        }

        silentReauth.reset()
        _sessionState.update {
            (it as? SessionState.Connected)?.update(
                authProcessState = AuthProcessState.NotStarted,
                user = user,
                wasAutoLogin = isAutoLogin,
                needsServerReauth = false,
                token = token,
            ) ?: it
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
                Event(message).event()?.let { event ->
                    (event as? CoreStateUpdatedEvent)?.let { refreshServerInfo(it.data) }
                    _eventsFlow.emit(event)
                }
            }

            else -> logger.i { "Unknown message: $message" }
        }
    }

    /**
     * Live refresh of the cached [ServerInfo] from a `core_state_updated` push.
     *
     * The guard itself lives in [withRefreshedServerInfo] and runs inside the state update, so a
     * concurrent auth write cannot be clobbered. The pre-check here only buys an early log of the
     * ignored case.
     */
    private fun refreshServerInfo(incoming: ServerInfo) {
        val cachedId = (_sessionState.value as? SessionState.Connected)?.serverInfo?.serverId
        if (cachedId != incoming.serverId) {
            logger.d { "Ignoring core_state_updated for ${incoming.serverId} (cached server: $cachedId)" }
            return
        }
        _sessionState.update { state ->
            val connected = state as? SessionState.Connected ?: return@update state
            connected.update(connectionData = connected.connectionData.withRefreshedServerInfo(incoming))
        }
    }

    private val recoveryMutex = Mutex()

    private val authHandshakeCommands = setOf(
        APICommands.AUTH_PROVIDERS,
        APICommands.AUTH_AUTHORIZATION_URL,
        APICommands.AUTH_EXCHANGE,
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
        // Gate on the session state itself, not the `isReadyForCommands` projection, which
        // updates a dispatch later and can still read true after a transport loss.
        if (_sessionState.value.readyForCommands) return true
        recoveryMutex.withLock {
            if (_sessionState.value.readyForCommands) return true
            kickRecovery()
        }
        return withTimeoutOrNull(timeoutMs) {
            _sessionState.first { it.readyForCommands }
            true
        } == true
    }

    private fun kickRecovery() {
        when (val state = _sessionState.value) {
            is SessionState.Connected -> retryFailedAutoLogin(state)
            else -> dispatch(SessionEvent.Kick)
        }
    }

    /** Auth recovery, outside the lifecycle machine: `AwaitingAuth(Failed)` after an auto-login. */
    private fun retryFailedAutoLogin(state: SessionState.Connected) {
        val auth = (state.dataConnectionState as? DataConnectionState.AwaitingAuth)?.authProcessState
        if (auth is AuthProcessState.Failed && state.wasAutoLogin && savedTokenForState(state) != null) {
            logger.i { "JIT: bumping AwaitingAuth(Failed) → NotStarted to retry auto-login" }
            _sessionState.update {
                (it as? SessionState.Connected)?.update(
                    authProcessState = AuthProcessState.NotStarted,
                ) ?: it
            }
        }
        // NotStarted/InProgress/LoggedOut are handled by AuthenticationManager or the user;
        // AwaitingServerInfo waits for server/hello; Authenticated is ready.
    }

    private fun savedTokenForState(state: SessionState.Connected): String? {
        // Null until `server/hello` lands: without the server id there is no token to find.
        return state.serverInfo?.serverId?.let { settings.getTokenForServer(it) }
    }

    override suspend fun sendRequest(request: Request): Result<Answer> {
        val controlLog = request.playerControlLogLabel()
        // Auth-handshake commands bypass the gate — they're the mechanism by which
        // `ensureReadyForCommands` is *resolved*, so gating them would deadlock.
        if (request.command in authHandshakeCommands) return sendRequestRaw(request)
        // Covers both the cold recovery below and the stale-ready case, where the gate
        // passes on a projected state and only the send itself discovers the dead socket.
        sessionHold.hold()
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

            // The engine invokes this exactly once: with the server's answer, or
            // with a failure when the transport carrying the request is lost.
            rpcEngine.registerCallback(msgId) { result ->
                logger.d {
                    "sendRequest[$msgId] cmd=$cmd resumed in " +
                        "${currentTimeMillis() - startMs}ms " +
                        (if (result.isSuccess) "(answered)" else "(failed: ${result.exceptionOrNull()?.message})")
                }
                continuation.resume(result)
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
                    // Resume only if the engine hasn't already failed this request
                    // (a transport loss racing the send); a second resume would throw.
                    if (rpcEngine.removeCallback(msgId)) {
                        continuation.resume(Result.failure(IllegalStateException("Not connected")))
                    }
                    return@launch
                }
                try {
                    val jsonObject =
                        myJson.encodeToJsonElement(Request.serializer(), request) as JsonObject
                    t.send(jsonObject)
                    logger.d { "sendRequest[$msgId] cmd=$cmd sent" }
                } catch (e: Exception) {
                    logger.e(e) { "sendRequest[$msgId] cmd=$cmd send FAILED" }
                    if (rpcEngine.removeCallback(msgId)) {
                        continuation.resume(Result.failure(e))
                    }
                    // A send failure is liveness evidence: the machine starts a fresh reconnect.
                    dispatch(SessionEvent.SendFailed(request.command, e))
                }
            }
        }

    fun close() {
        rpcEngine.failAll(ConnectionLostException("Client closed"))
        supervisorJob.cancel()
        currentClient.close()
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
        private const val ENSURE_READY_TIMEOUT_MS = 10_000L

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
