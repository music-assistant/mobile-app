package io.music_assistant.client.utils

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `map { }.stateIn(scope)` updates its value on another coroutine, so a write followed by a read
 * could see the old value. The local player made pause decisions on such a stale read.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StateFlowViewTest {
    @Test
    fun `the value follows a write to the source at once`() {
        val source = MutableStateFlow(1)
        val view = source.mapState { it * 10 }

        source.value = 2

        assertEquals(20, view.value)
    }

    @Test
    fun `a collector gets the current value and then each distinct change`() = runTest {
        val source = MutableStateFlow(1)
        val view = source.mapState { it / 2 }
        val seen = mutableListOf<Int>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { view.toList(seen) }

        source.value = 2
        source.value = 3
        source.value = 4

        assertEquals(listOf(0, 1, 2), seen)
        job.cancel()
    }

    @Test
    fun `a combined value follows a write to either source at once`() {
        val left = MutableStateFlow(false)
        val right = MutableStateFlow(false)
        val view = combineState(left, right) { a, b -> a && b }

        left.value = true
        assertEquals(false, view.value)
        right.value = true

        assertEquals(true, view.value)
    }

    @Test
    fun `a combined collector gets each distinct change of either source`() = runTest {
        val left = MutableStateFlow(1)
        val right = MutableStateFlow(10)
        val view = combineState(left, right) { a, b -> a + b }
        val seen = mutableListOf<Int>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { view.toList(seen) }

        left.value = 2
        right.value = 20
        right.value = 20

        assertEquals(listOf(11, 12, 22), seen)
        job.cancel()
    }
}
