package io.music_assistant.client.ui.compose.home.players

import io.music_assistant.client.api.APICommands
import io.music_assistant.client.data.factory.MediaItemFactory
import io.music_assistant.client.data.repository.AiRadioRepository
import io.music_assistant.client.data.repository.FakeAiRadioClient
import io.music_assistant.client.data.repository.ServiceClientMediaItemRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class QueueDjViewModelTest {
    private val client = FakeAiRadioClient(
        mutableMapOf(
            APICommands.AI_RADIO_HOSTS_LIST to """[{"id":"h1","name":"Ann"},{"id":"h2","name":"Bob"}]""",
            APICommands.AI_RADIO_QUEUE_DJ_STATUS to """{"q1":"h1"}""",
            APICommands.AI_RADIO_STATUS to """{"sessions":[]}""",
            APICommands.AI_RADIO_QUEUE_DJ_SET to """{"q1":"h2"}""",
        ),
    )
    private val available = MutableStateFlow(true)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(): QueueDjViewModel {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val repository = AiRadioRepository(
            client,
            ServiceClientMediaItemRepository(client, MediaItemFactory(client)),
            backgroundScope,
        )
        return QueueDjViewModel(repository, available).also { runCurrent() }
    }

    private fun commandsSent() = client.sent.map { it.command }

    @Test
    fun aHintRefetchesOnlyThePartItNames() = runTest {
        val viewModel = viewModel()
        assertEquals(
            setOf(APICommands.AI_RADIO_HOSTS_LIST, APICommands.AI_RADIO_QUEUE_DJ_STATUS, APICommands.AI_RADIO_STATUS),
            commandsSent().toSet(),
        )
        client.sent.clear()
        client.results[APICommands.AI_RADIO_QUEUE_DJ_STATUS] = """{"q1":"h2"}"""

        client.emitHint("queue_dj_updated")
        runCurrent()

        assertEquals(listOf(APICommands.AI_RADIO_QUEUE_DJ_STATUS), commandsSent())
        assertEquals("h2", viewModel.state.value?.assignments?.get("q1"))
    }

    @Test
    fun aClosedGateIgnoresHintsAndHidesTheRow() = runTest {
        available.value = false
        val viewModel = viewModel()

        client.emitHint("hosts_updated")
        client.emitHint("queue_dj_updated")
        runCurrent()

        assertEquals(emptyList(), commandsSent())
        assertNull(viewModel.state.value)
    }

    @Test
    fun aRefetchThatStartedBeforeASetCannotRestoreTheOldMap() = runTest {
        val viewModel = viewModel()
        val hold = CompletableDeferred<Unit>()
        client.holds[APICommands.AI_RADIO_QUEUE_DJ_STATUS] = hold

        client.emitHint("queue_dj_updated") // Starts a refetch that answers the old map.
        runCurrent()
        viewModel.select("q1", "h2")
        runCurrent()
        hold.complete(Unit)
        runCurrent()

        assertEquals("h2", viewModel.state.value?.assignments?.get("q1"))
    }
}
