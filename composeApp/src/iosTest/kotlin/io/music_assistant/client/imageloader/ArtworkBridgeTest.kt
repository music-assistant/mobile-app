@file:OptIn(
    kotlinx.cinterop.ExperimentalForeignApi::class,
    kotlinx.coroutines.ExperimentalCoroutinesApi::class,
)

package io.music_assistant.client.imageloader

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.Headers
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.music_assistant.client.api.Answer
import io.music_assistant.client.api.ConnectionInfo
import io.music_assistant.client.api.Request
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.data.model.server.events.Event
import io.music_assistant.client.di.KmpHelper
import io.music_assistant.client.utils.SessionState
import io.music_assistant.client.webrtc.DataChannelWrapper
import io.music_assistant.client.webrtc.WebRTCHttpProxy
import io.music_assistant.client.webrtc.model.RemoteId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.yield
import okio.FileSystem
import okio.Path.Companion.toPath
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlinx.coroutines.CoroutineDispatcher
import kotlin.coroutines.CoroutineContext
import kotlin.native.concurrent.ThreadLocal
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ArtworkBridgeTest {
    @Test
    fun real_transport_timeout_completes_null_but_cancellation_does_not_complete() = runBlocking(Dispatchers.Default) {
        val mainDispatcher = RecordingMainDispatcher()
        Dispatchers.setMain(mainDispatcher)
        val cancellationStarted = CompletableDeferred<Unit>()
        val cancellationFinished = CompletableDeferred<Unit>()
        val client = HttpClient(MockEngine { request ->
            if (request.url.toString() == CANCEL_URL) {
                cancellationStarted.complete(Unit)
                try {
                    CompletableDeferred<Unit>().await()
                } finally {
                    cancellationFinished.complete(Unit)
                }
            }
            delay(5_000L)
            respond(byteArrayOf(1), HttpStatusCode.OK)
        })
        val serviceClient = BridgeServiceClient()
        val timeoutTransport = KtorArtworkTransport(client, serviceClient, timeoutMs = 40L)
        val cancellationTransport = KtorArtworkTransport(client, serviceClient, timeoutMs = 30_000L)
        val transport = RoutingArtworkTransport(
            timeoutTransport = timeoutTransport,
            cancellationTransport = cancellationTransport,
        )
        val cachePath = ("/tmp/music-assistant-ios-bridge-timeout-${Random.nextLong()}").toPath()
        val cache = coil3.disk.DiskCache.Builder()
            .directory(cachePath)
            .maxSizeBytes(16L * 1024L * 1024L)
            .build()
        val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        startKoin {
            modules(
                module {
                    single<ServiceClient> { serviceClient }
                    single { cache }
                    single { ArtworkDiskStore(get()) }
                    single<ArtworkTransport> { transport }
                    single {
                        ArtworkRepository(
                            store = get(),
                            transport = get(),
                            serviceClient = get(),
                            now = { 1_000L },
                            scope = repositoryScope,
                        )
                    }
                },
            )
        }

        try {
            val timedOut = CompletableDeferred<io.music_assistant.client.di.NativeArtworkResult?>()
            KmpHelper.loadArtwork(TIMEOUT_URL) { timedOut.complete(it) }
            assertEquals(null, awaitOnWorker(timedOut))
            assertTrue(timedOut.isCompleted)

            val cancelled = CompletableDeferred<io.music_assistant.client.di.NativeArtworkResult?>()
            val request = KmpHelper.loadArtwork(CANCEL_URL) { cancelled.complete(it) }
            awaitOnWorker(cancellationStarted)
            request.cancel()
            awaitOnWorker(cancellationFinished)
            assertFalse(cancelled.isCompleted)
        } finally {
            repositoryScope.cancel()
            client.close()
            cache.shutdown()
            FileSystem.SYSTEM.deleteRecursively(cachePath)
            stopKoin()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun bridge_result_carries_invalidation_token() = runBlocking(Dispatchers.Default) {
        val mainDispatcher = RecordingMainDispatcher()
        Dispatchers.setMain(mainDispatcher)
        val transport = BridgeTransport()
        val cachePath = ("/tmp/music-assistant-ios-bridge-${Random.nextLong()}").toPath()
        val cache = coil3.disk.DiskCache.Builder()
            .directory(cachePath)
            .maxSizeBytes(16L * 1024L * 1024L)
            .build()
        val clock = MutableClock(1_000L)
        val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        startKoin {
            modules(
                module {
                    single<ServiceClient> { BridgeServiceClient() }
                    single { cache }
                    single { ArtworkDiskStore(get()) }
                    single<ArtworkTransport> { transport }
                    single {
                        ArtworkRepository(
                            store = get(),
                            transport = get(),
                            serviceClient = get(),
                            now = clock::value,
                            scope = repositoryScope,
                        )
                    }
                },
            )
        }

        try {
            val first = CompletableDeferred<BridgeCallback>()
            KmpHelper.loadArtwork(URL) { result ->
                first.complete(BridgeCallback(result, mainDispatcher.isExecutingOnDispatcher))
            }
            val firstCallback = awaitOnWorker(first)
            val firstResult = assertNotNull(firstCallback.result)
            assertTrue(firstCallback.wasExecutingOnConfiguredDispatcher)
            assertTrue(firstResult.data.length > 0uL)
            assertEquals("image/png", firstResult.mimeType)
            assertNotNull(firstResult.token)

            clock.value = 62_000L
            val second = CompletableDeferred<BridgeCallback>()
            KmpHelper.loadArtwork(URL) { result ->
                second.complete(BridgeCallback(result, mainDispatcher.isExecutingOnDispatcher))
            }
            val secondCallback = awaitOnWorker(second)
            val secondResult = assertNotNull<io.music_assistant.client.di.NativeArtworkResult>(secondCallback.result)
            assertEquals(SECOND_BYTES.size.toULong(), secondResult.data.length)
            assertEquals(2, transport.calls)

            // An old token cannot evict the newer bytes stored for the same URL.
            KmpHelper.invalidateArtwork(firstResult.token)
            val warm = CompletableDeferred<BridgeCallback>()
            KmpHelper.loadArtwork(URL) { result ->
                warm.complete(BridgeCallback(result, mainDispatcher.isExecutingOnDispatcher))
            }
            val warmCallback = awaitOnWorker(warm)
            val warmResult = assertNotNull<io.music_assistant.client.di.NativeArtworkResult>(warmCallback.result)
            assertEquals(SECOND_BYTES.size.toULong(), warmResult.data.length)
            assertEquals(2, transport.calls)

            val cancelled = CompletableDeferred<BridgeCallback>()
            transport.gate = CompletableDeferred()
            val request = KmpHelper.loadArtwork(CANCEL_URL) { result ->
                cancelled.complete(BridgeCallback(result, mainDispatcher.isExecutingOnDispatcher))
            }
            awaitOnWorker(transport.started)
            request.cancel()
            transport.gate?.complete(transport.responseFor(CANCEL_URL))
            kotlinx.coroutines.runBlocking(Dispatchers.Default) {
                kotlinx.coroutines.delay(250L)
            }
            assertFalse(cancelled.isCompleted)
        } finally {
            repositoryScope.cancel()
            cache.shutdown()
            FileSystem.SYSTEM.deleteRecursively(cachePath)
            stopKoin()
            Dispatchers.resetMain()
        }
    }

    private fun <T> awaitOnWorker(value: CompletableDeferred<T>): T =
        kotlinx.coroutines.runBlocking(Dispatchers.Default) {
            kotlinx.coroutines.withTimeout(TIMEOUT_MS) { value.await() }
        }

    private class RecordingMainDispatcher : CoroutineDispatcher() {
        val isExecutingOnDispatcher: Boolean
            get() = DispatcherExecution.depth > 0

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            DispatcherExecution.depth++
            try {
                block.run()
            } finally {
                DispatcherExecution.depth--
            }
        }
    }

    private data class BridgeCallback(
        val result: io.music_assistant.client.di.NativeArtworkResult?,
        val wasExecutingOnConfiguredDispatcher: Boolean,
    )

    @ThreadLocal
    private object DispatcherExecution {
        var depth = 0
    }

    private class MutableClock(var value: Long)

    private class RoutingArtworkTransport(
        private val timeoutTransport: ArtworkTransport,
        private val cancellationTransport: ArtworkTransport,
    ) : ArtworkTransport {
        override suspend fun fetch(url: String, context: ArtworkRequestContext): ArtworkResponse =
            if (url == CANCEL_URL) {
                cancellationTransport.fetch(url, context)
            } else {
                timeoutTransport.fetch(url, context)
            }
    }

    private class BridgeTransport : ArtworkTransport {
        var calls = 0
        var gate: CompletableDeferred<ArtworkResponse>? = null
        var started = CompletableDeferred<Unit>()
        var timeout = false

        override suspend fun fetch(url: String, context: ArtworkRequestContext): ArtworkResponse {
            calls++
            if (timeout) error("artwork HTTP request timed out")
            gate?.let {
                started.complete(Unit)
                return it.await()
            }
            return when (calls) {
                1 -> response(FIRST_BYTES)
                2 -> response(SECOND_BYTES)
                else -> responseFor(url)
            }
        }

        fun responseFor(url: String) = ArtworkResponse(
            bytes = url.encodeToByteArray(),
            headers = headersOf(
                "Content-Type" to listOf("image/png"),
                "Cache-Control" to listOf("max-age=60"),
            ),
            status = 200,
        )

        private fun response(bytes: ByteArray) = ArtworkResponse(
            bytes = bytes,
            headers = headersOf(
                "Content-Type" to listOf("image/png"),
                "Cache-Control" to listOf("max-age=60"),
            ),
            status = 200,
        )
    }

    private class BridgeServiceClient : ServiceClient {
        override val sessionState: StateFlow<SessionState> =
            MutableStateFlow(SessionState.Disconnected.Initial)
        override val isReadyForCommands = MutableStateFlow(false)
        override val externalConsumerActive = MutableStateFlow(false)
        override val events: Flow<Event<out Any>> = emptyFlow()
        override val webrtcSendspinChannel: DataChannelWrapper? = null
        override val foregroundEvents: Flow<Unit> = emptyFlow()
        override val webRTCHttpProxy: WebRTCHttpProxy? = null

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

    private companion object {
        const val URL = "https://example.test/artwork.png"
        const val CANCEL_URL = "https://example.test/cancel.png"
        const val TIMEOUT_URL = "https://example.test/timeout.png"
        const val TIMEOUT_MS = 10_000L
        val FIRST_BYTES = byteArrayOf(1, 2, 3)
        val SECOND_BYTES = byteArrayOf(4, 5, 6, 7)
    }
}
