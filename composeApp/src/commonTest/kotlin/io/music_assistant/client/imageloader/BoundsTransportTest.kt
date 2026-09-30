package io.music_assistant.client.imageloader

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith

class BoundsTransportTest {
    @Test
    fun oversize_http_stream_rejected() = runTest {
        assertFailsWith<Throwable> {
            transport(
                content = ByteArray(1),
                headers = headersOf(HttpHeaders.ContentLength, (16L * 1024L * 1024L + 1L).toString()),
            ).fetch("https://example.test/declared", ArtworkRequestContext(null, null))
        }
        assertFailsWith<Throwable> {
            transport(
                content = ByteArray(16 * 1024 * 1024 + 1),
                headers = headersOf(HttpHeaders.ContentType, ContentType.Image.PNG.toString()),
            ).fetch("https://example.test/missing", ArtworkRequestContext(null, null))
        }
        assertFailsWith<Throwable> {
            transport(
                content = ByteArray(16 * 1024 * 1024 + 1),
                headers = headersOf(HttpHeaders.ContentLength, "1"),
            ).fetch("https://example.test/lying", ArtworkRequestContext(null, null))
        }
        assertFailsWith<Throwable> {
            transport(content = ByteArray(0)).fetch("https://example.test/empty", ArtworkRequestContext(null, null))
        }
        assertFailsWith<Throwable> {
            transport(content = byteArrayOf(1, 2, 3), status = HttpStatusCode.NotFound)
                .fetch("https://example.test/error", ArtworkRequestContext(null, null))
        }
    }

    private fun transport(
        content: ByteArray,
        status: HttpStatusCode = HttpStatusCode.OK,
        headers: Headers = headersOf(),
    ): KtorArtworkTransport = KtorArtworkTransport(
        httpClient = HttpClient(
            MockEngine(
                MockEngineConfig().apply {
            addHandler { _: HttpRequestData ->
                respond(content = content, status = status, headers = headers)
            }
        },
            ),
        ),
        serviceClient = NoopArtworkServiceClient,
    )
}

private object NoopArtworkServiceClient : io.music_assistant.client.api.ServiceClient {
    override val sessionState = kotlinx.coroutines.flow.MutableStateFlow<io.music_assistant.client.utils.SessionState>(
        io.music_assistant.client.utils.SessionState.Disconnected.Initial,
    )
    override suspend fun sendRequest(request: io.music_assistant.client.api.Request) =
        error("not used") as Result<io.music_assistant.client.api.Answer>
    override suspend fun login(username: String, password: String) = Unit
    override suspend fun authorize(token: String, isAutoLogin: Boolean) = Unit
    override fun logout() = Unit
    override val isReadyForCommands = kotlinx.coroutines.flow.MutableStateFlow(false)
    override val externalConsumerActive = kotlinx.coroutines.flow.MutableStateFlow(false)
    override fun resolveImageUrl(path: String, provider: String, isRemotelyAccessible: Boolean, proxyId: String?) = null
    override fun rebaseServerImageUrl(rawUrl: String) = null
    override val webRTCHttpProxy: io.music_assistant.client.webrtc.WebRTCHttpProxy? = null
    override fun forceWebRTCReconnect() = Unit
    override val events = kotlinx.coroutines.flow.emptyFlow<io.music_assistant.client.data.model.server.events.Event<out Any>>()
    override val webrtcSendspinChannel: io.music_assistant.client.webrtc.DataChannelWrapper? = null
    override fun onAppForeground() = Unit
    override fun onAppBackground() = Unit
    override val foregroundEvents = kotlinx.coroutines.flow.emptyFlow<Unit>()
    override fun disconnectByUser() = Unit
    override fun connect(connection: io.music_assistant.client.api.ConnectionInfo) = Unit
    override fun connectWebRTC(remoteId: io.music_assistant.client.webrtc.model.RemoteId) = Unit
    override fun onExternalConsumerActive() = Unit
    override fun requestCommandRecovery() = Unit
    override fun onPlaybackActive() = Unit
    override fun onExternalConsumerInactive() = Unit
    override fun onPlaybackInactive() = Unit
    override fun forceDisconnect(reason: Exception) = Unit
    override fun noServer() = Unit
}
