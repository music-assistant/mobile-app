package io.music_assistant.client.ui.compose.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.music_assistant.client.settings.SendspinConnectionSettings
import io.music_assistant.client.settings.SendspinPlayerSettings
import io.music_assistant.client.settings.SettingsRepository
import io.music_assistant.sendspin.api.SourceCodec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Local player settings with a draft. Edits stay in the draft until a save or a reset applies
 * them, whether the player is on or off. Applying them restarts a running player, so a save or a
 * reset asks for a confirmation first if the player is playing.
 *
 * The device audio share codec is not in the draft: it applies at once, and only to the next share.
 */
class SendspinSettingsViewModel(
    private val settings: SettingsRepository,
    /** False on a platform that cannot share the device audio; the screen then hides the share codec. */
    val isDeviceAudioShareSupported: Boolean = false,
    private val isLocalPlayerPlaying: () -> Boolean,
) : ViewModel() {
    val enabled: StateFlow<Boolean> = settings.sendspinEnabled

    val deviceAudioShareCodec: StateFlow<SourceCodec> = settings.deviceAudioShareCodec

    private val _draft = MutableStateFlow(settings.sendspinPlayerSettings.value)
    val draft: StateFlow<SendspinPlayerSettings> = _draft.asStateFlow()

    val isSavable: StateFlow<Boolean> = combine(_draft, settings.sendspinPlayerSettings) { draft, saved ->
        draft.effective() != saved.effective()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val isResettable: StateFlow<Boolean> = _draft
        .map { it.effective() != SendspinPlayerSettings.defaults }
        .stateIn(viewModelScope, SharingStarted.Eagerly, _draft.value.effective() != SendspinPlayerSettings.defaults)

    private val _pendingApply = MutableStateFlow<SendspinPlayerSettings?>(null)

    /** True while the "interrupting playback" confirmation waits for an answer. */
    val isConfirmationPending: StateFlow<Boolean> = _pendingApply
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun setEnabled(enabled: Boolean) = settings.setSendspinEnabled(enabled)

    fun setDeviceAudioShareCodec(codec: SourceCodec) = settings.setDeviceAudioShareCodec(codec)

    fun update(transform: (SendspinPlayerSettings) -> SendspinPlayerSettings) {
        _draft.value = transform(_draft.value)
    }

    fun save() = request(_draft.value)

    fun reset() = request(SendspinPlayerSettings.defaults)

    fun confirm() {
        _pendingApply.value?.let(::apply)
        _pendingApply.value = null
    }

    /** Keeps the draft, so the user can still save it later. */
    fun dismiss() {
        _pendingApply.value = null
    }

    private fun request(target: SendspinPlayerSettings) {
        when {
            enabled.value && isLocalPlayerPlaying() -> _pendingApply.value = target
            else -> apply(target)
        }
    }

    private fun apply(target: SendspinPlayerSettings) {
        _draft.value = target
        settings.setSendspinPlayerSettings(target)
    }
}

/** Projection that ignores the override fields while the override is off: they have no effect then. */
private fun SendspinPlayerSettings.effective(): SendspinPlayerSettings = when {
    connectionOverride.enabled -> this
    else -> copy(connectionOverride = SendspinConnectionSettings())
}
