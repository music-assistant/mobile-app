package io.music_assistant.client.ui.compose.home.players

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.music_assistant.client.data.MainDataSource
import io.music_assistant.client.data.model.server.DspConfig
import io.music_assistant.client.data.model.server.DspConfigPreset
import io.music_assistant.client.data.model.server.supportsDspApplyPreset
import io.music_assistant.client.utils.HasConnectionData
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Resolves the server-reported active preset id to the (id, name) key shown as a checkmark. */
internal fun appliedDspPresetKey(
    config: DspConfig,
    presets: List<DspConfigPreset>,
): Pair<String?, String>? = config.presetId?.let { presetId ->
    presets.firstOrNull { it.presetId == presetId }?.let { it.presetId to it.name }
}

class DspSettingsViewModel(
    private val dataSource: MainDataSource,
) : ViewModel() {
    sealed class DspDialogState {
        data object Loading : DspDialogState()
        data class Content(
            val config: DspConfig,
            val presets: List<DspConfigPreset>,
            val appliedPresetKey: Pair<String?, String>? = null,
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
                    val uniquePresets = presets.distinctBy { it.presetId to it.name }
                    _state.value = DspDialogState.Content(
                        config = config,
                        presets = uniquePresets,
                        // Servers with apply_preset support report the active preset id in the
                        // config; older servers never set it, so this is simply null there.
                        appliedPresetKey = appliedDspPresetKey(config, uniquePresets),
                    )
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
        // A manual save clears the active preset server-side, so drop the checkmark too.
        val newConfig = current.config.copy(enabled = !current.config.enabled, presetId = null)
        _state.update { current.copy(config = newConfig, appliedPresetKey = null) }
        viewModelScope.launch {
            val saved = dataSource.saveDspConfig(playerId, newConfig)
            if (saved == null) {
                _state.update { current }
            }
        }
    }

    fun applyPreset(playerId: String, preset: DspConfigPreset) {
        val current = (_state.value as? DspDialogState.Content) ?: return
        val presetId = preset.presetId
        val useApplyPreset = presetId != null && supportsDspApplyPreset(schemaVersion())
        viewModelScope.launch {
            val saved = if (useApplyPreset && presetId != null) {
                dataSource.applyDspPreset(playerId, presetId)
            } else {
                dataSource.saveDspConfig(playerId, preset.config.copy(enabled = true))
            }
            if (saved != null) {
                _state.value = current.copy(
                    config = saved,
                    appliedPresetKey = if (useApplyPreset) {
                        appliedDspPresetKey(saved, current.presets)
                    } else {
                        presetId to preset.name
                    },
                )
                if (!useApplyPreset) {
                    // Legacy servers can't report the active preset; show a transient checkmark.
                    delay(1000)
                    _state.update { state ->
                        (state as? DspDialogState.Content)?.copy(appliedPresetKey = null) ?: state
                    }
                }
            }
        }
    }

    private fun schemaVersion(): Int? =
        (dataSource.apiClient.sessionState.value as? HasConnectionData)?.serverInfo?.schemaVersion
}
