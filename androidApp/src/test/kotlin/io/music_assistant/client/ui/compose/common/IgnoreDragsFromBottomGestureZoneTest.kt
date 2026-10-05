package io.music_assistant.client.ui.compose.common

import android.view.View
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

// Issue #1053: the iOS swipe-home gesture dragged the full-screen player pager to the next player.
@RunWith(AndroidJUnit4::class)
class IgnoreDragsFromBottomGestureZoneTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var pagerState: PagerState

    @Before
    fun setUp() {
        lateinit var view: View
        composeTestRule.setContent {
            view = LocalView.current
            pagerState = rememberPagerState { PAGE_COUNT }
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize().testTag(PAGER).ignoreDragsFromBottomGestureZone(),
            ) { page -> BasicText("Page $page", Modifier.fillMaxSize()) }
        }
        composeTestRule.runOnIdle {
            val insets = WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.mandatorySystemGestures(), Insets.of(0, 0, 0, ZONE_PX))
                .build()
            ViewCompat.dispatchApplyWindowInsets(view, insets)
        }
        composeTestRule.waitForIdle()
    }

    private fun swipeLeftAt(distanceFromBottom: Float) {
        composeTestRule.onNodeWithTag(PAGER).performTouchInput {
            val y = height - distanceFromBottom
            swipe(start = Offset(width * 0.9f, y), end = Offset(width * 0.1f, y))
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun `swipe starting in the bottom gesture zone keeps the page`() {
        swipeLeftAt(distanceFromBottom = ZONE_PX / 2f)

        assertEquals(0, pagerState.currentPage)
    }

    @Test
    fun `swipe starting above the bottom gesture zone changes the page`() {
        swipeLeftAt(distanceFromBottom = ZONE_PX * 2f)

        assertEquals(1, pagerState.currentPage)
    }

    private companion object {
        const val PAGER = "pager"
        const val PAGE_COUNT = 3
        const val ZONE_PX = 100
    }
}
