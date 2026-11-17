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
import musicassistantclient.composeapp.generated.resources.nav_discover
import musicassistantclient.composeapp.generated.resources.nav_search
import musicassistantclient.composeapp.generated.resources.search_query_label
import musicassistantclient.composeapp.generated.resources.search_recent
import musicassistantclient.composeapp.generated.resources.search_start

class SearchPage(composeTestRule: ComposeTestRule, val query: String? = null) : ComposePage(composeTestRule) {
    override fun assert() {
        if (query != null) {
            composeTestRule.onNodeWithText(query).assertIsDisplayed()
        } else {
            // The idle page shows the recent searches or the start hint, so check the empty field.
            composeTestRule.onNodeWithText(Res.string.search_query_label.get()).assertIsDisplayed()
        }

        assertNavBar(
            items = listOf(
                Res.string.nav_discover.get(),
                Res.string.nav_search.get(),
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

    fun assertNoResults(): SearchPage {
        composeTestRule.onNodeWithText(Res.string.search_start.get()).assertIsDisplayed()
        return this
    }

    fun clickOnMedia(item: ServerMediaItem): ItemPage {
        return clickOnMedia(item, Res.string.nav_search.get())
    }

    fun assertRecentSearches(vararg terms: String): SearchPage {
        composeTestRule.onNodeWithText(Res.string.search_recent.get()).assertIsDisplayed()
        terms.forEach { composeTestRule.onNodeWithText(it).assertIsDisplayed() }
        return this
    }

    fun clickRecentSearch(term: String): SearchPage {
        composeTestRule.onNodeWithText(term).performClick()
        return this
    }

    fun clearQuery(): SearchPage {
        composeTestRule.onNodeWithContentDescription(Res.string.common_clear.get())
            .performClick()
        return this
    }
}
