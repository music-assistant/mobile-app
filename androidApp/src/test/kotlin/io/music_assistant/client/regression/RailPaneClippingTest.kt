package io.music_assistant.client.regression

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.music_assistant.client.nav.AdaptiveNavigationBarLayout
import io.music_assistant.client.nav.NavigationItem
import io.music_assistant.client.nav.Section
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A screen that leaves with a slide must not draw over the rail on a wide window. The test
 * moves a screen to the left of the content pane, like the old screen during a push.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w951dp-h669dp")
class RailPaneClippingTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `a screen that moves left of the content pane does not draw over the rail`() {
        composeTestRule.setContent {
            AdaptiveNavigationBarLayout(
                appName = "App",
                sections = listOf(
                    Section(
                        title = "Section",
                        items = listOf(
                            NavigationItem(
                                selected = false,
                                onClick = {},
                                icon = EmptyIcon,
                                label = "Item",
                            ),
                        ),
                    ),
                ),
                menuLabel = "Menu",
            ) { _, _ ->
                Box(Modifier.offset(x = (-OFFSET_DP).dp).fillMaxSize().background(Color.Red))
            }
        }
        composeTestRule.waitForIdle()

        val image = composeTestRule.onRoot().captureToImage().toPixelMap()
        val density = composeTestRule.density
        val y = image.height - with(density) { BLANK_ROW_FROM_BOTTOM_DP.dp.roundToPx() }
        val insideRail = with(density) { (RAIL_WIDTH_DP - 20).dp.roundToPx() }
        val insidePane = with(density) { (RAIL_WIDTH_DP + 20).dp.roundToPx() }

        assertEquals(
            "The test screen is not visible in the pane, so the test checks nothing",
            Color.Red,
            image[insidePane, y],
        )
        assertNotEquals(
            "The old screen draws over the rail",
            Color.Red,
            image[insideRail, y],
        )
    }

    private companion object {
        val EmptyIcon: ImageVector = ImageVector.Builder(
            name = "Empty",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).build()
        const val RAIL_WIDTH_DP = 280
        const val OFFSET_DP = 100
        const val BLANK_ROW_FROM_BOTTOM_DP = 40
    }
}
