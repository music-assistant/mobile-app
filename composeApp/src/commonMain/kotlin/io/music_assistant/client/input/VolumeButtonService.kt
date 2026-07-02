package io.music_assistant.client.input

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class VolumeButtonService {
    private val _buttonPresses = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val buttonPresses = _buttonPresses.asSharedFlow()

    fun onPlatformVolumeButtonPressed() {
        _buttonPresses.tryEmit(Unit)
    }
}
