package io.music_assistant.client.ui.compose.party

import io.music_assistant.client.api.APICommands
import io.music_assistant.client.data.model.client.PartyInvite
import io.music_assistant.client.data.model.server.EventType
import io.music_assistant.client.data.model.server.GrantedScopes
import io.music_assistant.client.data.model.server.Scope
import io.music_assistant.client.data.model.server.events.ProvidersUpdatedEvent
import io.music_assistant.client.data.repository.FakeAiRadioClient
import io.music_assistant.client.data.repository.PartyRepository
import io.music_assistant.client.data.repository.ProviderRepository
import io.music_assistant.client.ui.compose.common.DataState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PartyViewModelTest {
    private val link = "http://ma.local:8095/?join=ABCDEFGHJKLM"

    private fun config(guestAccess: Boolean, name: String = "") = """{"values":{
        "party_name":{"key":"party_name","type":"string","label":"Party Name","value":"$name"},
        "party_duration":{"key":"party_duration","type":"integer","label":"Party Duration","default_value":8,"range":[1,168]},
        "enable_guest_access":{"key":"enable_guest_access","type":"boolean","label":"Guest access","value":$guestAccess}
    }}"""

    private val client = FakeAiRadioClient(
        mutableMapOf(
            APICommands.PROVIDERS to """[{"instance_id":"party","name":"Party","domain":"party","type":"plugin"}]""",
            APICommands.PARTY_URL to "\"$link\"",
            APICommands.PARTY_CONFIG to "{}",
            APICommands.CONFIG_PROVIDERS_GET to config(guestAccess = true),
            APICommands.CONFIG_PROVIDERS_SAVE to config(guestAccess = true),
        ),
    )

    private val scopes = MutableStateFlow(
        GrantedScopes(setOf(Scope.CONFIG_PROVIDERS_READ, Scope.CONFIG_PROVIDERS_WRITE)),
    )

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.openViewModel(): PartyViewModel {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val repository = PartyRepository(client, ProviderRepository(client, backgroundScope), scopes, backgroundScope)
        runCurrent()
        return PartyViewModel(repository).also {
            it.open()
            runCurrent()
        }
    }

    private suspend fun TestScope.providersUpdated() {
        client.eventFlow.subscriptionCount.first { it > 0 }
        client.eventFlow.emit(ProvidersUpdatedEvent(EventType.PROVIDERS_UPDATED))
        runCurrent()
    }

    private fun commandsSent() = client.sent.map { it.command }

    @Test
    fun openLoadsTheInviteAndTheSettings() = runTest {
        val state = openViewModel().state.value

        assertEquals(PartyInvite.Open(link, null, null), state.invite.dataOrNull)
        assertEquals("true", state.settings.dataOrNull?.guestAccess?.value)
    }

    @Test
    fun aUserWhoMayNotConfigureGetsTheInviteOnly() = runTest {
        scopes.value = GrantedScopes(setOf(Scope.CONFIG_PROVIDERS_READ))

        val state = openViewModel().state.value

        assertIs<DataState.Data<*>>(state.invite)
        assertIs<DataState.NoData<*>>(state.settings)
        assertFalse(APICommands.CONFIG_PROVIDERS_GET in commandsSent())
    }

    @Test
    fun aProviderChangeReloadsAndAFailedReloadKeepsTheLastAnswer() = runTest {
        val viewModel = openViewModel()
        client.results[APICommands.CONFIG_PROVIDERS_GET] = config(guestAccess = true, name = "Summer party")

        providersUpdated()
        assertEquals("Summer party", viewModel.state.value.settings.dataOrNull?.fields?.first()?.value)

        // The plugin is gone while the server reloads it.
        client.failures += listOf(APICommands.PARTY_URL, APICommands.CONFIG_PROVIDERS_GET)
        providersUpdated()

        assertEquals(PartyInvite.Open(link, null, null), viewModel.state.value.invite.dataOrNull)
        assertEquals("Summer party", viewModel.state.value.settings.dataOrNull?.fields?.first()?.value)
    }

    @Test
    fun turningGuestAccessOffSavesAtOnceAndReloadsTheInvite() = runTest {
        val viewModel = openViewModel()
        client.results[APICommands.CONFIG_PROVIDERS_SAVE] = config(guestAccess = false)
        client.results[APICommands.PARTY_URL] = "null"

        viewModel.setGuestAccess(false)
        runCurrent()

        val save = client.sent.last { it.command == APICommands.CONFIG_PROVIDERS_SAVE }
        assertEquals(JsonObject(mapOf("enable_guest_access" to JsonPrimitive(false))), save.args?.get("values"))
        assertEquals(PartyInvite.GuestAccessOff, viewModel.state.value.invite.dataOrNull)
        assertEquals("false", viewModel.state.value.settings.dataOrNull?.guestAccess?.value)
        assertFalse(viewModel.state.value.saving)
    }

    @Test
    fun startingThePartySavesTheSetupInTheSameSave() = runTest {
        client.results[APICommands.CONFIG_PROVIDERS_GET] = config(guestAccess = false)
        val viewModel = openViewModel()
        val name = viewModel.state.value.settings.dataOrNull?.fields?.first() ?: error("no fields")

        viewModel.edit(name, "Summer party")
        viewModel.setGuestAccess(true)
        runCurrent()

        val saves = client.sent.filter { it.command == APICommands.CONFIG_PROVIDERS_SAVE }
        assertEquals(1, saves.size)
        assertEquals(
            JsonObject(
                mapOf("party_name" to JsonPrimitive("Summer party"), "enable_guest_access" to JsonPrimitive(true)),
            ),
            saves.single().args?.get("values"),
        )
        assertTrue(viewModel.state.value.edits.isEmpty())
    }

    @Test
    fun anInvalidEditBlocksStartingTheParty() = runTest {
        client.results[APICommands.CONFIG_PROVIDERS_GET] = config(guestAccess = false)
        val viewModel = openViewModel()
        val duration = viewModel.state.value.settings.dataOrNull?.fields?.last() ?: error("no fields")

        viewModel.edit(duration, "0")
        viewModel.setGuestAccess(true)
        runCurrent()

        assertFalse(APICommands.CONFIG_PROVIDERS_SAVE in commandsSent())
    }

    @Test
    fun endingThePartyKeepsTheEdits() = runTest {
        val viewModel = openViewModel()
        val name = viewModel.state.value.settings.dataOrNull?.fields?.first() ?: error("no fields")
        client.results[APICommands.CONFIG_PROVIDERS_SAVE] = config(guestAccess = false)

        viewModel.edit(name, "Summer party")
        viewModel.setGuestAccess(false)
        runCurrent()

        val save = client.sent.last { it.command == APICommands.CONFIG_PROVIDERS_SAVE }
        assertEquals(JsonObject(mapOf("enable_guest_access" to JsonPrimitive(false))), save.args?.get("values"))
        assertEquals(mapOf("party_name" to "Summer party"), viewModel.state.value.edits)
    }

    @Test
    fun saveSendsOnlyTheChangedValuesAndClearsThem() = runTest {
        val viewModel = openViewModel()
        val (name, duration) = viewModel.state.value.settings.dataOrNull?.fields.orEmpty()

        viewModel.edit(name, "Summer party")
        viewModel.edit(duration, "8")
        viewModel.saveSettings()
        runCurrent()

        val save = client.sent.last { it.command == APICommands.CONFIG_PROVIDERS_SAVE }
        assertEquals(JsonObject(mapOf("party_name" to JsonPrimitive("Summer party"))), save.args?.get("values"))
        assertEquals(mapOf("party_duration" to "8"), viewModel.state.value.edits)
    }

    @Test
    fun anInvalidNumberBlocksTheSave() = runTest {
        val viewModel = openViewModel()
        val duration = viewModel.state.value.settings.dataOrNull?.fields?.last() ?: error("no fields")

        viewModel.edit(duration, "0")
        viewModel.saveSettings()
        runCurrent()

        assertNull(viewModel.state.value.pendingEdits)
        assertFalse(APICommands.CONFIG_PROVIDERS_SAVE in commandsSent())
    }

    @Test
    fun aFailedSaveKeepsTheEdits() = runTest {
        val viewModel = openViewModel()
        val name = viewModel.state.value.settings.dataOrNull?.fields?.first() ?: error("no fields")
        client.failures += APICommands.CONFIG_PROVIDERS_SAVE

        viewModel.edit(name, "Summer party")
        viewModel.saveSettings()
        runCurrent()

        assertTrue(viewModel.state.value.saveFailed)
        assertEquals(mapOf("party_name" to "Summer party"), viewModel.state.value.edits)
    }

    @Test
    fun closeDropsTheEditsAndStopsFollowingChanges() = runTest {
        val viewModel = openViewModel()
        val name = viewModel.state.value.settings.dataOrNull?.fields?.first() ?: error("no fields")
        viewModel.edit(name, "Summer party")

        viewModel.close()
        runCurrent()
        client.sent.clear()
        client.eventFlow.emit(ProvidersUpdatedEvent(EventType.PROVIDERS_UPDATED))
        runCurrent()

        val state = viewModel.state.value
        assertIs<DataState.NoData<*>>(state.invite)
        assertIs<DataState.NoData<*>>(state.settings)
        assertTrue(state.edits.isEmpty())
        assertFalse(APICommands.PARTY_URL in commandsSent())
    }
}
