package io.music_assistant.client.feature

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.data.model.server.ServerProviderInstance
import io.music_assistant.client.support.FakeServiceClient
import io.music_assistant.client.support.Qualifiers
import io.music_assistant.client.support.ServerMediaItemFixtures
import io.music_assistant.client.support.ServerPlayerFixtures
import io.music_assistant.client.support.launchLoggedInApp
import io.music_assistant.client.support.pages.expandPlayer
import io.music_assistant.client.support.rules.createTestRuleChain
import io.music_assistant.client.support.withinTag
import io.music_assistant.client.ui.compose.home.FloatingBarSemantics
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent.inject
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = Qualifiers.MEDIUM_PHONE)
class SharingTest {
    @get:Rule
    val testRuleChain = createTestRuleChain()

    @get:Rule
    val composeTestRule = createComposeRule()

    private val serviceClient: FakeServiceClient by inject(ServiceClient::class.java)

    @Test
    fun `details share loads destinations on selection`() {
        val album = ServerMediaItemFixtures.album()
        serviceClient.addItems(ServerMediaItemFixtures.track(album = album))
        enableSharing()

        launchLoggedInApp(composeTestRule, serviceClient).clickOnMedia(album)
        composeTestRule.onNodeWithContentDescription("Share").assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription("More").performClick()
        composeTestRule.onNodeWithText("Share").assertIsDisplayed()
        composeTestRule.runOnIdle { assertTrue(serviceClient.shareRequests.isEmpty()) }
        composeTestRule.onNodeWithText("Share").performClick()

        val destination = composeTestRule.onNode(hasText("Music Assistant").and(hasAnyAncestor(isDialog())))
        destination.assertIsDisplayed()
        assertRequested(album.uri)
        destination.performClick()
        assertRequested(album.uri, "share/item", "share/generate")
    }

    @Test
    fun `expanded player loads destinations for the underlying queue track`() {
        val album = ServerMediaItemFixtures.album()
        val track = ServerMediaItemFixtures.track(album = album)
        val player = ServerPlayerFixtures.player()
        serviceClient.addItems(track)
        serviceClient.addPlayers(player)
        enableSharing()

        launchLoggedInApp(composeTestRule, serviceClient)
            .clickOnMedia(album).clickPlay()
            .expandPlayer(player.displayName, playing = true, item = track.name)
        composeTestRule.onNode(
            withinTag(FloatingBarSemantics.TAG).and(hasContentDescription("More")),
        ).performClick()
        composeTestRule.onNodeWithText("Share").performClick()

        assertRequested(track.uri)
    }

    @Test
    fun `share is hidden without the plugin`() {
        serviceClient.schemaVersion = 122
        assertSharingHidden()
    }

    @Test
    fun `share is hidden before schema 122`() {
        enableSharing()
        serviceClient.schemaVersion = 121
        assertSharingHidden()
    }

    private fun enableSharing() {
        serviceClient.schemaVersion = 122
        serviceClient.generatedShareLink = "https://share.music-assistant.io/#1example"
        serviceClient.addProvider(ServerProviderInstance("share_provider", "Share Links", "share_provider", "plugin"))
    }

    private fun assertSharingHidden() {
        val album = ServerMediaItemFixtures.album()
        serviceClient.addItems(ServerMediaItemFixtures.track(album = album))
        launchLoggedInApp(composeTestRule, serviceClient).clickOnMedia(album)
        composeTestRule.onNodeWithContentDescription("More").performClick()
        composeTestRule.onNodeWithText("Share").assertDoesNotExist()
        composeTestRule.runOnIdle { assertTrue(serviceClient.shareRequests.isEmpty()) }
    }

    private fun assertRequested(uri: String?, vararg commands: String = arrayOf("share/item")) {
        composeTestRule.runOnIdle {
            assertEquals(commands.toList(), serviceClient.shareRequests.map { it.command })
            assertTrue(serviceClient.shareRequests.all { it.args?.get("uri")?.jsonPrimitive?.content == uri })
        }
    }
}
