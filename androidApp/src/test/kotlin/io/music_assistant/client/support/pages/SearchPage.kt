package io.music_assistant.client.support.pages

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import io.music_assistant.client.data.model.server.ServerMediaItem
import io.music_assistant.client.support.get
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.common_clear
import musicassistantclient.composeapp.generated.resources.nav_home
import musicassistantclient.composeapp.generated.resources.nav_library
import musicassistantclient.composeapp.generated.resources.nav_search
import musicassistantclient.composeapp.generated.resources.nav_settings
import musicassistantclient.composeapp.generated.resources.search_query_label
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
        composeTestRule.onNodeWithText(Res.string.search_query_label.get())
            .assertIsDisplayed()
            .performTextInput(query)

        composeTestRule.onNodeWithText(query)
            .performImeAction()

        return this
    }

    fun assertResult(result: String): SearchPage {
        composeTestRule.onNodeWithText(result).assertIsDisplayed()
        return this
    }

    fun assertNoResults(): SearchPage {
        composeTestRule.onNodeWithText(Res.string.search_start.get()).assertIsDisplayed()
        return this
    }

    fun clickOnMedia(item: ServerMediaItem): ItemPage {
        return clickOnMedia(item, Res.string.nav_search.get())
    }

    fun clearQuery(): SearchPage {
        composeTestRule.onNodeWithContentDescription(Res.string.common_clear.get())
            .performClick()
        return this
    }
}
