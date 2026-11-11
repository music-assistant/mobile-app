package io.music_assistant.client.data.repository

import io.music_assistant.client.api.APICommands
import io.music_assistant.client.api.Answer
import io.music_assistant.client.api.ConnectionInfo
import io.music_assistant.client.api.Request
import io.music_assistant.client.data.model.client.WaveformKey
import io.music_assistant.client.data.model.server.ServerInfo
import io.music_assistant.client.data.model.server.StubServiceClient
import io.music_assistant.client.data.model.server.WAVEFORM_MIN_SCHEMA
import io.music_assistant.client.ui.compose.common.DataState
import io.music_assistant.client.utils.ConnectionData
import io.music_assistant.client.utils.SessionState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class WaveformRepositoryTest {
    private val key = WaveformKey(itemId = "track-1", provider = "spotify--abc")
    private val bins = JsonArray(listOf(0.0, 0.5, 1.0).map(::JsonPrimitive))

    @Test
    fun `a server without the command is not asked`() = runTest {
        val client = GatedClient(WAVEFORM_MIN_SCHEMA - 1)
        val waveforms = WaveformRepository(client, backgroundScope)

        waveforms.load(key)
        runCurrent()

        assertTrue(client.asked.isEmpty())
        assertIs<DataState.NoData<List<Float>>>(waveforms.waveform(key).value)
    }

    @Test
    fun `a waveform loads once and then stays`() = runTest {
        val client = GatedClient()
        val waveforms = WaveformRepository(client, backgroundScope)

        waveforms.load(key)
        runCurrent()
        assertIs<DataState.Loading<List<Float>>>(waveforms.waveform(key).value)
        waveforms.load(key)
        runCurrent()
        client.answer(Result.success(bins))
        runCurrent()
        waveforms.load(key)
        runCurrent()

        assertEquals(1, client.asked.size)
        val request = client.asked.single()
        assertEquals(APICommands.AUDIO_ANALYSIS_WAVE_FORM, request.command)
        assertEquals(JsonPrimitive(key.itemId), request.args?.get("item_id"))
        assertEquals(JsonPrimitive(key.provider), request.args?.get("provider_instance_id_or_domain"))
        assertEquals(DataState.Data(listOf(0f, 0.5f, 1f)), waveforms.waveform(key).value)
    }

    @Test
    fun `a track without analysis gives no data`() = runTest {
        val client = GatedClient()
        val waveforms = WaveformRepository(client, backgroundScope)

        waveforms.load(key)
        runCurrent()
        client.answer(Result.success(JsonNull))
        runCurrent()

        assertIs<DataState.NoData<List<Float>>>(waveforms.waveform(key).value)
        waveforms.load(key)
        runCurrent()
        assertEquals(1, client.asked.size)
    }

    @Test
    fun `an empty waveform gives no data`() = runTest {
        val client = GatedClient()
        val waveforms = WaveformRepository(client, backgroundScope)

        waveforms.load(key)
        runCurrent()
        client.answer(Result.success(JsonArray(emptyList())))
        runCurrent()

        assertIs<DataState.NoData<List<Float>>>(waveforms.waveform(key).value)
    }

    @Test
    fun `a failed load is asked again on the next load`() = runTest {
        val client = GatedClient()
        val waveforms = WaveformRepository(client, backgroundScope)

        waveforms.load(key)
        runCurrent()
        client.answer(Result.failure(IllegalStateException("connection lost")))
        runCurrent()
        assertIs<DataState.Error<List<Float>>>(waveforms.waveform(key).value)

        waveforms.load(key)
        runCurrent()
        client.answer(Result.success(bins))
        runCurrent()

        assertEquals(2, client.asked.size)
        assertIs<DataState.Data<List<Float>>>(waveforms.waveform(key).value)
    }

    @Test
    fun `the oldest settled waveform goes when the cache is full`() = runTest {
        val client = GatedClient()
        val waveforms = WaveformRepository(client, backgroundScope)
        val keys = List(9) { WaveformKey(itemId = "track-$it", provider = "spotify--abc") }

        keys.forEach { key ->
            waveforms.load(key)
            runCurrent()
            client.answer(Result.success(bins))
            runCurrent()
        }

        assertIs<DataState.NoData<List<Float>>>(waveforms.waveform(keys[0]).value)
        assertIs<DataState.Data<List<Float>>>(waveforms.waveform(keys[1]).value)
        assertIs<DataState.Data<List<Float>>>(waveforms.waveform(keys[8]).value)
    }

    /** Answers each request with the `result` that the test gives, in request order. */
    private class GatedClient(schemaVersion: Int = WAVEFORM_MIN_SCHEMA) : StubServiceClient() {
        val asked = mutableListOf<Request>()
        private val pending = ArrayDeque<CompletableDeferred<Result<Answer>>>()

        override val sessionState: StateFlow<SessionState> = MutableStateFlow(
            SessionState.Connected.Direct(
                connectionInfo = ConnectionInfo(host = "test", port = 8095, isTls = false),
                connectionData = ConnectionData(
                    serverInfo = ServerInfo(serverId = "test", schemaVersion = schemaVersion),
                ),
            ),
        )

        override suspend fun sendRequest(request: Request): Result<Answer> {
            asked += request
            return CompletableDeferred<Result<Answer>>().also { pending.addLast(it) }.await()
        }

        fun answer(result: Result<JsonElement>) {
            pending.removeFirst().complete(result.map { Answer(JsonObject(mapOf("result" to it))) })
        }
    }
}
