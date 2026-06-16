package io.music_assistant.client.ui.compose.nav

import androidx.navigation3.runtime.NavKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MultiBackStackTest {
    @Test
    fun `resetCurrentBackStack restores back stack to original root`() {
        val backStack = mutableListOf(TestNavKey("root"), TestNavKey("top"))
        val multiBackStack = MultiBackStack(listOf(backStack))

        val screenState = MockScreenState()
        multiBackStack.resetCurrentBackStack(screenState)
        assertEquals(listOf(TestNavKey("root")), backStack)
        assertFalse(screenState.isReset)
    }

    @Test
    fun `resetCurrentBackStack resets ScreenState if the back stack is at its root`() {
        val backStack = mutableListOf(TestNavKey("root"))
        val multiBackStack = MultiBackStack(listOf(backStack))

        val screenState = MockScreenState()
        multiBackStack.resetCurrentBackStack(screenState)
        assertTrue(screenState.isReset)
    }
}

private data class TestNavKey(val id: String) :  NavKey
private class MockScreenState : ScreenState {
    var isReset: Boolean = false
        private set

    override fun reset() {
        isReset = true
    }
}
