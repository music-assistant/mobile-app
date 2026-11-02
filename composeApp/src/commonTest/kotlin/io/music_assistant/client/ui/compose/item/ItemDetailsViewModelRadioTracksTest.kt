package io.music_assistant.client.ui.compose.item

import com.russhwolf.settings.MapSettings
import io.music_assistant.client.api.APICommands
import io.music_assistant.client.data.FakeItemDetailsAuthCoordinator
import io.music_assistant.client.data.FakeItemDetailsMainDataSource
import io.music_assistant.client.data.FakeItemDetailsServiceClient
import io.music_assistant.client.data.RecordingItemDetailsMediaItemRepository
import io.music_assistant.client.data.model.client.MediaType
import io.music_assistant.client.data.model.client.items.RadioStation
import io.music_assistant.client.data.model.client.testRadio
import io.music_assistant.client.data.play.PlayDispatcher
import io.music_assistant.client.data.repository.ItemRefresher
import io.music_assistant.client.data.repository.ProviderRepository
import io.music_assistant.client.data.repository.TranscriptRepository
import io.music_assistant.client.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

@OptIn(ExperimentalCoroutinesApi::class)
class ItemDetailsViewModelRadioTracksTest {
    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.createViewModel(
        station: RadioStation,
        repository: RecordingItemDetailsMediaItemRepository,
    ): ItemDetailsViewModel {
        val apiClient = FakeItemDetailsServiceClient()

        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))

        return ItemDetailsViewModel(
            playDispatcher = PlayDispatcher(apiClient, backgroundScope),
            mainDataSource = FakeItemDetailsMainDataSource(),
            settingsRepository = SettingsRepository(MapSettings(), MapSettings()),
            mediaItemRepository = repository,
            providerRepository = ProviderRepository(apiClient, backgroundScope),
            itemRefresher = ItemRefresher(repository, backgroundScope),
            authCoordinator = FakeItemDetailsAuthCoordinator(),
            transcriptRepository = TranscriptRepository(repository, apiClient, backgroundScope),
            itemId = station.itemId,
            mediaType = MediaType.RADIO,
            providerId = station.provider,
        )
    }

    private fun radioTrackRequests(
        repository: RecordingItemDetailsMediaItemRepository,
    ) = repository.requests.filter {
        it.command == APICommands.MUSIC_RADIOS_RADIO_TRACKS
    }

    @Test
    fun finiteNonDynamicRadioLoadsItsTracklist() = runTest {
        val station = testRadio(isEndless = false, isDynamic = false)
        val repository = RecordingItemDetailsMediaItemRepository(station)

        createViewModel(station, repository)
        runCurrent()

        assertContains(
            radioTrackRequests(repository).map { it.command },
            APICommands.MUSIC_RADIOS_RADIO_TRACKS,
        )
    }

    @Test
    fun endlessRadioDoesNotLoadItsTracklist() = runTest {
        val station = testRadio(isEndless = true, isDynamic = false)
        val repository = RecordingItemDetailsMediaItemRepository(station)

        createViewModel(station, repository)
        runCurrent()

        assertFalse(radioTrackRequests(repository).isNotEmpty())
    }

    @Test
    fun dynamicRadioDoesNotLoadItsTracklist() = runTest {
        val station = testRadio(isEndless = false, isDynamic = true)
        val repository = RecordingItemDetailsMediaItemRepository(station)

        createViewModel(station, repository)
        runCurrent()

        assertFalse(radioTrackRequests(repository).isNotEmpty())
    }
}
