package io.music_assistant.client.data.repository

import app.cash.turbine.test
import io.music_assistant.client.data.factory.MediaItemFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Pins how [AiRadioRepository.changes] turns server hints and reconnects into refetches. */
class AiRadioChangesTest {
    private fun repository(client: FakeAiRadioClient, scope: CoroutineScope) =
        AiRadioRepository(client, ServiceClientMediaItemRepository(client, MediaItemFactory(client)), scope)

    @Test
    fun hintsMapToTheEndpointTheyName() = runTest {
        val client = FakeAiRadioClient(mutableMapOf())
        repository(client, backgroundScope).changes.test {
            client.emitHint("hosts_updated")
            client.emitHint("stations_updated")
            client.emitHint("queue_dj_updated")
            client.emitHint("sessions_updated")

            assertEquals(AiRadioChange.HOSTS, awaitItem())
            assertEquals(AiRadioChange.STATIONS, awaitItem())
            assertEquals(AiRadioChange.QUEUE_DJ, awaitItem())
            assertEquals(AiRadioChange.SESSIONS, awaitItem())
        }
    }

    @Test
    fun otherProvidersAndUnshownKindsAreDropped() = runTest {
        val client = FakeAiRadioClient(mutableMapOf())
        repository(client, backgroundScope).changes.test {
            client.emitHint("queue_dj_updated", objectId = "smart_playlist")
            client.emitHint("sections_updated")
            client.emitHint("something_new")
            client.emitHint("hosts_updated")

            assertEquals(AiRadioChange.HOSTS, awaitItem())
        }
    }

    @Test
    fun onlyAReturnOfTheSessionResyncs() = runTest {
        val client = FakeAiRadioClient(mutableMapOf())
        repository(client, backgroundScope).changes.test {
            // Subscribing while connected is not a return: the first item must be the hint.
            client.emitHint("hosts_updated")
            assertEquals(AiRadioChange.HOSTS, awaitItem())

            // The flow is conflated, so each value must be delivered before the next is set.
            client.ready.value = false
            runCurrent()
            client.ready.value = true
            assertEquals(AiRadioChange.RESYNC, awaitItem())
        }
    }
}
