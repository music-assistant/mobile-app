package io.music_assistant.client.imageloader

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.ByteChannel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ArtworkTransportTimeoutTest {
    @Test
    fun stalled_body_channel_is_cancelled_at_timeout() = runBlocking {
        var calls = 0
        val client = HttpClient(
            MockEngine {
                calls++
                if (calls == 1) {
                    val channel = ByteChannel(autoFlush = true)
                    respond(
                        channel,
                        HttpStatusCode.OK,
                        Headers.build { append(HttpHeaders.ContentType, "image/png") },
                    )
                } else {
                    respond(byteArrayOf(1, 2, 3), HttpStatusCode.OK)
                }
            },
        )
        try {
            val transport = KtorArtworkTransport(
                client,
                MutableArtworkServiceClient(),
                timeoutMs = 30L,
            )
            assertFailsWith<IllegalStateException> {
                transport.fetch("https://example.test/stalled", ArtworkRequestContext(null, null))
            }
            assertEquals(
                listOf<Byte>(1, 2, 3),
                transport.fetch("https://example.test/stalled", ArtworkRequestContext(null, null)).bytes.toList(),
            )
            assertEquals(2, calls)
            Unit
        } finally {
            client.close()
        }
    }

    @Test
    fun concrete_http_timeout_cancels_body_and_allows_retry() = runBlocking {
        var calls = 0
        val client = HttpClient(
            MockEngine(
                MockEngineConfig().apply {
                    addHandler { _: HttpRequestData ->
                        calls++
                        if (calls == 1) delay(5_000L)
                        respond(byteArrayOf(1, 2, 3), HttpStatusCode.OK)
                    }
                },
            ),
        )
        try {
            val transport = KtorArtworkTransport(
                client,
                MutableArtworkServiceClient(),
                timeoutMs = 30L,
            )
            assertFailsWith<IllegalStateException> {
                transport.fetch("https://example.test/timeout", ArtworkRequestContext(null, null))
            }
            assertEquals(1, calls)
            val response = transport.fetch("https://example.test/timeout", ArtworkRequestContext(null, null))
            assertEquals(listOf<Byte>(1, 2, 3), response.bytes.toList())
            assertEquals(2, calls)
        } finally {
            client.close()
        }
    }

    @Test
    fun caller_cancellation_is_not_translated_to_timeout_failure() = runBlocking {
        val client = HttpClient(
            MockEngine {
                delay(5_000L)
                respond(byteArrayOf(1), HttpStatusCode.OK)
            },
        )
        try {
            val transport = KtorArtworkTransport(
                client,
                MutableArtworkServiceClient(),
                timeoutMs = 5_000L,
            )
            val request = async {
                transport.fetch("https://example.test/cancel", ArtworkRequestContext(null, null))
            }
            request.cancel()
            assertFailsWith<CancellationException> { request.await() }
            Unit
        } finally {
            client.close()
        }
    }
}
