package io.music_assistant.client.support.pages

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import io.music_assistant.client.data.model.server.ServerMediaItem
import io.music_assistant.client.support.get
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.nav_home
import musicassistantclient.composeapp.generated.resources.nav_library
import musicassistantclient.composeapp.generated.resources.nav_search
import musicassistantclient.composeapp.generated.resources.nav_settings
import musicassistantclient.composeapp.generated.resources.search_min_chars
import musicassistantclient.composeapp.generated.resources.search_start

class SearchPage(composeTestRule: ComposeTestRule) : ComposePage(composeTestRule) {
    override fun assert() {
        composeTestRule.onNodeWithText(Res.string.search_start.get()).assertIsDisplayed()
        assertNavBar(
            items = listOf(
                Res.string.nav_home.get(),
                Res.string.nav_library.get(),
                Res.string.nav_search.get(),
                Res.string.nav_settings.get(),
            ),
            selected = Res.string.nav_search.get(),
        )
    }

    fun search(query: String): SearchPage {
        composeTestRule.onNodeWithText(Res.string.search_min_chars.get())
            .assertIsDisplayed()
            .performTextInput(query)

        return this
    }

    fun assertResult(result: String): SearchPage {
        composeTestRule.onNodeWithText(result).assertIsDisplayed()
        return this
    }

    fun clickOnMedia(item: ServerMediaItem): MediaItemPage {
        return clickOnMedia(item, Res.string.nav_search.get())
    }
}
