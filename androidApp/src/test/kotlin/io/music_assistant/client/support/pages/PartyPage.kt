package io.music_assistant.client.support.pages

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import io.music_assistant.client.support.get
import io.music_assistant.client.support.onNodeInContent
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.cd_party_qr
import musicassistantclient.composeapp.generated.resources.common_save
import musicassistantclient.composeapp.generated.resources.party_copy_link
import musicassistantclient.composeapp.generated.resources.party_guest_access_off
import musicassistantclient.composeapp.generated.resources.party_guest_access_off_host
import musicassistantclient.composeapp.generated.resources.party_link_copied
import musicassistantclient.composeapp.generated.resources.party_settings

/** The party invite sheet. It loads after it opens, so each check waits for its text. */
class PartyPage(composeTestRule: ComposeTestRule) : ComposePage(composeTestRule) {
    override fun assert() = Unit

    fun assertInvite(title: String, link: String): PartyPage {
        waitForText(link)
        // The drawer item behind the sheet has the default title too, and the name field the name.
        composeTestRule.onNodeInContent(hasText(title) and hasSetTextAction().not()).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(Res.string.cd_party_qr.get()).assertIsDisplayed()
        return this
    }

    fun assertGuestAccessOff(): PartyPage {
        waitForText(Res.string.party_guest_access_off.get())
        return this
    }

    /** The host sees the switch, so the sheet tells the host to turn it on here. */
    fun assertHostGuestAccessOff(): PartyPage {
        waitForText(Res.string.party_guest_access_off_host.get())
        return this
    }

    fun assertNoSettings(): PartyPage {
        composeTestRule.onAllNodesWithText(Res.string.party_settings.get()).assertCountEquals(0)
        return this
    }

    fun toggleGuestAccess(label: String): PartyPage {
        composeTestRule.onNode(isToggleable() and hasText(label)).performScrollTo().performClick()
        return this
    }

    /** While the party is on, the settings are folded away. */
    fun expandSettings(): PartyPage {
        composeTestRule.onNodeWithText(Res.string.party_settings.get()).performScrollTo().performClick()
        return this
    }

    fun type(label: String, text: String): PartyPage {
        composeTestRule.onNode(hasSetTextAction() and hasText(label)).performScrollTo().performTextReplacement(text)
        return this
    }

    fun save(): PartyPage {
        composeTestRule.onNodeWithText(Res.string.common_save.get()).performScrollTo().performClick()
        return this
    }

    fun copyLink(): PartyPage {
        composeTestRule.onNodeWithText(Res.string.party_copy_link.get()).performClick()
        waitForText(Res.string.party_link_copied.get())
        return this
    }

    private fun waitForText(text: String) {
        composeTestRule.waitUntil("'$text' is in the party sheet") {
            composeTestRule.onNodeWithText(text).isDisplayed()
        }
    }
}
