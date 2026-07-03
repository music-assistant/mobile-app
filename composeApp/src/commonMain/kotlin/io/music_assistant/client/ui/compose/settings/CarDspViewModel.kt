package io.music_assistant.client.ui.compose.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.music_assistant.client.data.MainDataSource
import io.music_assistant.client.data.model.server.DspConfigPreset
import io.music_assistant.client.settings.CarDspAction
import io.music_assistant.client.settings.SettingsRepository
import io.music_assistant.client.settings.matches
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Backs the Settings → Car → DSP presets dialog: loads the server preset list, exposes the stored
 * connect/disconnect actions, and persists changes immediately. On load, an action pointing at a
 * preset the server no longer has is reset to [CarDspAction.Nothing] (persisted) and flagged so the
 * dialog can warn the user.
 */
class CarDspViewModel(
    private val settings: SettingsRepository,
    private val dataSource: MainDataSource,
) : ViewModel() {
    sealed interface State {
        data object Loading : State
        data object Error : State
        data class Content(
            val presets: List<DspConfigPreset>,
            val connect: CarDspAction,
            val disconnect: CarDspAction,
            val connectReset: Boolean,
            val disconnectReset: Boolean,
        ) : State
    }

    private val _state = MutableStateFlow<State>(State.Loading)
    val state = _state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _state.value = State.Loading
            val presets = runCatching { dataSource.getDspPresets() }.getOrNull()
            if (presets == null) {
                _state.value = State.Error
                return@launch
            }
            val unique = presets.distinctBy { it.presetId to it.name }
            val (connect, connectReset) = reconcile(unique, settings.carDspConnectAction.value) {
                settings.setCarDspConnectAction(it)
            }
            val (disconnect, disconnectReset) = reconcile(unique, settings.carDspDisconnectAction.value) {
                settings.setCarDspDisconnectAction(it)
            }
            _state.value = State.Content(unique, connect, disconnect, connectReset, disconnectReset)
        }
    }

    /** Returns the effective action plus whether a dangling preset was reset to Nothing. */
    private fun reconcile(
        presets: List<DspConfigPreset>,
        stored: CarDspAction,
        persist: (CarDspAction) -> Unit,
    ): Pair<CarDspAction, Boolean> {
        if (stored is CarDspAction.Preset && presets.none { it.matches(stored) }) {
            persist(CarDspAction.Nothing)
            return CarDspAction.Nothing to true
        }
        return stored to false
    }

    fun setConnect(action: CarDspAction) {
        settings.setCarDspConnectAction(action)
        _state.update { it.copy(connect = action, connectReset = false) }
    }

    fun setDisconnect(action: CarDspAction) {
        settings.setCarDspDisconnectAction(action)
        _state.update { it.copy(disconnect = action, disconnectReset = false) }
    }

    private inline fun MutableStateFlow<State>.update(block: (State.Content) -> State.Content) {
        val current = value as? State.Content ?: return
        value = block(current)
    }
}
