package io.music_assistant.client.ui.compose.library

import io.music_assistant.client.ui.compose.common.moveToEnabledBoundary
import kotlin.test.Test
import kotlin.test.assertEquals

class LibraryCategoryConfigUtilsTest {
    @Test
    fun `enabling a disabled tab moves it to the end of the enabled section`() {
        val items = listOf(
            "A" to true,
            "B" to true,
            "C" to false,
            "D" to false,
        )
        val result = moveToEnabledBoundary(items, "C", newEnabled = true)
        assertEquals(
            listOf("A" to true, "B" to true, "C" to true, "D" to false),
            result,
        )
    }

    @Test
    fun `disabling an enabled tab moves it to the top of the disabled section`() {
        val items = listOf(
            "A" to true,
            "B" to true,
            "C" to true,
            "D" to false,
        )
        val result = moveToEnabledBoundary(items, "A", newEnabled = false)
        assertEquals(
            listOf("B" to true, "C" to true, "A" to false, "D" to false),
            result,
        )
    }

    @Test
    fun `enabling an already-last-enabled tab is a no-op boundary reinsert`() {
        val items = listOf("A" to true, "B" to true)
        val result = moveToEnabledBoundary(items, "B", newEnabled = true)
        assertEquals(items, result)
    }

    @Test
    fun `disabling tab from middle preserves other ordering`() {
        val items = listOf(
            "A" to true,
            "B" to true,
            "C" to true,
            "D" to true,
            "E" to false,
        )
        val result = moveToEnabledBoundary(items, "B", newEnabled = false)
        assertEquals(
            listOf("A" to true, "C" to true, "D" to true, "B" to false, "E" to false),
            result,
        )
    }

    @Test
    fun `enabling a tab from deep in disabled section does not disturb other disabled tabs relative order`() {
        val items = listOf(
            "A" to true,
            "B" to false,
            "C" to false,
            "D" to false,
        )
        val result = moveToEnabledBoundary(items, "D", newEnabled = true)
        assertEquals(
            listOf("A" to true, "D" to true, "B" to false, "C" to false),
            result,
        )
    }

    @Test
    fun `unknown target returns input unchanged`() {
        val items = listOf("A" to true, "B" to false)
        val result = moveToEnabledBoundary(items, "Z", newEnabled = true)
        assertEquals(items, result)
    }

    @Test
    fun `disabling the only enabled tab places it first in the disabled section`() {
        val items = listOf("A" to true, "B" to false, "C" to false)
        val result = moveToEnabledBoundary(items, "A", newEnabled = false)
        assertEquals(
            listOf("A" to false, "B" to false, "C" to false),
            result,
        )
    }
}
