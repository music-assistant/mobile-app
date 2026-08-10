package io.music_assistant.client.support.pages

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import io.music_assistant.client.support.get
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.nav_home
import musicassistantclient.composeapp.generated.resources.nav_library
import musicassistantclient.composeapp.generated.resources.nav_search
import musicassistantclient.composeapp.generated.resources.nav_settings

open class ItemListPage(private val title: String, private val navigationItem: String, composeTestRule: ComposeTestRule) : ComposePage(
    composeTestRule,
) {
    override fun assert() {
        composeTestRule.onAllNodesWithText(title).onFirst().assertIsDisplayed()
        assertNavBar(
            items = listOf(
                Res.string.nav_home.get(),
                Res.string.nav_library.get(),
                Res.string.nav_search.get(),
                Res.string.nav_settings.get(),
            ),
            selected = navigationItem,
        )
    }
}
