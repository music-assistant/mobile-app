package io.music_assistant.client.support.pages

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.music_assistant.client.support.get
import io.music_assistant.client.support.onNodeInContent
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.cd_party_qr
import musicassistantclient.composeapp.generated.resources.party_copy_link
import musicassistantclient.composeapp.generated.resources.party_guest_access_off
import musicassistantclient.composeapp.generated.resources.party_link_copied

/** The party invite sheet. It loads after it opens, so each check waits for its text. */
class PartyPage(composeTestRule: ComposeTestRule) : ComposePage(composeTestRule) {
    override fun assert() = Unit

    fun assertInvite(title: String, link: String): PartyPage {
        waitForText(link)
        // The drawer item behind the sheet has the default title too.
        composeTestRule.onNodeInContent(hasText(title)).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(Res.string.cd_party_qr.get()).assertIsDisplayed()
        return this
    }

    fun assertGuestAccessOff(): PartyPage {
        waitForText(Res.string.party_guest_access_off.get())
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
