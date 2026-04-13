package io.music_assistant.client.support.pages

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.music_assistant.client.support.isTab

fun ComposePage.clickOnMedia(name: String): MedaItemPage {
    composeTestRule.onNodeWithText(name)
        .assertIsDisplayed()
        .performClick()

    return MedaItemPage(name, composeTestRule).assertOnPage()
}

fun ComposePage.assertNavBar(items: List<String>, selected: String) {
    items.forEach {
        if (it == selected) {
            composeTestRule.onNode(isTab(it)).assertIsSelected()
        } else {
            composeTestRule.onNode(isTab(it)).assertIsNotSelected()
        }
    }
}