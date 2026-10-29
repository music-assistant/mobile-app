package io.music_assistant.client.support.pages

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.music_assistant.client.support.get
import io.music_assistant.client.support.hasRole
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.common_done
import musicassistantclient.composeapp.generated.resources.filter_sheet_title
import musicassistantclient.composeapp.generated.resources.filter_sources

class FilterSheetPage(composeTestRule: ComposeTestRule) : ComposePage(composeTestRule) {
    override fun assert() {
        composeTestRule.onNodeWithText(Res.string.filter_sheet_title.get()).assertIsDisplayed()
    }

    fun enableChip(text: String): FilterSheetPage {
        composeTestRule.onNode(hasRole(Role.Checkbox).and(hasText(text))).performClick()
        composeTestRule.onNode(hasRole(Role.Checkbox).and(hasText(text))).assertIsSelected()
        return this
    }

    fun enableSwitch(text: String): FilterSheetPage {
        composeTestRule.onNodeWithText(text).performClick()
        composeTestRule.onNode(hasRole(Role.Switch).and(hasAnyAncestor(hasText(text)))).assertIsOn()
        return this
    }

    fun assertNoSwitch(text: String): FilterSheetPage {
        composeTestRule.onNodeWithText(text).assertDoesNotExist()
        return this
    }

    /** Opens the "Music sources" picker, checks each of [labels], and confirms. */
    fun pickSources(vararg labels: String): FilterSheetPage {
        composeTestRule.onNodeWithText(Res.string.filter_sources.get()).performClick()
        labels.forEach { label ->
            composeTestRule.onNode(hasText(label).and(hasAnyAncestor(isDialog()))).performClick()
        }
        composeTestRule.onNodeWithText(Res.string.common_done.get()).performClick()
        return this
    }
}
