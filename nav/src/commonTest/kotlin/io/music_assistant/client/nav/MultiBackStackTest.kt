package io.music_assistant.client.nav

import androidx.navigation3.runtime.NavKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MultiBackStackTest {
    @Test
    fun `back at home root does not remove it`() {
        val root = TestNavKey("home")
        val backStack = mutableListOf(root)
        val multiBackStack = MultiBackStack(listOf(backStack))

        repeat(3) { multiBackStack.removeLastOrNull() }

        assertEquals(listOf(root), backStack)
    }

    @Test
    fun `repeated back from another tab preserves both roots`() {
        val home = mutableListOf(TestNavKey("home"))
        val library = mutableListOf(TestNavKey("library"), TestNavKey("album"))
        val multiBackStack = MultiBackStack(listOf(home, library))
        multiBackStack.currentBackStack = 1

        repeat(5) { multiBackStack.removeLastOrNull() }

        assertEquals(0, multiBackStack.currentBackStack)
        assertEquals(listOf(TestNavKey("home")), home)
        assertEquals(listOf(TestNavKey("library")), library)
    }

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

private data class TestNavKey(val id: String) : NavKey
private class MockScreenState : ScreenState {
    var isReset: Boolean = false
        private set

    override fun reset() {
        isReset = true
    }
}
