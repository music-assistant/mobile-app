package io.music_assistant.client.support.pages

import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.music_assistant.client.data.model.server.ServerMediaItem
import io.music_assistant.client.support.get
import io.music_assistant.client.ui.compose.home.HomeScreenSemantics
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.library_error
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

    fun clickOnShortcut(item: ServerMediaItem): ItemPage {
        return clickOnMedia(
            item,
            Res.string.nav_home.get(),
            withinTag = HomeScreenSemantics.SHORTCUTS_ROW_TAG,
        )
    }

    fun playShortcut(item: ServerMediaItem): HomePage {
        playMedia(item, withinTag = HomeScreenSemantics.SHORTCUTS_ROW_TAG)
        return this
    }

    fun assertShortcutDisplayed(item: ServerMediaItem): HomePage {
        assertMediaDisplayed(item.name, withinTag = HomeScreenSemantics.SHORTCUTS_ROW_TAG)
        return this
    }

    fun assertErrorLoadingData(): HomePage {
        composeTestRule.onNodeWithText(Res.string.library_error.get()).assertIsDisplayed()
        return this
    }

    fun assertProgress(): HomePage {
        composeTestRule.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate))
            .assertIsDisplayed()

        return this
    }
}
