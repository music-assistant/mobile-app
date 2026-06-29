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
    // Compose only reports full backgrounding; iOS filters resign-active itself.
    private var isAppForeground = true
    private var isPlatformObserverStarted = false

    fun setPlatformObserver(observer: PlatformVolumeButtonObserver?) {
        if (platformObserver === observer) return
        if (isPlatformObserverStarted) {
            platformObserver?.stop()
            isPlatformObserverStarted = false
        }
        platformObserver = observer
        updatePlatformObserver()
    }

    fun startObservingPlatformButtons() {
        observingPlatformButtons = true
        updatePlatformObserver()
    }

    fun stopObservingPlatformButtons() {
        observingPlatformButtons = false
        updatePlatformObserver()
    }

    fun onAppForeground() {
        isAppForeground = true
        updatePlatformObserver()
    }

    fun onAppBackground() {
        isAppForeground = false
        updatePlatformObserver()
    }

    fun onPlatformVolumeButtonPressed() {
        if (!isAppForeground || !observingPlatformButtons) return
        _buttonPresses.tryEmit(Unit)
    }

    private fun updatePlatformObserver() {
        val observer = platformObserver
        val shouldStart = observer != null && observingPlatformButtons && isAppForeground
        if (shouldStart == isPlatformObserverStarted) return

        if (shouldStart) {
            observer.start()
            isPlatformObserverStarted = true
        } else {
            observer?.stop()
            isPlatformObserverStarted = false
        }
    }
}
