package io.music_assistant.client.data.repository

import io.music_assistant.client.api.APICommands
import io.music_assistant.client.data.model.client.Dependency
import io.music_assistant.client.data.model.client.PartyInvite
import io.music_assistant.client.data.model.client.PartySettingInput
import io.music_assistant.client.data.model.client.PartySettings
import io.music_assistant.client.data.model.server.EventType
import io.music_assistant.client.data.model.server.GrantedScopes
import io.music_assistant.client.data.model.server.Scope
import io.music_assistant.client.data.model.server.events.ProvidersUpdatedEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PartyRepositoryTest {
    private fun provider(domain: String, available: Boolean = true) =
        """{"instance_id":"$domain","name":"$domain","domain":"$domain","type":"plugin","available":$available}"""

    private val link = "http://ma.local:8095/?join=ABCDEFGHJKLM"

    private val providerConfig = """{"values":{
        "mode":{"key":"mode","type":"string","label":"Party Mode","default_value":"venue",
            "options":[{"value":"venue","title":"Venue"},{"value":"remote","title":"Remote"}]},
        "player":{"key":"player","type":"string","label":"Party Player","value":"kitchen",
            "depends_on":"mode","depends_on_value":"venue",
            "options":[{"value":"__auto__","title":"Auto"},{"value":"kitchen","title":"Kitchen"}]},
        "party_name":{"key":"party_name","type":"string","label":"Party Name","default_value":""},
        "qr_text":{"key":"qr_text","type":"string","label":"QR Code Text","depends_on":"enable_guest_access"},
        "party_duration":{"key":"party_duration","type":"integer","label":"Party Duration","default_value":8,"range":[1,168]},
        "enable_guest_access":{"key":"enable_guest_access","type":"boolean","label":"Guest access","value":true},
        "guest_enabled_note":{"key":"guest_enabled_note","type":"alert","label":"Guests can join"},
        "karaoke_mode":{"key":"karaoke_mode","type":"boolean","label":"Karaoke","default_value":false}
    }}"""

    private val client = FakeAiRadioClient(
        mutableMapOf(
            APICommands.PROVIDERS to "[${provider("party")}]",
            APICommands.PARTY_URL to "\"$link\"",
            APICommands.PARTY_CONFIG to """{"party_name":"Summer party","qr_text":"Add your songs","mode":"venue"}""",
            APICommands.CONFIG_PROVIDERS_GET to providerConfig,
            APICommands.CONFIG_PROVIDERS_SAVE to providerConfig,
        ),
    )

    private val scopes = MutableStateFlow(
        GrantedScopes(setOf(Scope.CONFIG_PROVIDERS_READ, Scope.CONFIG_PROVIDERS_WRITE)),
    )

    private fun PartySettings.field(key: String) = fields.first { it.key == key }

    private fun TestScope.repository(): PartyRepository {
        val providers = ProviderRepository(client, backgroundScope)
        return PartyRepository(client, providers, scopes, backgroundScope).also { runCurrent() }
    }

    @Test
    fun aLoadedPluginIsAvailable() = runTest {
        assertTrue(repository().available.value)
    }

    @Test
    fun anUnavailableOrMissingPluginIsNotAvailable() = runTest {
        client.results[APICommands.PROVIDERS] = "[${provider("party", available = false)},${provider("spotify")}]"

        assertFalse(repository().available.value)
    }

    @Test
    fun aFailedProviderListIsNotAvailable() = runTest {
        client.failures += APICommands.PROVIDERS

        assertFalse(repository().available.value)
    }

    @Test
    fun anInviteCarriesTheLinkTheNameAndTheText() = runTest {
        assertEquals(
            Result.success(PartyInvite.Open(link, "Summer party", "Add your songs")),
            repository().invite(),
        )
    }

    @Test
    fun aBlankNameOrTextIsNull() = runTest {
        client.results[APICommands.PARTY_CONFIG] = """{"party_name":"  ","qr_text":""}"""

        val invite = repository().invite().getOrNull() as PartyInvite.Open

        assertEquals(null, invite.partyName)
        assertEquals(null, invite.qrText)
    }

    @Test
    fun aNullLinkMeansGuestAccessIsOff() = runTest {
        client.results[APICommands.PARTY_URL] = "null"

        assertEquals(Result.success(PartyInvite.GuestAccessOff), repository().invite())
    }

    @Test
    fun aFailedConfigKeepsTheLink() = runTest {
        client.failures += APICommands.PARTY_CONFIG

        assertEquals(
            Result.success(PartyInvite.Open(link, null, null)),
            repository().invite(),
        )
    }

    @Test
    fun aFailedLinkFails() = runTest {
        client.failures += APICommands.PARTY_URL

        assertTrue(repository().invite().isFailure)
    }

    @Test
    fun onlyAUserWhoMayWriteProviderConfigsMayConfigure() = runTest {
        val repository = repository()
        assertTrue(repository.canConfigure.value)

        scopes.value = GrantedScopes(setOf(Scope.CONFIG_PROVIDERS_READ))
        runCurrent()

        assertFalse(repository.canConfigure.value)
    }

    @Test
    fun theSettingsAreTheCoreFieldsInOrderWithTheirInputs() = runTest {
        val settings = repository().settings().getOrThrow()

        assertEquals("true", settings.guestAccess.value)
        assertEquals(PartySettingInput.Switch, settings.guestAccess.input)
        assertEquals(
            listOf("mode", "player", "party_name", "qr_text", "party_duration", "karaoke_mode"),
            settings.fields.map { it.key },
        )
        val mode = settings.field("mode")
        val player = settings.field("player")
        val name = settings.field("party_name")
        val duration = settings.field("party_duration")
        assertEquals("venue", mode.value)
        assertEquals(listOf("Venue", "Remote"), (mode.input as PartySettingInput.Choice).options.map { it.title })
        assertEquals("kitchen", player.value)
        assertEquals(Dependency("mode", "venue"), player.dependsOn)
        assertEquals("", name.value)
        assertEquals(PartySettingInput.Number(1..168), duration.input)
        assertEquals("8", duration.value)
        assertEquals(PartySettingInput.Switch, settings.field("karaoke_mode").input)
        assertEquals("false", settings.field("karaoke_mode").value)
        assertEquals(listOf(APICommands.PROVIDERS, APICommands.CONFIG_PROVIDERS_GET), client.sent.map { it.command })
        assertEquals(JsonPrimitive("party"), client.sent.last().args?.get("instance_id"))
    }

    @Test
    fun aDependentFieldFollowsTheValueThatTheHostSees() = runTest {
        val settings = repository().settings().getOrThrow()
        val player = settings.field("player")
        val qrText = settings.field("qr_text")

        assertTrue(settings.isShown(player, emptyMap()))
        assertFalse(settings.isShown(player, mapOf("mode" to "remote")))
        assertTrue(settings.isShown(qrText, emptyMap()))
        assertFalse(settings.isShown(qrText, mapOf("enable_guest_access" to "false")))
    }

    @Test
    fun aSaveSendsTheEditsTyped() = runTest {
        val repository = repository()
        val settings = repository.settings().getOrThrow()
        val name = settings.field("party_name")
        val duration = settings.field("party_duration")

        assertTrue(
            repository.save(mapOf(name to "Summer party", duration to "12", settings.guestAccess to "false")).isSuccess,
        )

        val save = client.sent.last()
        assertEquals(APICommands.CONFIG_PROVIDERS_SAVE, save.command)
        assertEquals(JsonPrimitive("party"), save.args?.get("provider_domain"))
        assertEquals(JsonPrimitive("party"), save.args?.get("instance_id"))
        assertEquals(
            JsonObject(
                mapOf(
                    "party_name" to JsonPrimitive("Summer party"),
                    "party_duration" to JsonPrimitive(12),
                    "enable_guest_access" to JsonPrimitive(false),
                ),
            ),
            save.args?.get("values"),
        )
    }

    @Test
    fun aNumberOutOfRangeIsNotSent() = runTest {
        val repository = repository()
        val duration = repository.settings().getOrThrow().field("party_duration")

        assertTrue(repository.save(mapOf(duration to "500")).isFailure)
        assertEquals(APICommands.CONFIG_PROVIDERS_GET, client.sent.last().command)
    }

    @Test
    fun noLoadedPluginMeansNoSettings() = runTest {
        client.results[APICommands.PROVIDERS] = "[]"

        assertTrue(repository().settings().isFailure)
        assertFalse(client.sent.any { it.command == APICommands.CONFIG_PROVIDERS_GET })
    }

    @Test
    fun aProviderChangeIsPassedOn() = runTest {
        val repository = repository()
        var changes = 0
        backgroundScope.launch { repository.changes.collect { changes++ } }
        runCurrent()

        client.eventFlow.subscriptionCount.first { it > 0 }
        client.eventFlow.emit(ProvidersUpdatedEvent(EventType.PROVIDERS_UPDATED))
        runCurrent()

        assertEquals(1, changes)
    }
}
