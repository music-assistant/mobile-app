package io.music_assistant.client.ui.compose.nav

import androidx.navigation3.runtime.NavKey
import kotlin.test.Test
import kotlin.test.assertEquals

class MultiBackStackTest {
    @Test
    fun `#resetCurrentBackStack restores back stack to original root`() {
        val backStack = mutableListOf(TestNavKey("root"), TestNavKey("top"))
        val multiBackStack = MultiBackStack(listOf(backStack))
        multiBackStack.resetCurrentBackStack()
        assertEquals(listOf(TestNavKey("root")), backStack)
    }
}

private data class TestNavKey(val id: String) :  NavKey
