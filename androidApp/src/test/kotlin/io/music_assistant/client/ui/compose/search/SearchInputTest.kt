package io.music_assistant.client.ui.compose.search

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.music_assistant.client.support.get
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.search_query_label
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class SearchInputTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `requests focus`() {
        composeTestRule.setContent {
            SearchInput(query = "")
        }

        composeTestRule.onNodeWithText(Res.string.search_query_label.get()).assertIsFocused()
    }

    @Test
    fun `does not request focus when query is not empty`() {
        val restorationTester = StateRestorationTester(composeTestRule)

        restorationTester.setContent {
            SearchInput(query = "blah")
        }

        composeTestRule.onNodeWithText("blah").assertIsNotFocused()
    }
}
