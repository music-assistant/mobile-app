package io.music_assistant.client.imageloader

import coil3.Uri
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.Headers
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Integration coverage for the production repository-to-Coil adapter boundary. */
class ArtworkRepositoryIntegrationTest {
    @Test
    fun coil_ownership_matrix() = runTest {
        val fixture = pngFixture(0x21)
        val httpCalls = mutableListOf<String>()
        val httpClient = HttpClient(
            MockEngine { request ->
                httpCalls += request.url.toString()
                respond(
                    content = fixture,
                    status = HttpStatusCode.OK,
                    headers = headersOf(
                        "Content-Type" to listOf("image/png"),
                        "Cache-Control" to listOf("max-age=3600"),
                    ),
                )
            },
        )
        val httpCache = RealArtworkCache()
        val httpRepository = ArtworkRepository(
            httpCache.store,
            KtorArtworkTransport(httpClient, MutableArtworkServiceClient()),
            MutableArtworkServiceClient(),
            now = { 1_000L },
            scope = testScope(),
        )
        val proxyCache = RealArtworkCache()
        val proxyTransport = CoilRecordingArtworkTransport(fixture)
        val proxyRepository = ArtworkRepository(
            proxyCache.store,
            proxyTransport,
            MutableArtworkServiceClient("test-server", io.music_assistant.client.webrtc.WebRTCHttpProxy(sender = {})),
            now = { 1_000L },
            scope = testScope(),
        )
        try {
            val http = "https://example.test/cover.png"
            val firstHttp = httpRepository.load(http)
            val secondHttp = httpRepository.load(Uri(http).toString())
            assertContentEquals(fixture, firstHttp.bytes)
            assertContentEquals(fixture, secondHttp.bytes)
            assertEquals(1, httpCalls.count { it == http })

            val proxy = "mawebrtc://cover/proxy.png"
            proxyRepository.load(proxy)
            proxyRepository.load(Uri(proxy).toString())
            assertEquals(1, proxyTransport.calls.count { it.first == proxy })

            assertFailsWith<Throwable> {
                httpRepository.load("https://example.test/missing.png", ArtworkReadPolicy.READ_ONLY)
            }
            assertEquals(0, httpCalls.count { it.endsWith("missing.png") })
        } finally {
            httpClient.close()
            httpCache.close()
            proxyCache.close()
        }
    }

    @Test
    fun coil_refresh_changes_visible_image() = runTest {
        var now = 1_000L
        val oldBytes = pngFixture(0x31)
        val newBytes = pngFixture(0x32)
        val transport = CoilRecordingArtworkTransport(oldBytes)
        val cache = RealArtworkCache()
        val repository = ArtworkRepository(
            cache.store,
            transport,
            MutableArtworkServiceClient(),
            now = { now },
            scope = testScope(),
        )
        try {
            val url = "https://example.test/refresh.png"
            val first = repository.load(url)
            transport.nextBody = newBytes
            now = 1_000L + 7L * 24L * 60L * 60L * 1_000L + 1L
            val second = repository.load(url)
            assertFalse(first.digest == second.digest)
            assertContentEquals(oldBytes, first.bytes)
            assertContentEquals(newBytes, second.bytes)
            assertEquals(2, transport.calls.count { it.first == url })
            assertTrue(first.token.digest != second.token.digest)
        } finally {
            cache.close()
        }
    }

    @Test
    fun coil_noncacheable_and_cacheonly_policy() = runTest {
        val cache = RealArtworkCache()
        val transport = CoilRecordingArtworkTransport(pngFixture(0x41))
        val repository = ArtworkRepository(
            cache.store,
            transport,
            MutableArtworkServiceClient(),
            now = { 1_000L },
            scope = testScope(),
        )
        try {
            val noStore = "https://example.test/no-store.png"
            transport.nextHeaders = headersOf(
                "Content-Type" to listOf("image/png"),
                "Cache-Control" to listOf("max-age=3600", "no-store"),
            )
            val consumed = repository.load(noStore)
            assertContentEquals(pngFixture(0x41), consumed.bytes)
            assertNull(cache.store.read(consumed.token.identity, 1_000L))
            assertEquals(1, transport.calls.count { it.first == noStore })

            val cacheable = "https://example.test/cacheable.png"
            transport.nextHeaders = headersOf("Content-Type" to listOf("image/png"), "Cache-Control" to listOf("max-age=3600"))
            val stored = repository.load(cacheable)
            assertEquals(ArtworkSource.NETWORK, stored.source)
            assertNotNull(cache.store.read(stored.token.identity, 1_001L))
            transport.nextBody = pngFixture(0x42)
            val diskOnly = repository.load(cacheable, ArtworkReadPolicy.READ_ONLY)
            assertEquals(ArtworkSource.DISK, diskOnly.source)
            assertContentEquals(stored.bytes, diskOnly.bytes)
            assertEquals(1, transport.calls.count { it.first == cacheable })

            val missing = "https://example.test/missing.png"
            assertFailsWith<Throwable> { repository.load(missing, ArtworkReadPolicy.READ_ONLY) }
            assertEquals(0, transport.calls.count { it.first == missing })
        } finally {
            cache.close()
        }
    }

    @Test
    fun write_only_replaces_warm_cache_but_disabled_preserves_it() = runTest {
        val cache = RealArtworkCache()
        val transport = CoilRecordingArtworkTransport(pngFixture(0x61))
        val repository = ArtworkRepository(
            cache.store,
            transport,
            MutableArtworkServiceClient(),
            now = { 1_000L },
            scope = testScope(),
        )
        try {
            val url = "https://example.test/policy-matrix.png"
            val first = repository.load(url)
            transport.nextBody = pngFixture(0x62)
            val writeOnly = repository.load(url, ArtworkReadPolicy.WRITE_ONLY)
            assertEquals(ArtworkSource.NETWORK, writeOnly.source)
            assertContentEquals(pngFixture(0x62), cache.store.read(first.token.identity, 1_001L)!!.bytes)
            transport.nextBody = pngFixture(0x63)
            val disabled = repository.load(url, ArtworkReadPolicy.DISABLED)
            assertEquals(ArtworkSource.NETWORK, disabled.source)
            assertContentEquals(pngFixture(0x62), cache.store.read(first.token.identity, 1_001L)!!.bytes)
        } finally {
            cache.close()
        }
    }

    @Test
    fun decode_failure_versioned_invalidation() = runTest {
        val cache = RealArtworkCache()
        val transport = CoilRecordingArtworkTransport(pngFixture(0x51))
        val repository = ArtworkRepository(
            cache.store,
            transport,
            MutableArtworkServiceClient(),
            now = { 1_000L },
            scope = testScope(),
        )
        try {
            val url = "https://example.test/versioned.png"
            val old = repository.load(url)
            assertTrue(repository.invalidate(old.token))
            assertNull(cache.store.read(old.token.identity, 1_001L))

            transport.nextBody = pngFixture(0x52)
            val newer = repository.load(url)
            assertFalse(repository.invalidate(old.token))
            assertNotNull(cache.store.read(newer.token.identity, 1_001L))
            assertTrue(repository.invalidate(newer.token))
            assertNull(cache.store.read(newer.token.identity, 1_001L))

            transport.nextHeaders = headersOf("Content-Type" to listOf("image/svg+xml"), "Cache-Control" to listOf("max-age=3600"))
            transport.nextBody = "<svg/>".encodeToByteArray()
            val unsupported = repository.load(url)
            assertNotNull(cache.store.read(unsupported.token.identity, 1_001L))
            assertFalse(repository.invalidate(old.token))
        } finally {
            cache.close()
        }
    }

    private fun testScope() = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}

private fun pngFixture(seed: Int): ByteArray = byteArrayOf(
    0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
    0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
    0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
    0x08, 0x06, 0x00, 0x00, 0x00, seed.toByte(), 0x00, 0x00,
    0x00, 0x0A, 0x49, 0x44, 0x41, 0x54, 0x08, 0xD7.toByte(),
    seed.toByte(), 0x00, 0x00, 0x00, 0x02, 0x00, 0x01,
    0xE2.toByte(), 0x21, 0xBC.toByte(), 0x33, 0x00, 0x00, 0x00,
    0x00, 0x49, 0x45, 0x4E, 0x44, 0xAE.toByte(), 0x42, 0x60,
    0x82.toByte(),
)

private class CoilRecordingArtworkTransport(initialBody: ByteArray) : ArtworkTransport {
    val calls = mutableListOf<Pair<String, ArtworkRequestContext>>()
    var nextBody = initialBody
    var nextHeaders: Headers = headersOf(
        "Content-Type" to listOf("image/png"),
        "Cache-Control" to listOf("max-age=3600"),
    )

    override suspend fun fetch(url: String, context: ArtworkRequestContext): ArtworkResponse {
        calls += url to context
        return ArtworkResponse(nextBody.copyOf(), nextHeaders, 200)
    }
}
