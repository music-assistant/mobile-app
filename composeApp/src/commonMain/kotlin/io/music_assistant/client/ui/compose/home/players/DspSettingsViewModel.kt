package io.music_assistant.client.ui.compose.home.players

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.music_assistant.client.data.MainDataSource
import io.music_assistant.client.data.model.server.DspConfig
import io.music_assistant.client.data.model.server.DspConfigPreset
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DspSettingsViewModel(
    private val dataSource: MainDataSource,
) : ViewModel() {
    sealed class DspDialogState {
        data object Loading : DspDialogState()
        data class Content(
            val config: DspConfig,
            val presets: List<DspConfigPreset>,
            val appliedPresetId: String? = null,
        ) : DspDialogState()

        data object Error : DspDialogState()
    }

    private val _state = MutableStateFlow<DspDialogState>(DspDialogState.Loading)
    val state = _state.asStateFlow()

    fun load(playerId: String) {
        viewModelScope.launch {
            _state.value = DspDialogState.Loading
            try {
                val (config, presets) = coroutineScope {
                    val configDeferred = async { dataSource.getDspConfig(playerId) }
                    val presetsDeferred = async { dataSource.getDspPresets() }
                    configDeferred.await() to presetsDeferred.await()
                }
                if (config != null) {
                    _state.value = DspDialogState.Content(config = config, presets = presets)
                } else {
                    _state.value = DspDialogState.Error
                }
            } catch (_: Exception) {
                _state.value = DspDialogState.Error
            }
        }
    }

    fun toggleEnabled(playerId: String) {
        val current = (_state.value as? DspDialogState.Content) ?: return
        val newConfig = current.config.copy(enabled = !current.config.enabled)
        _state.update { current.copy(config = newConfig) }
        viewModelScope.launch {
            val saved = dataSource.saveDspConfig(playerId, newConfig)
            if (saved == null) {
                _state.update { current }
            }
        }
    }

    fun applyPreset(playerId: String, preset: DspConfigPreset) {
        val current = (_state.value as? DspDialogState.Content) ?: return
        val configToSave = preset.config.copy(enabled = true)
        viewModelScope.launch {
            val saved = dataSource.saveDspConfig(playerId, configToSave)
            if (saved != null) {
                _state.value = current.copy(config = saved, appliedPresetId = preset.presetId ?: preset.name)
                delay(1000)
                _state.update { state ->
                    (state as? DspDialogState.Content)?.copy(appliedPresetId = null) ?: state
                }
            }
        }
    }
}
