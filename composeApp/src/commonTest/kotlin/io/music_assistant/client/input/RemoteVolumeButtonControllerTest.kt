package io.music_assistant.client.input

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class RemoteVolumeButtonControllerTest {
    @Test
    fun buttonPressesAreEmittedOnlyWhileObservedAndForegrounded() = runTest {
        val controller = RemoteVolumeButtonController()

        controller.buttonPresses.test {
            controller.onPlatformVolumeButtonPressed()
            expectNoEvents()

            controller.startObservingPlatformButtons()
            controller.onPlatformVolumeButtonPressed()
            awaitItem()

            controller.onAppBackground()
            controller.onPlatformVolumeButtonPressed()
            expectNoEvents()

            controller.onAppForeground()
            controller.onPlatformVolumeButtonPressed()
            awaitItem()

            controller.stopObservingPlatformButtons()
            controller.onPlatformVolumeButtonPressed()
            expectNoEvents()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun platformObserverRunsOnlyWhileObservedAndForegrounded() {
        val observer = RecordingPlatformVolumeButtonObserver()
        val controller = RemoteVolumeButtonController()

        controller.setPlatformObserver(observer)
        assertEquals(0, observer.starts)
        assertEquals(0, observer.stops)

        controller.startObservingPlatformButtons()
        assertEquals(1, observer.starts)
        assertEquals(0, observer.stops)

        controller.onAppBackground()
        assertEquals(1, observer.starts)
        assertEquals(1, observer.stops)

        controller.onAppForeground()
        assertEquals(2, observer.starts)
        assertEquals(1, observer.stops)

        controller.stopObservingPlatformButtons()
        assertEquals(2, observer.starts)
        assertEquals(2, observer.stops)
    }

    @Test
    fun replacingStartedObserverStopsOldAndStartsNew() {
        val first = RecordingPlatformVolumeButtonObserver()
        val second = RecordingPlatformVolumeButtonObserver()
        val controller = RemoteVolumeButtonController()

        controller.setPlatformObserver(first)
        controller.startObservingPlatformButtons()
        assertEquals(1, first.starts)
        assertEquals(0, first.stops)

        controller.setPlatformObserver(second)
        assertEquals(1, first.starts)
        assertEquals(1, first.stops)
        assertEquals(1, second.starts)
        assertEquals(0, second.stops)
    }
}

private class RecordingPlatformVolumeButtonObserver : PlatformVolumeButtonObserver {
    var starts = 0
    var stops = 0

    override fun start() {
        starts++
    }

    override fun stop() {
        stops++
    }
}
