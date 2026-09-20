package io.music_assistant.client.imageloader

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

class ArtworkRepositorySingleFlightTest {
    @Test
    fun cancel_one_waiter() = runTest {
        val transport = GateTransport()
        val repository = repository(transport)
        val first = async { repository.load(URL, ArtworkReadPolicy.DISABLED) }
        val second = async { repository.load(URL, ArtworkReadPolicy.DISABLED) }
        transport.started.await()
        first.cancel()
        transport.result.complete(response(BYTES))
        assertEquals(BYTES.toList(), second.await().bytes.toList())
        assertEquals(1, transport.calls)
    }

    @Test
    fun cancel_last_waiter() = runTest {
        val transport = GateTransport()
        val repository = repository(transport)
        val request = launch { repository.load(URL, ArtworkReadPolicy.DISABLED) }
        transport.started.await()
        request.cancel()
        request.join()
        transport.result.cancel()
        assertTrue(transport.calls == 1)
        transport.result = CompletableDeferred<ArtworkResponse>()
        transport.result.complete(response(BYTES))
        assertEquals(BYTES.toList(), repository.load(URL, ArtworkReadPolicy.DISABLED).bytes.toList())
        assertTrue(transport.calls >= 2)
    }

    @Test
    fun failure_then_retry() = runTest {
        val transport = QueueTransport(IllegalStateException("failed"), response(BYTES))
        val repository = repository(transport)
        assertFails { repository.load(URL, ArtworkReadPolicy.DISABLED) }
        assertEquals(BYTES.toList(), repository.load(URL, ArtworkReadPolicy.DISABLED).bytes.toList())
        assertEquals(2, transport.calls)
    }

    @Test
    fun old_flight_cannot_remove_replacement() = runTest {
        val transport = ReplacementTransport()
        val repository = repository(transport)
        val old = async { repository.load(URL, ArtworkReadPolicy.DISABLED) }
        transport.oldStarted.await()
        old.cancel()
        old.join()
        val replacement = async { repository.load(URL, ArtworkReadPolicy.DISABLED) }
        transport.newStarted.await()
        transport.oldResult.complete(response("old".encodeToByteArray()))
        transport.newResult.complete(response(BYTES))
        assertEquals(BYTES.toList(), replacement.await().bytes.toList())
        assertEquals(2, transport.calls)
    }

    @Test
    fun caller_cancellation_releases_singleflight() = runTest {
        val transport = GateTransport()
        val repository = repository(transport)
        val request = async { repository.load(URL, ArtworkReadPolicy.DISABLED) }
        transport.started.await()
        request.cancel()
        assertFails { request.await() }
        transport.result.complete(response(BYTES))
        val retry = repository.load(URL, ArtworkReadPolicy.DISABLED)
        assertEquals(BYTES.toList(), retry.bytes.toList())
        assertTrue(transport.calls >= 2)
    }

    @Test
    fun cancellation_during_disk_write_is_not_a_success() = runTest {
        val transport = RecordingArtworkTransport()
        val gate = CompletableDeferred<Unit>()
        val repository = ArtworkRepository(
            store = testStore("cancel-write") { gate.await() },
            transport = transport,
            serviceClient = MutableArtworkServiceClient(),
            now = { 1_000L },
        )
        val request = async { repository.load(URL) }
        while (transport.calls.isEmpty()) testScheduler.runCurrent()
        request.cancel()
        gate.cancel()
        assertFails { request.await() }
    }

    @Test
    fun mixed_policy_requests_respect_isolation() = runTest {
        val transport = RecordingArtworkTransport().apply { awaitNext = true }
        val repository = repository(transport)
        val readWrite = async { repository.load(URL, ArtworkReadPolicy.READ_WRITE) }
        testScheduler.advanceUntilIdle()
        assertFails { repository.load(URL, ArtworkReadPolicy.READ_ONLY) }
        val disabled = async { repository.load(URL, ArtworkReadPolicy.DISABLED) }
        testScheduler.advanceUntilIdle()
        assertEquals(2, transport.calls.size)
        transport.next.complete(response(BYTES))
        readWrite.await()
        disabled.await()
        assertEquals(2, transport.calls.size)

        val equalTransport = RecordingArtworkTransport().apply { awaitNext = true }
        val equalRepository = repository(equalTransport)
        val equalA = async { equalRepository.load("https://example.test/equal", ArtworkReadPolicy.DISABLED) }
        val equalB = async { equalRepository.load("https://example.test/equal", ArtworkReadPolicy.DISABLED) }
        testScheduler.advanceUntilIdle()
        assertEquals(1, equalTransport.calls.size)
        equalTransport.next.complete(response(BYTES))
        equalA.await()
        equalB.await()
        assertEquals(1, equalTransport.calls.size)
    }

    private fun repository(
        transport: ArtworkTransport,
        scope: CoroutineScope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob()),
    ): ArtworkRepository = ArtworkRepository(
        store = testStore("singleflight"),
        transport = transport,
        serviceClient = MutableArtworkServiceClient(),
        now = { 1_000L },
        scope = scope,
    )

    private class GateTransport : ArtworkTransport {
        val started = CompletableDeferred<Unit>()
        var result = CompletableDeferred<ArtworkResponse>()
        var calls = 0

        override suspend fun fetch(url: String, context: ArtworkRequestContext): ArtworkResponse {
            calls++
            started.complete(Unit)
            return result.await()
        }
    }

    private class QueueTransport(private vararg val responses: Any) : ArtworkTransport {
        var calls = 0
        override suspend fun fetch(url: String, context: ArtworkRequestContext): ArtworkResponse {
            val response = responses[calls++]
            if (response is Throwable) throw response
            return response as ArtworkResponse
        }
    }

    private class ReplacementTransport : ArtworkTransport {
        val oldStarted = CompletableDeferred<Unit>()
        val newStarted = CompletableDeferred<Unit>()
        val oldResult = CompletableDeferred<ArtworkResponse>()
        val newResult = CompletableDeferred<ArtworkResponse>()
        var calls = 0

        override suspend fun fetch(url: String, context: ArtworkRequestContext): ArtworkResponse {
            calls++
            return if (calls == 1) {
                oldStarted.complete(Unit)
                oldResult.await()
            } else {
                newStarted.complete(Unit)
                newResult.await()
            }
        }
    }

    private companion object {
        const val URL = "https://example.test/art.png"
        val BYTES = byteArrayOf(1, 2, 3)
    }
}
