package io.music_assistant.client.data.repository

import com.russhwolf.settings.MapSettings
import io.music_assistant.client.api.APICommands
import io.music_assistant.client.api.Answer
import io.music_assistant.client.api.Request
import io.music_assistant.client.data.model.client.PlayerDelay
import io.music_assistant.client.data.model.server.GrantedScopes
import io.music_assistant.client.data.model.server.StubServiceClient
import io.music_assistant.client.settings.SettingsRepository
import io.music_assistant.client.utils.myJson
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SendspinDelayRepositoryTest {
    private val admin = MutableStateFlow(GrantedScopes.of(mapOf("admin" to listOf("*")), "admin"))
    private val settings = SettingsRepository(MapSettings(), MapSettings()).apply {
        setSendspinEffectivePlayerId(LOCAL)
    }

    @Test
    fun `a user without the write scope asks nothing`() = runTest {
        val client = FakeClient(mapOf("a" to withDelay(value = 10)))
        val user = MutableStateFlow(GrantedScopes.of(mapOf("user" to listOf("players.control")), "user"))
        val repository = SendspinDelayRepository(client, user, settings, backgroundScope)

        repository.load(listOf("a"))
        runCurrent()

        assertTrue(client.requests.isEmpty())
        assertEquals(setOf(LOCAL), repository.delays.value.keys)
    }

    @Test
    fun `load keeps the players that offer the entry and drops the others`() = runTest {
        val client = FakeClient(mapOf("a" to withDelay(value = null, default = 0), "b" to WITHOUT_DELAY))
        val repository = SendspinDelayRepository(client, admin, settings, backgroundScope)

        repository.load(listOf("a", "b"))
        runCurrent()

        assertEquals(PlayerDelay(0, 0..5000), repository.delays.value["a"])
        assertNull(repository.delays.value["b"])
    }

    @Test
    fun `adjust applies at once within the range and saves the last value only`() = runTest {
        val client = FakeClient(mapOf("a" to withDelay(value = 4950)))
        val repository = SendspinDelayRepository(client, admin, settings, backgroundScope)
        repository.load(listOf("a"))
        runCurrent()

        repository.adjust("a", 10)
        repository.adjust("a", 100)
        runCurrent()
        assertEquals(5000, repository.delays.value.getValue("a").valueMs)

        assertEquals(listOf(5000), client.savedValues("a"))
    }

    @Test
    fun `a step at the edge and an unknown player save nothing`() = runTest {
        val client = FakeClient(mapOf("a" to withDelay(value = 0)))
        val repository = SendspinDelayRepository(client, admin, settings, backgroundScope)
        repository.load(listOf("a"))
        runCurrent()

        repository.adjust("a", -10)
        repository.adjust("unknown", 10)
        runCurrent()

        assertEquals(0, repository.delays.value.getValue("a").valueMs)
        assertNull(repository.delays.value["unknown"])
        assertTrue(client.savedValues("a").isEmpty())
    }

    @Test
    fun `a failed save loads the server value again`() = runTest {
        val client = FakeClient(mapOf("a" to withDelay(value = 100)), saveFails = true)
        val repository = SendspinDelayRepository(client, admin, settings, backgroundScope)
        repository.load(listOf("a"))
        runCurrent()

        repository.adjust("a", 10)
        runCurrent()

        assertEquals(100, repository.delays.value.getValue("a").valueMs)
        assertEquals(listOf(110), client.savedValues("a"))
    }

    @Test
    fun `the local player comes from the settings and asks the server nothing`() = runTest {
        val client = FakeClient(emptyMap())
        val repository = SendspinDelayRepository(client, admin, settings, backgroundScope)

        repository.load(listOf(LOCAL))
        repository.adjust(LOCAL, -10)
        runCurrent()

        assertEquals(PlayerDelay(-10, -2000..2000), repository.delays.value[LOCAL])
        assertEquals(-10, settings.sendspinStaticDelayMs.value)
        assertTrue(client.requests.isEmpty())
    }

    @Test
    fun `the entry of the preferred output protocol is loaded and saved under its full key`() = runTest {
        val client = FakeClient(mapOf("a" to withOutputs(preferred = "sendspin-x")))
        val repository = SendspinDelayRepository(client, admin, settings, backgroundScope)
        repository.load(listOf("a"))
        runCurrent()

        assertEquals(PlayerDelay(250, 0..5000), repository.delays.value["a"])
        repository.adjust("a", 10)
        runCurrent()

        assertEquals(listOf(260), client.savedValues("a", PROTOCOL_DELAY_KEY))
        assertTrue(client.savedValues("a").isEmpty())
    }

    @Test
    fun `the bare entry wins when the native output is preferred`() = runTest {
        val client = FakeClient(mapOf("a" to withOutputs(preferred = "native", nativeValue = 5)))
        val repository = SendspinDelayRepository(client, admin, settings, backgroundScope)

        repository.load(listOf("a"))
        runCurrent()

        assertEquals(PlayerDelay(5, 0..5000), repository.delays.value["a"])
    }

    private fun withDelay(value: Int?, default: Int = 0): String =
        """{"player_id":"a","values":{"sendspin_static_delay":${delayEntry(value, default)},
           "name":{"key":"name","type":"string","value":"Kitchen","default_value":null}}}"""

    /** A parent player with a native output and a Sendspin protocol player `sendspin-x`. */
    private fun withOutputs(preferred: String, nativeValue: Int? = null): String {
        val native = nativeValue?.let { """"sendspin_static_delay":${delayEntry(it, 0)},""" }.orEmpty()
        return """{"player_id":"a","values":{$native"$PROTOCOL_DELAY_KEY":${delayEntry(250, 0)},
           "preferred_output_protocol":{"key":"preferred_output_protocol","type":"string",
           "value":"$preferred","default_value":"native"}}}"""
    }

    private fun delayEntry(value: Int?, default: Int): String =
        """{"key":"sendspin_static_delay","type":"integer","value":${value ?: "null"},
           "default_value":$default,"range":[0,5000]}"""

    private class FakeClient(
        private val configs: Map<String, String>,
        private val saveFails: Boolean = false,
    ) : StubServiceClient() {
        val requests = mutableListOf<Request>()

        override suspend fun sendRequest(request: Request): Result<Answer> {
            requests += request
            val playerId = request.args?.get("player_id")?.jsonPrimitive?.content
            return when (request.command) {
                APICommands.CONFIG_PLAYERS_GET -> configs[playerId]
                    ?.let { Result.success(answer(it)) }
                    ?: Result.failure(IllegalStateException("No config for $playerId"))
                APICommands.CONFIG_PLAYERS_SAVE -> if (saveFails) {
                    Result.failure(IllegalStateException("Forbidden"))
                } else {
                    Result.success(answer(configs.getValue(playerId.orEmpty())))
                }
                else -> error("Unexpected command ${request.command}")
            }
        }

        fun savedValues(playerId: String, key: String = SendspinDelayRepository.KEY): List<Int> = requests
            .filter { it.command == APICommands.CONFIG_PLAYERS_SAVE }
            .filter { it.args?.get("player_id")?.jsonPrimitive?.content == playerId }
            .mapNotNull { request ->
                request.args?.get("values")?.jsonObject?.get(key)?.jsonPrimitive?.content?.toInt()
            }

        private fun answer(result: String) =
            Answer(buildJsonObject { put("result", myJson.parseToJsonElement(result)) })
    }

    private companion object {
        const val LOCAL = "local-player"
        const val PROTOCOL_DELAY_KEY = "sendspin-x||protocol||sendspin_static_delay"
        const val WITHOUT_DELAY =
            """{"player_id":"b","values":{"name":{"key":"name","type":"string","value":null,"default_value":null}}}"""
    }
}
