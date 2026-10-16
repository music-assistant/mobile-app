package io.music_assistant.client.ui.compose.settings

import com.russhwolf.settings.MapSettings
import io.music_assistant.client.settings.SendspinPlayerSettings
import io.music_assistant.client.settings.SettingsRepository
import io.music_assistant.sendspin.api.AudioCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SendspinSettingsViewModelTest {
    private val store = MapSettings()
    private val secrets = MapSettings()
    private val repository = SettingsRepository(store, secrets)
    private var playing = false

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(enabled: Boolean = true) =
        SendspinSettingsViewModel(repository) { playing }.also { it.setEnabled(enabled) }

    private fun SendspinSettingsViewModel.rename(name: String) = update { it.copy(name = name) }

    private fun SendspinSettingsViewModel.setCustomConnection(enabled: Boolean) =
        update { it.copy(connectionOverride = it.connectionOverride.copy(enabled = enabled)) }

    @Test
    fun freshInstallMatchesDefaults() {
        val viewModel = viewModel()

        assertEquals(SendspinPlayerSettings.defaults, repository.sendspinPlayerSettings.value)
        assertFalse(viewModel.isSavable.value)
        assertFalse(viewModel.isResettable.value)
    }

    @Test
    fun editWhilePlayerOffIsSavedAtOnce() {
        val viewModel = viewModel(enabled = false)

        viewModel.rename("Kitchen")

        assertEquals("Kitchen", repository.sendspinPlayerSettings.value.name)
        assertFalse(viewModel.isSavable.value)
        assertTrue(viewModel.isResettable.value)
    }

    @Test
    fun editWhilePlayerOnStaysInDraft() {
        val viewModel = viewModel()

        viewModel.rename("Kitchen")

        assertEquals(SettingsRepository.DEVICE_NAME, repository.sendspinPlayerSettings.value.name)
        assertTrue(viewModel.isSavable.value)
    }

    @Test
    fun revertedEditDisablesSave() {
        val viewModel = viewModel()

        viewModel.update { it.copy(codecPreference = AudioCodec.FLAC) }
        viewModel.update { it.copy(codecPreference = SettingsRepository.DEFAULT_CODEC) }

        assertFalse(viewModel.isSavable.value)
    }

    @Test
    fun customConnectionToggledOnAndOffDisablesSave() {
        val viewModel = viewModel()

        viewModel.setCustomConnection(true)
        assertTrue(viewModel.isSavable.value)
        viewModel.update { it.copy(connectionOverride = it.connectionOverride.copy(host = "10.0.0.2")) }
        viewModel.setCustomConnection(false)

        assertFalse(viewModel.isSavable.value)
        assertFalse(viewModel.isResettable.value)
    }

    @Test
    fun saveWhileNotPlayingAppliesWithoutConfirmation() {
        val viewModel = viewModel()
        viewModel.rename("Kitchen")

        viewModel.save()

        assertFalse(viewModel.isConfirmationPending.value)
        assertEquals("Kitchen", repository.sendspinPlayerSettings.value.name)
        assertFalse(viewModel.isSavable.value)
    }

    @Test
    fun saveWhilePlayingAppliesOnlyAfterConfirmation() {
        playing = true
        val viewModel = viewModel()
        viewModel.rename("Kitchen")

        viewModel.save()

        assertTrue(viewModel.isConfirmationPending.value)
        assertEquals(SettingsRepository.DEVICE_NAME, repository.sendspinPlayerSettings.value.name)

        viewModel.confirm()

        assertFalse(viewModel.isConfirmationPending.value)
        assertEquals("Kitchen", repository.sendspinPlayerSettings.value.name)
    }

    @Test
    fun dismissedConfirmationKeepsDraftAndSavedSettings() {
        playing = true
        val viewModel = viewModel()
        viewModel.rename("Kitchen")

        viewModel.save()
        viewModel.dismiss()

        assertFalse(viewModel.isConfirmationPending.value)
        assertEquals("Kitchen", viewModel.draft.value.name)
        assertEquals(SettingsRepository.DEVICE_NAME, repository.sendspinPlayerSettings.value.name)
        assertTrue(viewModel.isSavable.value)
    }

    @Test
    fun resetWhilePlayerOffIsSavedAtOnce() {
        val viewModel = viewModel(enabled = false)
        viewModel.rename("Kitchen")

        viewModel.reset()

        assertFalse(viewModel.isConfirmationPending.value)
        assertEquals(SendspinPlayerSettings.defaults, repository.sendspinPlayerSettings.value)
        assertFalse(viewModel.isSavable.value)
        assertFalse(viewModel.isResettable.value)
    }

    @Test
    fun resetWhilePlayingAppliesDefaultsAfterConfirmation() {
        repository.setSendspinPlayerSettings(SendspinPlayerSettings(name = "Kitchen", bufferCapacityMb = 30))
        playing = true
        val viewModel = viewModel()

        viewModel.reset()

        assertTrue(viewModel.isConfirmationPending.value)
        assertEquals("Kitchen", repository.sendspinPlayerSettings.value.name)

        viewModel.confirm()

        assertEquals(SendspinPlayerSettings.defaults, repository.sendspinPlayerSettings.value)
        assertEquals(SendspinPlayerSettings.defaults, viewModel.draft.value)
    }

    @Test
    fun turningPlayerOffAppliesTheDraft() {
        val viewModel = viewModel()
        viewModel.rename("Kitchen")

        viewModel.setEnabled(false)

        assertEquals("Kitchen", repository.sendspinPlayerSettings.value.name)
        assertFalse(viewModel.isSavable.value)
    }

    @Test
    fun savedSettingsSurviveRestart() {
        val saved = SendspinPlayerSettings(
            name = "Kitchen",
            bufferCapacityMb = 30,
            codecPreference = AudioCodec.PCM,
        ).let { it.copy(connectionOverride = it.connectionOverride.copy(enabled = true, tls = true, port = 9000)) }
        repository.setSendspinPlayerSettings(saved)

        assertEquals(saved, SettingsRepository(store, secrets).sendspinPlayerSettings.value)
    }

    @Test
    fun defaultsSurviveRestartAsNotResettable() {
        viewModel(enabled = false).apply {
            rename("Kitchen")
            reset()
        }

        val restarted = SendspinSettingsViewModel(SettingsRepository(store, secrets)) { false }

        assertFalse(restarted.isResettable.value)
    }
}
