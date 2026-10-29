package io.music_assistant.client.support.pages

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.music_assistant.client.data.model.server.ServerMediaItem
import io.music_assistant.client.support.get
import io.music_assistant.client.ui.compose.ScreenSemantics
import io.music_assistant.client.ui.compose.home.HomeScreenSemantics
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.discover_edit_rows
import musicassistantclient.composeapp.generated.resources.discover_save_rows
import musicassistantclient.composeapp.generated.resources.library_error
import musicassistantclient.composeapp.generated.resources.nav_discover
import musicassistantclient.composeapp.generated.resources.nav_search

class HomePage(composeTestRule: ComposeTestRule) : ComposePage(composeTestRule) {
    override fun assert() {
        composeTestRule
            .onNode(hasTestTag(ScreenSemantics.TITLE_TAG).and(hasText(Res.string.nav_discover.get())))
            .assertIsDisplayed()

        assertNavBar(
            items = listOf(
                Res.string.nav_discover.get(),
                Res.string.nav_search.get(),
            ),
            selected = Res.string.nav_discover.get(),
        )
    }

    fun clickOnMedia(item: ServerMediaItem, withinTag: String? = null): ItemPage {
        return clickOnMedia(item, Res.string.nav_discover.get(), withinTag)
    }

    fun refresh(): HomePage {
        composeTestRule.onNodeWithContentDescription("Refresh").performClick()
        return this
    }

    fun editRows(): HomePage {
        composeTestRule.onNodeWithContentDescription(Res.string.discover_edit_rows.get()).performClick()
        return this
    }

    fun saveRows(): HomePage {
        composeTestRule.onNodeWithContentDescription(Res.string.discover_save_rows.get()).performClick()
        return this
    }

    fun clickOnShortcut(item: ServerMediaItem): ItemPage {
        return clickOnMedia(
            item,
            Res.string.nav_discover.get(),
            withinTag = HomeScreenSemantics.SHORTCUTS_ROW_TAG,
        )
    }

    fun playShortcut(item: ServerMediaItem): HomePage {
        playMedia(item, withinTag = HomeScreenSemantics.SHORTCUTS_ROW_TAG)
        return this
    }

    fun assertShortcutDisplayed(item: ServerMediaItem): HomePage {
        assertMediaDisplayed(item, withinTag = HomeScreenSemantics.SHORTCUTS_ROW_TAG)
        return this
    }

    fun assertErrorLoadingData(): HomePage {
        composeTestRule.waitUntil("library error is displayed") {
            composeTestRule.onNodeWithText(Res.string.library_error.get()).isDisplayed()
        }
        return this
    }
}
