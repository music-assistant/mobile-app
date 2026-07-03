package io.music_assistant.client.data

import kotlinx.coroutines.flow.StateFlow

/**
 * Reliable, platform-provided "phone is connected to a car" signal, used to trigger the car DSP
 * action ([CarDspApplier]).
 *
 * Deliberately NOT sourced from the Android MediaBrowserService lifecycle: `onExternalConsumerActive`
 * fires at most once per service instance and `onExternalConsumerInactive` only in `onDestroy()`,
 * neither of which is a dependable connect/disconnect edge. Android backs this with androidx
 * `CarConnection` (car-host content provider, lifecycle-independent); iOS backs it with the CarPlay
 * scene-delegate edges, which are already precise.
 */
interface CarConnectionMonitor {
    val connected: StateFlow<Boolean>
}
