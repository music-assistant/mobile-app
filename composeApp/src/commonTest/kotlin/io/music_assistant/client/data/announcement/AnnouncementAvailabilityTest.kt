package io.music_assistant.client.data.announcement

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondOk
import io.music_assistant.client.api.APICommands
import io.music_assistant.client.api.Answer
import io.music_assistant.client.api.ConnectionInfo
import io.music_assistant.client.api.ErrorMessageBus
import io.music_assistant.client.api.Request
import io.music_assistant.client.data.model.server.ServerInfo
import io.music_assistant.client.data.model.server.StubServiceClient
import io.music_assistant.client.data.model.server.User
import io.music_assistant.client.utils.ConnectionData
import io.music_assistant.client.utils.SessionState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AnnouncementAvailabilityTest {
    private class FakeClient(engines: Int) : StubServiceClient() {
        override val sessionState = MutableStateFlow<SessionState>(SessionState.Disconnected.Initial)
        val commands = mutableListOf<String>()
        private val engineList = JsonArray(
            List(engines) { buildJsonObject { put("uid", JsonPrimitive("tts.$it")) } },
        )

        override suspend fun sendRequest(request: Request): Result<Answer> {
            commands += request.command
            return Result.success(Answer(buildJsonObject { put("result", engineList) }))
        }
    }

    private fun connected(schema: Int) = SessionState.Connected.Direct(
        connectionInfo = ConnectionInfo("nas.local", 8095, isTls = false),
        connectionData = ConnectionData(
            serverInfo = ServerInfo(serverId = "server-1", schemaVersion = schema),
            user = User(userId = "u1"),
            token = "token-1",
        ),
    )

    private fun TestScope.availability(client: FakeClient): StateFlow<AnnouncementAvailability> =
        AnnouncementRepository(
            apiClient = client,
            httpClient = HttpClient(MockEngine { respondOk() }),
            microphone = object : MicrophoneCapture {
                override fun frames() = emptyFlow<PcmFrame>()
            },
            errorBus = ErrorMessageBus(),
            scope = backgroundScope,
        ).availability

    @Test
    fun `voice needs schema 48 and text needs a speech engine`() = runTest {
        val client = FakeClient(engines = 1)
        val availability = availability(client)

        client.sessionState.value = connected(schema = 47)
        runCurrent()
        assertEquals(AnnouncementAvailability(text = true, voice = false), availability.value)

        client.sessionState.value = SessionState.Disconnected.Initial
        runCurrent()
        assertEquals(AnnouncementAvailability(), availability.value)

        client.sessionState.value = connected(schema = 48)
        runCurrent()
        assertEquals(AnnouncementAvailability(text = true, voice = true), availability.value)
    }

    @Test
    fun `no speech engine hides text`() = runTest {
        val client = FakeClient(engines = 0)
        val availability = availability(client)

        client.sessionState.value = connected(schema = 60)
        runCurrent()
        assertEquals(AnnouncementAvailability(text = false, voice = true), availability.value)
    }

    @Test
    fun `older servers are never asked for engines`() = runTest {
        val client = FakeClient(engines = 1)
        val availability = availability(client)

        client.sessionState.value = connected(schema = 45)
        runCurrent()
        assertEquals(AnnouncementAvailability(), availability.value)
        assertTrue(APICommands.PLAYERS_TTS_ENGINES !in client.commands)
    }
}
