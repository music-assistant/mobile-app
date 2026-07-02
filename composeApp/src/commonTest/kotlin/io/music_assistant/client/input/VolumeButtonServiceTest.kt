package io.music_assistant.client.input

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class VolumeButtonServiceTest {
    @Test
    fun buttonPressesAreEmitted() = runTest {
        val service = VolumeButtonService()

        service.buttonPresses.test {
            service.onPlatformVolumeButtonPressed()
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
    }
}
