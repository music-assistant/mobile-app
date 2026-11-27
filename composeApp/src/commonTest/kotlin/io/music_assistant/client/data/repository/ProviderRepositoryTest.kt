package io.music_assistant.client.data.repository

import io.music_assistant.client.api.APICommands
import io.music_assistant.client.api.ConnectionInfo
import io.music_assistant.client.data.model.server.EventType
import io.music_assistant.client.data.model.server.ServerProviderInstance
import io.music_assistant.client.data.model.server.User
import io.music_assistant.client.data.model.server.events.ProvidersUpdatedEvent
import io.music_assistant.client.ui.compose.common.DataState
import io.music_assistant.client.utils.ConnectionData
import io.music_assistant.client.utils.SessionState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ProviderRepositoryTest {
    private fun providersJson(vararg ids: String) =
        ids.joinToString(prefix = "[", postfix = "]") {
            """{"instance_id":"$it","name":"$it","domain":"$it","type":"music"}"""
        }

    private val client = FakeAiRadioClient(mutableMapOf(APICommands.PROVIDERS to providersJson("a")))

    private fun TestScope.repository() = ProviderRepository(client, backgroundScope).also { runCurrent() }

    private fun ProviderRepository.ids() = providers.value.dataOrNull?.map { it.instanceId }

    private suspend fun TestScope.providersUpdated() {
        client.eventFlow.subscriptionCount.first { it > 0 }
        client.eventFlow.emit(ProvidersUpdatedEvent(EventType.PROVIDERS_UPDATED))
        runCurrent()
    }

    @Test
    fun aReadySessionLoadsTheListOnce() = runTest {
        val repository = repository()

        assertEquals(listOf("a"), repository.ids())
        assertEquals(listOf(APICommands.PROVIDERS), client.sent.map { it.command })
    }

    @Test
    fun providersUpdatedLoadsTheListAgain() = runTest {
        val repository = repository()
        client.results[APICommands.PROVIDERS] = providersJson("a", "b")

        providersUpdated()

        assertEquals(listOf("a", "b"), repository.ids())
        assertEquals(2, client.sent.size)
    }

    @Test
    fun aFailedRefetchKeepsTheLastList() = runTest {
        val repository = repository()
        client.results[APICommands.PROVIDERS] = null

        providersUpdated()

        assertEquals(listOf("a"), repository.ids())
    }

    @Test
    fun aFailedFirstFetchIsAnError() = runTest {
        client.results[APICommands.PROVIDERS] = null

        assertIs<DataState.Error<*>>(repository().providers.value)
    }

    @Test
    fun noSessionMeansNoDataAndNoRequest() = runTest {
        client.ready.value = false
        val repository = repository()

        assertIs<DataState.NoData<*>>(repository.providers.value)
        assertTrue(client.sent.isEmpty())

        client.ready.value = true
        runCurrent()

        assertEquals(listOf("a"), repository.ids())
    }

    @Test
    fun aNewSessionDropsTheOldListAndLoadsAgain() = runTest {
        val repository = repository()
        client.results[APICommands.PROVIDERS] = providersJson("b")

        client.ready.value = false
        runCurrent()
        assertIs<DataState.NoData<*>>(repository.providers.value)

        client.ready.value = true
        runCurrent()
        assertEquals(listOf("b"), repository.ids())
    }

    @Test
    fun anEmptyProviderFilterShowsEveryInstance() {
        val providers = listOf(
            ServerProviderInstance("base", "Base", "ytmusic"),
            ServerProviderInstance("allowed-a", "Allowed A", "ytmusic"),
            ServerProviderInstance("allowed-b", "Allowed B", "ytmusic"),
        )

        assertEquals(providers, filterVisibleProviders(providers, emptyList()))
    }

    @Test
    fun aProviderFilterKeepsOnlyAllowedInstances() {
        val providers = listOf(
            ServerProviderInstance("base", "Base", "ytmusic"),
            ServerProviderInstance("allowed-a", "Allowed A", "ytmusic"),
            ServerProviderInstance("allowed-b", "Allowed B", "ytmusic"),
        )

        assertEquals(
            listOf(providers[1]),
            filterVisibleProviders(providers, listOf("allowed-a")),
        )
    }

    @Test
    fun providersAreFilteredByTheActiveSessionUser() = runTest {
        client.results[APICommands.PROVIDERS] = providersJson("base", "allowed-a", "allowed-b")
        val repository = repository()

        client.sessionState.value = SessionState.Connected.Direct(
            connectionInfo = ConnectionInfo(host = "localhost", port = 8095, isTls = false),
            connectionData = ConnectionData(
                user = User(providerFilter = listOf("allowed-a")),
            ),
        )
        runCurrent()

        assertEquals(listOf("allowed-a"), repository.ids())
    }
}
