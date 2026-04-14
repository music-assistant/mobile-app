package io.music_assistant.client.support.pages

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.music_assistant.client.support.isTab

fun ComposePage.clickOnMedia(name: String, navigationItem: String): MedaItemPage {
    composeTestRule.onNodeWithText(name)
        .assertIsDisplayed()
        .performClick()

    return MedaItemPage(name, navigationItem, composeTestRule).assertOnPage()
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

fun ComposePage.clickNavBarItem(item: String) {
    composeTestRule.onNode(isTab(item)).assertIsDisplayed().performClick()
}

fun ComposePage.clickSearch(): SearchPage {
    clickNavBarItem("Search")
    return SearchPage(composeTestRule).assertOnPage()
}

fun <T : Page> ComposePage.clickSearch(destination: T): T {
    clickNavBarItem("Search")
    return destination.assertOnPage()
}

fun <T : Page> ComposePage.clickHome(destination: T): T {
    clickNavBarItem("Home")
    return destination.assertOnPage()
}