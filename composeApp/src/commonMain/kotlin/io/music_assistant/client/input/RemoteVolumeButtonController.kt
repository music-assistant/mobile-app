package io.music_assistant.client.input

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

interface PlatformVolumeButtonObserver {
    fun start()
    fun stop()
}

class RemoteVolumeButtonController {
    private val _buttonPresses = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val buttonPresses = _buttonPresses.asSharedFlow()

    private var platformObserver: PlatformVolumeButtonObserver? = null
    private var observingPlatformButtons = false

    fun setPlatformObserver(observer: PlatformVolumeButtonObserver?) {
        platformObserver?.stop()
        platformObserver = observer
        if (observingPlatformButtons) {
            platformObserver?.start()
        }
    }

    fun startObservingPlatformButtons() {
        observingPlatformButtons = true
        platformObserver?.start()
    }

    fun stopObservingPlatformButtons() {
        observingPlatformButtons = false
        platformObserver?.stop()
    }

    fun onPlatformVolumeButtonPressed() {
        _buttonPresses.tryEmit(Unit)
    }
}
