package io.music_assistant.client.ui.compose.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BoundedModalSheetLayoutTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun fractionalChildInUnboundedHeight() = verify(Constraints(0, 400, 0, Constraints.Infinity), true, 400, 800)

    @Test
    fun wrapContentChildInUnboundedHeight() = verify(Constraints(0, 400, 0, Constraints.Infinity), false, 100, 800)

    @Test
    fun boundedHeightKeepsMeasuredHeight() = verify(Constraints(0, 400, 0, 800), false, 100, 100)

    @Test
    fun unboundedWidthDoesNotExpandBoundedHeight() = verify(
        Constraints(0, Constraints.Infinity, 0, 800),
        false,
        100,
        100,
    )

    @Test
    fun bothAxesUnbounded() = verify(Constraints(), false, 100, 800)

    private fun verify(incoming: Constraints, fractional: Boolean, expectedChild: Int, expectedParent: Int) {
        var reportedHeight = -1
        var childHeight = -1
        var childTop = -1
        var rootTop = -1
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                Layout(
                    modifier = Modifier.onGloballyPositioned { rootTop = it.positionInRoot().y.toInt() },
                    content = {
                        Layout(
                            modifier = Modifier.boundedModalSheetLayout(
                                IntSize(400, 800),
                                WindowUnavailableSheetWidth,
                                WindowUnavailableSheetHeight,
                            ),
                            content = {
                                Box(
                                    (if (fractional) Modifier.fillMaxHeight(0.5f) else Modifier.height(100.dp))
                                        .onGloballyPositioned {
                                            childHeight = it.size.height
                                            childTop = it.positionInRoot().y.toInt()
                                        },
                                )
                            },
                        ) { measurables, constraints ->
                            val child = measurables.single().measure(constraints)
                            layout(child.width, child.height) {
                                child.placeRelative(0, constraints.maxHeight - child.height)
                            }
                        }
                    },
                ) { measurables, _ ->
                    val sheet = measurables.single().measure(incoming)
                    reportedHeight = sheet.height
                    layout(400, 800) { sheet.placeRelative(0, (800 - sheet.height) / 2) }
                }
            }
        }
        rule.runOnIdle {
            assertEquals(expectedParent, reportedHeight)
            assertEquals(expectedChild, childHeight)
            if (!incoming.hasBoundedHeight) assertEquals(800 - expectedChild, childTop - rootTop)
        }
    }
}
