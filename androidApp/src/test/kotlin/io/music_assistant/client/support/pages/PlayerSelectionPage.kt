package io.music_assistant.client.support.pages

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.music_assistant.client.support.get
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.players_title

class PlayerSelectionPage<H : Page>(private val host: H, composeTestRule: ComposeTestRule) : ComposePage(
    composeTestRule,
) {
    override fun assert() {
        composeTestRule.onNodeWithText(Res.string.players_title.get()).assertIsDisplayed()
    }

    fun selectPlayer(name: String): H {
        composeTestRule.onNodeWithText(name).performClick()
        assertPlayer(name)
        return host.assertOnPage()
    }
}
