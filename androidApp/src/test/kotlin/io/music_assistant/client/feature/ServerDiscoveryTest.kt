package io.music_assistant.client.feature

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.music_assistant.client.discovery.MdnsService
import io.music_assistant.client.support.FakeMdnsBrowser
import io.music_assistant.client.support.Qualifiers
import io.music_assistant.client.support.get
import io.music_assistant.client.support.launchApp
import io.music_assistant.client.support.rules.createTestRuleChain
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.settings_discovered_server_ha_addon
import musicassistantclient.composeapp.generated.resources.settings_port
import musicassistantclient.composeapp.generated.resources.settings_port_home_assistant_hint
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent.inject
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = Qualifiers.MEDIUM_PHONE)
class ServerDiscoveryTest {
    @get:Rule
    val testRuleChain = createTestRuleChain()

    @get:Rule
    val composeTestRule = createComposeRule()

    private val mdnsBrowser: FakeMdnsBrowser by inject(FakeMdnsBrowser::class.java)

    @Test
    fun `a found server connects with its published address on tap`() {
        mdnsBrowser.services.value = listOf(
            MdnsService(
                mapOf(
                    "server_id" to "living-room",
                    "name" to "Living room server",
                    "base_url" to "http://192.168.1.20:8095",
                    "homeassistant_addon" to "True",
                ),
            ),
        )
        launchApp(composeTestRule)

        val haAddon = Res.string.settings_discovered_server_ha_addon.get()
        composeTestRule.onNodeWithText("192.168.1.20:8095 · $haAddon").assertIsDisplayed()
        composeTestRule.onNodeWithText("Living room server").performClick()

        composeTestRule.onNodeWithText("Authentication").assertIsDisplayed()
        composeTestRule.onNodeWithText("192.168.1.20:8095").assertIsDisplayed()
    }

    @Test
    fun `the Home Assistant port shows the Music Assistant port hint`() {
        launchApp(composeTestRule)
        val hint = Res.string.settings_port_home_assistant_hint.get()

        composeTestRule.onNodeWithText(hint).assertDoesNotExist()
        composeTestRule.onNodeWithText(Res.string.settings_port.get())
            .performScrollTo()
            .performTextReplacement("8123")
        composeTestRule.onNodeWithText(hint).performScrollTo().assertIsDisplayed()
    }
}
