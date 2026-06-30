package io.music_assistant.client.input

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Platform hook for volume buttons that need active observation (iOS KVO).
 * [start]/[stop] must be idempotent — repeated calls in the same state are no-ops.
 */
interface PlatformVolumeButtonObserver {
    fun start()
    fun stop()
}

class RemoteVolumeButtonController {
    private val _buttonPresses = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val buttonPresses = _buttonPresses.asSharedFlow()

    private var platformObserver: PlatformVolumeButtonObserver? = null

    /**
     * True while the user is viewing a remote player. Scopes both the toast gate
     * and the (expensive) iOS observer, which flips the shared audio session to
     * `.mixWithOthers` and so must only run while a remote player is on screen.
     */
    var observingRemote: Boolean = false
        set(value) {
            field = value
            if (value) platformObserver?.start() else platformObserver?.stop()
        }

    fun setPlatformObserver(observer: PlatformVolumeButtonObserver?) {
        if (platformObserver === observer) return
        platformObserver?.stop()
        platformObserver = observer
        if (observingRemote) observer?.start()
    }

    fun onPlatformVolumeButtonPressed() {
        if (observingRemote) _buttonPresses.tryEmit(Unit)
    }
}
