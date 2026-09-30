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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ArtworkTransportTimeoutTest {
    @Test
    fun stalled_body_channel_is_cancelled_at_timeout() = runTest {
        var calls = 0
        var stalledChannel: ByteChannel? = null
        val client = HttpClient(
            MockEngine(
                MockEngineConfig().apply {
                    dispatcher = StandardTestDispatcher(testScheduler)
                    addHandler {
                        calls++
                        if (calls == 1) {
                            val channel = ByteChannel(autoFlush = true)
                            stalledChannel = channel
                            respond(
                                channel,
                                HttpStatusCode.OK,
                                Headers.build { append(HttpHeaders.ContentType, "image/png") },
                            )
                        } else {
                            respond(byteArrayOf(1, 2, 3), HttpStatusCode.OK)
                        }
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
            val stalled = async {
                assertFailsWith<IllegalStateException> {
                    transport.fetch("https://example.test/stalled", ArtworkRequestContext(null, null))
                }
            }
            runCurrent()
            advanceTimeBy(30L)
            runCurrent()
            stalled.await()
            assertTrue(stalledChannel?.isClosedForRead == true)
            assertEquals(
                listOf<Byte>(1, 2, 3),
                transport.fetch("https://example.test/stalled", ArtworkRequestContext(null, null)).bytes.toList(),
            )
            assertEquals(2, calls)
        } finally {
            client.close()
        }
    }

    @Test
    fun concrete_http_timeout_cancels_body_and_allows_retry() = runTest {
        var calls = 0
        val client = HttpClient(
            MockEngine(
                MockEngineConfig().apply {
                    dispatcher = StandardTestDispatcher(testScheduler)
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
            val timedOut = async {
                assertFailsWith<IllegalStateException> {
                    transport.fetch("https://example.test/timeout", ArtworkRequestContext(null, null))
                }
            }
            runCurrent()
            advanceTimeBy(30L)
            runCurrent()
            timedOut.await()
            assertEquals(1, calls)

            val response = async {
                transport.fetch("https://example.test/timeout", ArtworkRequestContext(null, null))
            }
            runCurrent()
            assertEquals(listOf<Byte>(1, 2, 3), response.await().bytes.toList())
            assertEquals(2, calls)
        } finally {
            client.close()
        }
    }

    @Test
    fun caller_cancellation_is_not_translated_to_timeout_failure() = runTest {
        val handlerStarted = CompletableDeferred<Unit>()
        val fetchFailure = CompletableDeferred<Throwable>()
        val client = HttpClient(
            MockEngine(
                MockEngineConfig().apply {
                    dispatcher = StandardTestDispatcher(testScheduler)
                    addHandler {
                        handlerStarted.complete(Unit)
                        delay(5_000L)
                        respond(byteArrayOf(1), HttpStatusCode.OK)
                    }
                },
            ),
        )
        try {
            val transport = KtorArtworkTransport(
                client,
                MutableArtworkServiceClient(),
                timeoutMs = 5_000L,
            )
            val request = launch {
                try {
                    transport.fetch("https://example.test/cancel", ArtworkRequestContext(null, null))
                } catch (throwable: Throwable) {
                    fetchFailure.complete(throwable)
                }
            }
            runCurrent()
            handlerStarted.await()
            request.cancel()
            request.join()

            assertIs<CancellationException>(fetchFailure.await())
        } finally {
            client.close()
        }
    }
}
