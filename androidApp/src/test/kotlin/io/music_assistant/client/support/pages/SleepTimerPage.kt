package io.music_assistant.client.support.pages

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.music_assistant.client.support.get
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.player_sleep_timer
import musicassistantclient.composeapp.generated.resources.sleep_timer_clear
import musicassistantclient.composeapp.generated.resources.sleep_timer_hour
import musicassistantclient.composeapp.generated.resources.sleep_timer_hours
import musicassistantclient.composeapp.generated.resources.sleep_timer_minutes

class SleepTimerPage(
    private val name: String,
    private val playing: Boolean,
    private val item: String?,
    composeTestRule: ComposeTestRule,
) : ComposePage(composeTestRule) {
    override fun assert() {
        composeTestRule.onNodeWithText(Res.string.player_sleep_timer.get()).assertIsDisplayed()
        listOf(15, 30, 45).forEach {
            composeTestRule
                .onNodeWithText(Res.string.sleep_timer_minutes.get(it))
                .assertIsDisplayed()
        }
        composeTestRule.onNodeWithText(Res.string.sleep_timer_hour.get()).assertIsDisplayed()
        composeTestRule.onNodeWithText(Res.string.sleep_timer_hours.get(2)).assertIsDisplayed()
    }

    fun assertClearOffered(offered: Boolean): SleepTimerPage {
        composeTestRule.waitUntil {
            composeTestRule
                .onNodeWithText(Res.string.sleep_timer_clear.get())
                .isDisplayed() == offered
        }
        return this
    }

    fun chooseMinutes(minutes: Int): ExpandedPlayerPage {
        composeTestRule
            .onNodeWithText(Res.string.sleep_timer_minutes.get(minutes))
            .performClick()
        return ExpandedPlayerPage(name, playing, item, composeTestRule).assertOnPage()
    }

    fun clearTimer(): ExpandedPlayerPage {
        composeTestRule.onNodeWithText(Res.string.sleep_timer_clear.get()).performClick()
        return ExpandedPlayerPage(name, playing, item, composeTestRule).assertOnPage()
    }
}
