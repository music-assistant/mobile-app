package io.music_assistant.client.ui.utils

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SnapToHeightLayoutTest {
    @get:Rule
    val rule = createComposeRule()

    /** A sheet gives more height while it opens, then less once its top inset applies. */
    @Test
    fun snappedHeightFollowsShrinkingMaxHeight() {
        var available by mutableIntStateOf(800)
        val height = measure(available = { available }, content = { Box(Modifier.fillMaxHeight()) })
        rule.runOnIdle { assertEquals(800, height()) }

        available = 740
        rule.runOnIdle { assertEquals(740, height()) }
    }

    @Test
    fun shorterContentKeepsTheHeight() {
        var tall by mutableStateOf(true)
        val height = measure(available = { 800 }, content = { Box(Modifier.height(if (tall) 300.dp else 100.dp)) })
        rule.runOnIdle { assertEquals(300, height()) }

        tall = false
        rule.runOnIdle { assertEquals(300, height()) }
    }

    private fun measure(available: () -> Int, content: @Composable () -> Unit): () -> Int {
        var height = -1
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                Box(Modifier.requiredHeight(available().dp)) {
                    // measuredHeight is the height the layout asks for, before the parent coerces it.
                    Layout(content = { SnapToHeightLayout(thresholdRatio = 0.6, content = content) }) { m, c ->
                        val placeable = m.single().measure(c)
                        height = placeable.measuredHeight
                        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                    }
                }
            }
        }
        return { height }
    }
}
