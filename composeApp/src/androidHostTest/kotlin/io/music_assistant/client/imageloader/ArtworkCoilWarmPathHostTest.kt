package io.music_assistant.client.imageloader

import coil3.BitmapImage
import coil3.EventListener
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import io.ktor.http.Headers
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.utils.SessionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import okio.FileSystem
import okio.ForwardingFileSystem
import okio.Path
import okio.Source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.lang.reflect.Proxy
import java.util.Base64
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ArtworkCoilWarmPathHostTest {
    @Test
    fun secondDecodedMemoryHitHasNoRepositoryBodyOrMetadataIo() = runBlocking {
        val fixture = HostFixture(reusable = true)
        try {
            val initial = fixture.execute()
            fixture.establishDiskPathsAndResetCounters()
            val cold = fixture.executeWithMemoryMiss()
            assertEquals(DataSource.DISK, cold.dataSource)
            assertTrue(fixture.fileSystem.metadataReads > 0)
            assertTrue(fixture.fileSystem.bodyReads > 0)

            fixture.fileSystem.resetCounters()
            val warm = fixture.execute()

            assertEquals(DataSource.MEMORY_CACHE, warm.dataSource)
            assertEquals(1, fixture.transportCalls.get())
            assertEquals(0, fixture.fileSystem.metadataReads)
            assertEquals(0, fixture.fileSystem.bodyReads)
            assertEquals(initial.memoryCacheKey, warm.memoryCacheKey)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun noStoreResponseIsNotReusedByDecodedMemoryCache() = runBlocking {
        val fixture = HostFixture(reusable = false)
        try {
            val first = fixture.execute()
            val second = fixture.execute()

            assertEquals(2, fixture.transportCalls.get())
            assertNull(first.memoryCacheKey)
            assertNull(second.memoryCacheKey)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun cacheableReplacementRaceUsesT2KeyPixelsAndDoesNotStoreOldT() = runBlocking {
        val fixture = RaceFixture(replacementReusable = true)
        try {
            val first = fixture.execute()
            val oldKey = first.memoryCacheKey
            assertNotNull(oldKey)
            fixture.loader.memoryCache?.clear()
            fixture.armReplacement()

            val raced = fixture.executeWithMemoryKey("race")
            val newKey = raced.memoryCacheKey

            assertEquals(2, fixture.transportCalls.get())
            assertNotNull(newKey)
            assertTrue(newKey?.toString()?.contains(fixture.newDigest) == true)
            assertFalse(newKey?.toString()?.contains(fixture.oldDigest) == true)
            assertBitmapPixel(raced, android.graphics.Color.RED)
            assertEquals(DataSource.DISK, raced.dataSource)
            assertNull(oldKey?.let { fixture.loader.memoryCache?.get(it) })
            assertTrue(fixture.replacementHookExecuted)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun noStoreReplacementRaceHasNullMemoryKeyAndNextRequestUsesTransport() = runBlocking {
        val fixture = RaceFixture(replacementReusable = false)
        try {
            fixture.execute()
            fixture.loader.memoryCache?.clear()
            fixture.armReplacement()

            val raced = fixture.executeWithMemoryKey("race")
            assertNull(raced.memoryCacheKey)
            assertBitmapPixel(raced, android.graphics.Color.RED)
            assertEquals(3, fixture.transportCalls.get())

            val retry = fixture.execute()
            assertNull(retry.memoryCacheKey)
            assertBitmapPixel(retry, android.graphics.Color.RED)
            assertEquals(4, fixture.transportCalls.get())
            assertTrue(fixture.replacementHookExecuted)
        } finally {
            fixture.close()
        }
    }

    private class HostFixture(private val reusable: Boolean) : AutoCloseable {
        private val directory = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "coil-host-${System.nanoTime()}"
        val fileSystem = CountingFileSystem()
        private val cache = coil3.disk.DiskCache.Builder()
            .directory(directory)
            .maxSizeBytes(16L * 1024L * 1024L)
            .build()
        private val transport = object : ArtworkTransport {
            override suspend fun fetch(url: String, context: ArtworkRequestContext): ArtworkResponse =
                ArtworkResponse(
                    bytes = PNG,
                    headers = Headers.build {
                        append("Content-Type", "image/png")
                        append("Cache-Control", if (reusable) "max-age=3600" else "no-store")
                    },
                    status = 200,
                ).also { transportCalls.incrementAndGet() }
        }
        val transportCalls = AtomicInteger()
        private val repository = ArtworkRepository(
            store = ArtworkDiskStore(cache, fileSystem, now = { 1_000L }),
            transport = transport,
            serviceClient = proxyServiceClient(),
            now = { 1_000L },
        )
        private val loader = buildAppImageLoader(RuntimeEnvironment.getApplication(), repository)
        private val request = ImageRequest.Builder(RuntimeEnvironment.getApplication())
            .data(URL)
            .build()

        suspend fun execute(): SuccessResult = loader.execute(request) as SuccessResult

        suspend fun executeWithMemoryMiss(): SuccessResult = loader.execute(
            request.newBuilder().memoryCachePolicy(coil3.request.CachePolicy.DISABLED).build(),
        ) as SuccessResult

        fun establishDiskPathsAndResetCounters() {
            runBlocking {
                val token = repository.resolveFreshToken(URL)
                assertNotNull(token)
                val identity = token?.identity ?: error("missing artwork identity")
                val snapshot = cache.openSnapshot(identity.key)
                assertNotNull(snapshot)
                fileSystem.metadataPath = snapshot?.metadata
                fileSystem.bodyPath = snapshot?.data
                snapshot?.close()
            }
            fileSystem.resetCounters()
        }

        override fun close() {
            loader.shutdown()
            cache.shutdown()
            FileSystem.SYSTEM.deleteRecursively(directory)
        }
    }

    private class RaceFixture(private val replacementReusable: Boolean) : AutoCloseable {
        private val directory = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "coil-host-race-${System.nanoTime()}"
        private val fileSystem = CountingFileSystem()
        private val cache = coil3.disk.DiskCache.Builder()
            .directory(directory)
            .maxSizeBytes(16L * 1024L * 1024L)
            .build()
        private val store = ArtworkDiskStore(cache, fileSystem, now = { 1_000L })
        private var responseBytes = PNG
        val transportCalls = AtomicInteger()
        val oldDigest = runBlocking { artworkSha256Hex(PNG) }
        val newDigest = runBlocking { artworkSha256Hex(PNG_T2) }
        private val transport = object : ArtworkTransport {
            override suspend fun fetch(url: String, context: ArtworkRequestContext): ArtworkResponse =
                ArtworkResponse(
                    bytes = responseBytes,
                    headers = Headers.build {
                        append("Content-Type", "image/png")
                        append(
                            "Cache-Control",
                            if (transportCalls.get() == 0) {
                                "max-age=3600"
                            } else {
                                if (replacementReusable) {
                                    "max-age=3600"
                                } else {
                                    "no-store"
                                }
                            },
                        )
                    },
                    status = 200,
                ).also { transportCalls.incrementAndGet() }
        }
        private val repository = ArtworkRepository(
            store = store,
            transport = transport,
            serviceClient = proxyServiceClient(),
            now = { 1_000L },
        )
        var replacementHookExecuted = false
        private var replaceOnNextResolved = false
        private var replaced = false
        private val productionLoader = buildAppImageLoader(RuntimeEnvironment.getApplication(), repository)
        private val eventListener = object : EventListener() {
            override fun keyStart(request: ImageRequest, input: Any) {
                val data = input as? ResolvedArtworkData ?: return
                if (replaceOnNextResolved && !replaced) {
                    replaced = true
                    replacementHookExecuted = true
                    runBlocking {
                        store.invalidate(data.keyToken.identity, data.keyToken.digest)
                        responseBytes = PNG_T2
                        repository.load(data.url, ArtworkReadPolicy.WRITE_ONLY)
                    }
                }
            }
        }
        val loader: ImageLoader = productionLoader.newBuilder()
            .eventListener(eventListener)
            .build()
            .also { productionLoader.shutdown() }
        private val request = ImageRequest.Builder(RuntimeEnvironment.getApplication())
            .data(URL)
            .build()

        suspend fun execute(): SuccessResult = loader.execute(request) as SuccessResult

        suspend fun executeWithMemoryKey(value: String): SuccessResult = loader.execute(
            request.newBuilder().memoryCacheKey(value).build(),
        ) as SuccessResult

        fun armReplacement() {
            replaceOnNextResolved = true
        }

        override fun close() {
            loader.shutdown()
            cache.shutdown()
            FileSystem.SYSTEM.deleteRecursively(directory)
        }
    }

    private class CountingFileSystem : ForwardingFileSystem(FileSystem.SYSTEM) {
        var metadataReads = 0
        var bodyReads = 0
        var metadataPath: Path? = null
        var bodyPath: Path? = null

        override fun source(file: Path): Source {
            if (file == metadataPath) metadataReads++
            if (file == bodyPath) bodyReads++
            return super.source(file)
        }

        fun resetCounters() {
            metadataReads = 0
            bodyReads = 0
        }
    }

    private fun assertBitmapPixel(result: SuccessResult, expected: Int) {
        val bitmap = (result.image as BitmapImage).bitmap
        assertEquals(expected, bitmap.getPixel(0, 0))
    }

    private companion object {
        const val URL = "https://artwork.test/host.png"
        val PNG: ByteArray = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=",
        )
        val PNG_T2: ByteArray = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4nGP4z8DwHwAFAAH/iZk9HQAAAABJRU5ErkJggg==",
        )

        @Suppress("UNCHECKED_CAST")
        fun proxyServiceClient(): ServiceClient = Proxy.newProxyInstance(
            ServiceClient::class.java.classLoader,
            arrayOf(ServiceClient::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "getSessionState" -> MutableStateFlow<SessionState>(SessionState.Disconnected.Initial)
                "getWebRTCHttpProxy" -> null
                "getIsReadyForCommands", "getExternalConsumerActive" -> MutableStateFlow(false)
                "getEvents", "getForegroundEvents" -> emptyFlow<Any>()
                "getWebrtcSendspinChannel" -> null
                "toString" -> "host-test-service"
                "hashCode" -> 1
                "equals" -> false
                else -> error("Unexpected ServiceClient call: ${method.name}")
            }
        } as ServiceClient
    }
}
