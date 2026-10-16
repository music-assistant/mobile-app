package io.music_assistant.client.ui.compose.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.music_assistant.client.settings.SendspinPlayerSettings
import io.music_assistant.client.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Local player settings with a draft. While the player is off, every edit is saved at once.
 * While it is on, edits stay in the draft until a save or a reset applies them, after a
 * confirmation if the player is playing, because applying them restarts the player.
 */
class SendspinSettingsViewModel(
    private val settings: SettingsRepository,
    private val isLocalPlayerPlaying: () -> Boolean,
) : ViewModel() {
    val enabled: StateFlow<Boolean> = settings.sendspinEnabled

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

    fun setEnabled(enabled: Boolean) {
        settings.setSendspinEnabled(enabled)
        // A stopped player has nothing to interrupt, so the draft is applied with it.
        if (!enabled) apply(_draft.value)
    }

    fun update(transform: (SendspinPlayerSettings) -> SendspinPlayerSettings) {
        _draft.value = transform(_draft.value)
        if (!enabled.value) apply(_draft.value)
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
