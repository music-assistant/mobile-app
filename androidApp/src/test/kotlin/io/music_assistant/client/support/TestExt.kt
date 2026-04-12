package io.music_assistant.client.support

import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.ComposeTestRule

fun ComposeTestRule.assertNavBar(items: List<String>, selected: String) {
    items.forEach {
        if (it == selected) {
            this.onNode(isTab(it)).assertIsSelected()
        } else {
            this.onNode(isTab(it)).assertIsNotSelected()
        }
    }
}