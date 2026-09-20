package io.music_assistant.client.imageloader

import coil3.disk.DiskCache
import io.ktor.http.Headers
import io.music_assistant.client.api.Answer
import io.music_assistant.client.api.ConnectionInfo
import io.music_assistant.client.api.Request
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.data.model.server.ServerInfo
import io.music_assistant.client.data.model.server.events.Event
import io.music_assistant.client.utils.ConnectionData
import io.music_assistant.client.utils.SessionState
import io.music_assistant.client.webrtc.DataChannelWrapper
import io.music_assistant.client.webrtc.WebRTCHttpProxy
import io.music_assistant.client.webrtc.model.RemoteId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import okio.FileSystem
import okio.Path.Companion.toPath
import kotlin.random.Random

internal class RecordingArtworkTransport : ArtworkTransport {
    val calls = mutableListOf<Pair<String, ArtworkRequestContext>>()
    val responses = ArrayDeque<Any>()
    val next = CompletableDeferred<ArtworkResponse>()
    var awaitNext = false

    override suspend fun fetch(url: String, context: ArtworkRequestContext): ArtworkResponse {
        calls += url to context
        if (awaitNext) return next.await()
        val item = responses.removeFirstOrNull() ?: ArtworkResponse(
            bytes = url.encodeToByteArray(),
            headers = Headers.build {
                append("Content-Type", "image/png")
                append("Cache-Control", "max-age=60")
            },
            status = 200,
        )
        if (item is Throwable) throw item
        return item as ArtworkResponse
    }
}

internal class MutableArtworkServiceClient(
    serverId: String? = null,
    proxy: WebRTCHttpProxy? = null,
) : ServiceClient {
    private val state = MutableStateFlow<SessionState>(
        if (serverId == null) {
            SessionState.Disconnected.Initial
        } else {
            SessionState.Connected.WebRTC(
            remoteId = RemoteId(TEST_REMOTE_ID),
            connectionData = ConnectionData(serverInfo = ServerInfo(serverId = serverId)),
        )
        },
    )
    var onSessionStateRead: (() -> Unit)? = null
    override val sessionState: StateFlow<SessionState>
        get() {
            onSessionStateRead?.invoke()
            return state
        }
    override var webRTCHttpProxy: WebRTCHttpProxy? = proxy
    override val isReadyForCommands = MutableStateFlow(false)
    override val externalConsumerActive = MutableStateFlow(false)
    override val events: Flow<Event<out Any>> = emptyFlow()
    override val webrtcSendspinChannel: DataChannelWrapper? = null
    override val foregroundEvents: Flow<Unit> = emptyFlow()

    fun disconnect() {
        state.value = SessionState.Disconnected.ByUser
        webRTCHttpProxy = null
    }

    fun reconnect(serverId: String, proxy: WebRTCHttpProxy) {
        state.value = SessionState.Connected.WebRTC(
            remoteId = RemoteId(TEST_REMOTE_ID),
            connectionData = ConnectionData(serverInfo = ServerInfo(serverId = serverId)),
        )
        webRTCHttpProxy = proxy
    }

    override suspend fun sendRequest(request: Request): Result<Answer> = error("unused")
    override suspend fun login(username: String, password: String) = error("unused")
    override suspend fun authorize(token: String, isAutoLogin: Boolean) = error("unused")
    override fun logout() = Unit
    override fun resolveImageUrl(
        path: String,
        provider: String,
        isRemotelyAccessible: Boolean,
        proxyId: String?,
    ): String? = null
    override fun rebaseServerImageUrl(rawUrl: String): String? = null
    override fun forceWebRTCReconnect() = Unit
    override fun connect(connection: ConnectionInfo) = Unit
    override fun connectWebRTC(remoteId: RemoteId) = Unit
    override fun onAppForeground() = Unit
    override fun onAppBackground() = Unit
    override fun disconnectByUser() = Unit
    override fun onExternalConsumerActive() = Unit
    override fun requestCommandRecovery() = Unit
    override fun onPlaybackActive() = Unit
    override fun onExternalConsumerInactive() = Unit
    override fun onPlaybackInactive() = Unit
    override fun forceDisconnect(reason: Exception) = Unit
    override fun noServer() = Unit
}

internal fun testStore(name: String, beforeWrite: (suspend () -> Unit)? = null): ArtworkDiskStore {
    val path = "/tmp/music-assistant-artwork-$name-${Random.nextLong()}".toPath()
    return ArtworkDiskStore(
        cache = DiskCache.Builder()
            .directory(path)
            .maxSizeBytes(TEST_DISK_CACHE_SIZE_BYTES)
            .build(),
        fileSystem = FileSystem.SYSTEM,
        beforeWrite = beforeWrite,
    )
}

private const val TEST_REMOTE_ID = "ABCDEFGHIJKLMNOPQRSTUVWXY2"
private const val TEST_DISK_CACHE_SIZE_BYTES = 16L * 1024L * 1024L

internal fun response(
    bytes: ByteArray,
    vararg headers: Pair<String, String>,
): ArtworkResponse = ArtworkResponse(
    bytes = bytes,
    headers = Headers.build {
        headers.forEach { append(it.first, it.second) }
    },
    status = 200,
)
