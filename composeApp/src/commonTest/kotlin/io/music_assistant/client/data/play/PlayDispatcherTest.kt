package io.music_assistant.client.data.play

import io.music_assistant.client.api.Answer
import io.music_assistant.client.api.Request
import io.music_assistant.client.data.model.client.QueueOption
import io.music_assistant.client.data.model.server.StubServiceClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class PlayDispatcherTest {
    @Test
    fun `ten different taps during a running request send two requests`() = runTest {
        val client = GatedClient()
        val dispatcher = PlayDispatcher(client, backgroundScope)

        dispatcher.play(play("0"))
        runCurrent()
        (1..9).forEach { dispatcher.play(play("$it")) }
        runCurrent()
        client.answer()
        runCurrent()
        client.answer()
        runCurrent()

        assertEquals(listOf("0", "9"), client.sentMedia)
        assertEquals(emptySet(), dispatcher.busyQueueIds.value)
    }

    @Test
    fun `a repeat of the running request is not sent`() = runTest {
        val client = GatedClient()
        val dispatcher = PlayDispatcher(client, backgroundScope)

        dispatcher.play(play("a"))
        runCurrent()
        dispatcher.play(play("a"))
        client.answer()
        runCurrent()

        assertEquals(listOf("a"), client.sentMedia)
    }

    @Test
    fun `a failed request does not stop the next one`() = runTest {
        val client = GatedClient()
        val dispatcher = PlayDispatcher(client, backgroundScope)

        dispatcher.play(play("a"))
        dispatcher.play(play("add-b", QueueOption.ADD))
        runCurrent()
        client.answer(Result.failure(IllegalStateException("boom")))
        runCurrent()

        assertEquals(listOf("a", "add-b"), client.sentMedia)
    }

    @Test
    fun `a queue is busy from the tap until its last answer even a failed one`() = runTest {
        val client = GatedClient()
        val dispatcher = PlayDispatcher(client, backgroundScope)

        dispatcher.play(play("a"))
        runCurrent()
        assertEquals(setOf(QUEUE), dispatcher.busyQueueIds.value)

        // RpcEngine.failAll answers every open request this way when the connection drops.
        client.answer(Result.failure(IllegalStateException("connection lost")))
        runCurrent()
        assertEquals(emptySet(), dispatcher.busyQueueIds.value)
    }

    @Test
    fun `a tap after the queue went idle starts a new request`() = runTest {
        val client = GatedClient()
        val dispatcher = PlayDispatcher(client, backgroundScope)

        dispatcher.play(play("a"))
        runCurrent()
        client.answer()
        runCurrent()
        dispatcher.play(play("a"))
        runCurrent()

        assertEquals(listOf("a", "a"), client.sentMedia)
    }

    @Test
    fun `queues do not wait for each other`() = runTest {
        val client = GatedClient()
        val dispatcher = PlayDispatcher(client, backgroundScope)

        dispatcher.play(play("a", queue = "kitchen"))
        dispatcher.play(play("b", queue = "office"))
        runCurrent()

        assertEquals(listOf("a", "b"), client.sentMedia)
        assertEquals(setOf("kitchen", "office"), dispatcher.busyQueueIds.value)
    }

    /** Holds every request until the test answers it, oldest first. */
    private class GatedClient : StubServiceClient() {
        private val sent = mutableListOf<Request>()
        private val pending = ArrayDeque<CompletableDeferred<Result<Answer>>>()

        val sentMedia: List<String>
            get() = sent.map { request ->
                request.args?.get("media")?.jsonArray?.single()?.jsonPrimitive?.content.orEmpty()
            }

        override suspend fun sendRequest(request: Request): Result<Answer> {
            sent += request
            return CompletableDeferred<Result<Answer>>().also { pending.addLast(it) }.await()
        }

        fun answer(result: Result<Answer> = Result.success(emptyAnswer())) {
            pending.removeFirst().complete(result)
        }
    }

    private companion object {
        const val QUEUE = "queue"

        fun play(media: String, option: QueueOption = QueueOption.PLAY, queue: String = QUEUE) =
            PlayRequest(queueOrPlayerId = queue, media = listOf(media), option = option)

        fun emptyAnswer() = Answer(json = buildJsonObject { put("message_id", JsonPrimitive("fake")) })
    }
}
