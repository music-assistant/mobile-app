package io.music_assistant.client.support.pages

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import io.music_assistant.client.data.model.server.ServerMediaItem
import io.music_assistant.client.support.get
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.library_quick_search
import musicassistantclient.composeapp.generated.resources.nav_home
import musicassistantclient.composeapp.generated.resources.nav_library
import musicassistantclient.composeapp.generated.resources.nav_search
import musicassistantclient.composeapp.generated.resources.nav_settings

class ItemListPage(private val type: String, composeTestRule: ComposeTestRule) :
    ComposePage(composeTestRule) {
    override fun assert() {
        composeTestRule.onAllNodesWithText(type).onFirst().assertIsDisplayed()
        assertNavBar(
            items = listOf(
                Res.string.nav_home.get(),
                Res.string.nav_library.get(),
                Res.string.nav_search.get(),
                Res.string.nav_settings.get(),
            ),
            selected = Res.string.nav_library.get(),
        )
    }

    fun clickOnMedia(item: ServerMediaItem): ItemPage {
        return clickOnMedia(item, Res.string.nav_library.get())
    }

    fun search(query: String): ItemListPage {
        composeTestRule.onNodeWithText(Res.string.library_quick_search.get())
            .assertIsDisplayed()
            .performTextInput(query)

        composeTestRule.onNodeWithText(query).performImeAction()

        return this
    }

    fun openSearch(): ItemListPage {
        composeTestRule.onNodeWithContentDescription(Res.string.library_quick_search.get()).performClick()
        return this
    }
}
