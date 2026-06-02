package io.music_assistant.client.support.pages

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.music_assistant.client.data.model.server.ServerMediaItem
import io.music_assistant.client.support.get
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.nav_home
import musicassistantclient.composeapp.generated.resources.nav_library
import musicassistantclient.composeapp.generated.resources.nav_search
import musicassistantclient.composeapp.generated.resources.nav_settings

class HomePage(composeTestRule: ComposeTestRule) : ComposePage(composeTestRule) {
    override fun assert() {
        composeTestRule.onNodeWithText(Res.string.nav_home.get()).assertIsDisplayed()
        assertNavBar(
            items = listOf(
                Res.string.nav_home.get(),
                Res.string.nav_library.get(),
                Res.string.nav_search.get(),
                Res.string.nav_settings.get(),
            ),
            selected = Res.string.nav_home.get(),
        )
    }

    fun clickOnMedia(item: ServerMediaItem): ItemPage {
        return clickOnMedia(item, Res.string.nav_home.get())
    }

    fun refresh(): HomePage {
        composeTestRule.onNodeWithContentDescription("Refresh").performClick()
        return this
    }
}
