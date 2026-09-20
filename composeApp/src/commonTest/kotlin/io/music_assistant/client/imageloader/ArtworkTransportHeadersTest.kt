package io.music_assistant.client.imageloader

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class ArtworkTransportHeadersTest {
    @Test
    fun repeated_cache_control_no_store_is_preserved_in_both_orders() = runTest {
        listOf(
            listOf("no-store", "max-age=3600"),
            listOf("max-age=3600", "no-store"),
        ).forEachIndexed { index, cacheControlValues ->
            var requests = 0
            val body = byteArrayOf(0x01, index.toByte(), 0x03)
            val client = HttpClient(
                MockEngine(
                    MockEngineConfig().apply {
                        addHandler {
                            requests++
                            respond(
                                content = body,
                                status = HttpStatusCode.OK,
                                headers = headersOf(
                                    "Content-Type" to listOf(ContentType.Image.PNG.toString()),
                                    "Cache-Control" to cacheControlValues,
                                ),
                            )
                        }
                    },
                ),
            )
            val store = testStore("transport-headers-$index")
            val repository = ArtworkRepository(
                store = store,
                transport = KtorArtworkTransport(client, MutableArtworkServiceClient()),
                serviceClient = MutableArtworkServiceClient(),
                now = { 1_000L },
                scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            )
            val url = "https://example.test/repeated-cache-control-$index.png"
            try {
                val first = repository.load(url)
                val second = repository.load(url)

                assertContentEquals(body, first.bytes)
                assertContentEquals(body, second.bytes)
                assertFalse(first.reusable)
                assertFalse(second.reusable)
                assertEquals(2, requests)
                assertNull(store.read(first.token.identity, 1_000L))
            } finally {
                client.close()
            }
        }
    }
}
