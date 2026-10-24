package io.music_assistant.client.data.repository

import io.music_assistant.client.api.ConnectionInfo
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.data.model.server.ServerInfo
import io.music_assistant.client.ui.compose.common.items.singlePayload
import io.music_assistant.client.utils.ConnectionData
import io.music_assistant.client.utils.SessionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShareRepositoryTest {
    private fun repository(client: FakeAiRadioClient, schema: Int? = 100) = ShareRepository(
        object : ServiceClient by client {
            override val sessionState = MutableStateFlow<SessionState>(
                SessionState.Connected.Direct(
                    ConnectionInfo("localhost", 8095, false),
                    ConnectionData(serverInfo = ServerInfo("test", schemaVersion = schema)),
                ),
            )
        },
    )

    @Test
    fun oldOrUnknownSchemaDoesNotSendARequest() = runTest {
        val client = FakeAiRadioClient(mutableMapOf())
        assertNull(repository(client, 99).shareItem("library://track/1"))
        assertNull(repository(client, null).shareItem("library://track/1"))
        assertTrue(client.sent.isEmpty())
    }

    @Test
    fun publicLinksComeOnlyFromTheServer() = runTest {
        val client = FakeAiRadioClient(
            mutableMapOf(
                "music/share_item" to """
            {"title":"A song","text":null,"links":[
              {"provider_domain":"spotify","provider_instance":"spotify--one","url":"https://open.spotify.com/track/123"}
            ]}
        """.trimIndent(),
            ),
        )
        val response = repository(client).shareItem("library://track/42")
        val request = client.sent.single()
        assertEquals("music/share_item", request.command)
        assertEquals(setOf("uri"), request.args?.keys)
        assertEquals("library://track/42", request.args?.get("uri")?.jsonPrimitive?.content)
        val payload = response?.singlePayload()
        assertEquals("https://open.spotify.com/track/123", payload?.value)
        assertEquals("A song", payload?.title)
        assertTrue(payload?.isUrl == true)
    }

    @Test
    fun localFallbackIsPlainText() = runTest {
        val client = FakeAiRadioClient(
            mutableMapOf(
                "music/share_item" to
                    """{"title":"Station","text":"Station • Radio","links":[]}""",
            ),
        )
        val payload = repository(client).shareItem("library://radio/1")?.singlePayload()
        assertEquals("Station • Radio", payload?.value)
        assertFalse(payload?.isUrl ?: true)
    }

    @Test
    fun oldServerHasNoFallback() = runTest {
        val client = FakeAiRadioClient(mutableMapOf("music/share_item" to null))
        assertNull(repository(client).shareItem("filesystem://track/private"))
    }
}
