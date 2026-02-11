package io.music_assistant.client.ui.compose.search

import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SearchInputTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `requests focus`() {
        composeTestRule.setContent {
            SearchInput(query = "", onQueryChanged = {})
        }

        composeTestRule.onNodeWithText("Type at least 3 characters to search").assertIsFocused()
    }
}
