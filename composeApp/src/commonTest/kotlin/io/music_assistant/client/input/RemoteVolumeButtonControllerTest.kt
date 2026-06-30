package io.music_assistant.client.input

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class RemoteVolumeButtonControllerTest {
    @Test
    fun buttonPressesAreEmittedOnlyWhileObservingRemote() = runTest {
        val controller = RemoteVolumeButtonController()

        controller.buttonPresses.test {
            controller.onPlatformVolumeButtonPressed()
            expectNoEvents()

            controller.observingRemote = true
            controller.onPlatformVolumeButtonPressed()
            awaitItem()

            controller.observingRemote = false
            controller.onPlatformVolumeButtonPressed()
            expectNoEvents()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun platformObserverRunsOnlyWhileObservingRemote() {
        val observer = RecordingPlatformVolumeButtonObserver()
        val controller = RemoteVolumeButtonController()

        controller.setPlatformObserver(observer)
        assertEquals(0, observer.starts)
        assertEquals(0, observer.stops)

        controller.observingRemote = true
        assertEquals(1, observer.starts)
        assertEquals(0, observer.stops)

        controller.observingRemote = false
        assertEquals(1, observer.starts)
        assertEquals(1, observer.stops)
    }

    @Test
    fun replacingStartedObserverStopsOldAndStartsNew() {
        val first = RecordingPlatformVolumeButtonObserver()
        val second = RecordingPlatformVolumeButtonObserver()
        val controller = RemoteVolumeButtonController()

        controller.setPlatformObserver(first)
        controller.observingRemote = true
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
