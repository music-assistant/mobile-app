package io.music_assistant.client.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.settings.SettingsRepository
import io.music_assistant.client.utils.BackgroundUsageGuard
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

/**
 * Drives the single "background usage disabled" warning dialog (see App.kt).
 *
 * The OS restriction has no callback API and can only change while we are backgrounded (the user
 * flips it in system settings), so [restricted] re-samples it on every app foreground plus once at
 * creation. Sampling only at creation would miss an allowed→disallowed transition on a warm
 * relaunch, where the process (and this ViewModel) survive and no source would re-trigger the check.
 */
class BackgroundRestrictionViewModel(
    private val settings: SettingsRepository,
    private val guard: BackgroundUsageGuard,
    apiClient: ServiceClient,
) : ViewModel() {
    private val restricted: StateFlow<Boolean> =
        apiClient.foregroundEvents
            .onStart { emit(Unit) } // sample immediately, not only on the next foreground
            .map { guard.isBackgroundRestricted() }
            .stateIn(viewModelScope, SharingStarted.Eagerly, guard.isBackgroundRestricted())

    init {
        // Episode-scoped dismissal: once background usage is allowed again, forget a prior "don't
        // show again" so that a later re-restriction warns afresh.
        restricted
            .onEach { isRestricted ->
                if (!isRestricted && settings.bgWarningDismissed.value) {
                    settings.setBgWarningDismissed(false)
                }
            }
            .launchIn(viewModelScope)
    }

    val shouldWarn: StateFlow<Boolean> =
        combine(
            settings.sendspinEnabled,
            settings.bgWarningDismissed,
            restricted,
        ) { enabled, dismissed, isRestricted ->
            enabled && !dismissed && isRestricted
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /** Persist "don't show again" — suppresses the warning for the current restricted episode. */
    fun onDismiss() = settings.setBgWarningDismissed(true)

    /** Open system settings; deliberately does NOT dismiss, so an unfixed state reminds next launch. */
    fun onOpenSettings() = guard.openBackgroundUsageSettings()
}
