package io.music_assistant.client.support.pages

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithText
import io.music_assistant.client.data.model.client.items.description
import io.music_assistant.client.data.model.server.AudioFormat
import io.music_assistant.client.support.get
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.quality_dialog_title

class AudioChainPage(composeTestRule: ComposeTestRule) : ComposePage(composeTestRule) {
    override fun assert() {
        composeTestRule.onNodeWithText(Res.string.quality_dialog_title.get()).assertIsDisplayed()
    }

    fun assertFormatDisplayed(audioFormat: AudioFormat): AudioChainPage {
        composeTestRule.onNodeWithText(audioFormat.description).assertIsDisplayed()
        return this
    }
}
