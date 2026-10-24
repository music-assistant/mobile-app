package io.music_assistant.client.feature

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.data.model.server.PlayerState
import io.music_assistant.client.data.model.server.ServerPlayerMedia
import io.music_assistant.client.data.model.server.ServerProviderInstance
import io.music_assistant.client.data.model.server.ServerQueueItem
import io.music_assistant.client.data.model.server.ServerShareItem
import io.music_assistant.client.data.model.server.ServerShareLink
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
    fun `details share appears only inside item overflow`() {
        val album = ServerMediaItemFixtures.album()
        val track = ServerMediaItemFixtures.track(album = album)
        serviceClient.schemaVersion = 100
        serviceClient.shareResponse = ServerShareItem(track.name, track.name, emptyList())
        serviceClient.addItems(track)
        launchLoggedInApp(composeTestRule, serviceClient).clickOnMedia(album)
        composeTestRule.onNodeWithContentDescription("Share").assertDoesNotExist()
        composeTestRule.runOnIdle { assertTrue(serviceClient.shareRequests.isEmpty()) }
        composeTestRule.onNodeWithContentDescription("More").performClick()
        composeTestRule.onNodeWithText("Share").assertIsDisplayed()
        composeTestRule.runOnIdle {
            assertEquals(album.uri, serviceClient.shareRequests.single().args?.get("uri")?.jsonPrimitive?.content)
        }
    }

    @Test
    fun `expanded player shares underlying queue item inside overflow`() {
        val album = ServerMediaItemFixtures.album()
        val track = ServerMediaItemFixtures.track(album = album)
        val player = ServerPlayerFixtures.player()
        serviceClient.schemaVersion = 100
        serviceClient.addProvider(ServerProviderInstance("spotify--account", "My Spotify", "spotify", "music"))
        serviceClient.addProvider(ServerProviderInstance("ytmusic--account", "YouTube Music", "ytmusic", "music"))
        serviceClient.shareResponse = ServerShareItem(
            track.name,
            null,
            listOf(
                ServerShareLink("spotify", "spotify--account", "https://example.com/a"),
                ServerShareLink("ytmusic", "missing-instance", "https://example.com/b"),
            ),
        )
        serviceClient.addItems(track)
        serviceClient.addPlayers(player)
        launchLoggedInApp(composeTestRule, serviceClient)
            .clickOnMedia(album).clickPlay()
            .expandPlayer(player.displayName, playing = true, item = track.name)
        composeTestRule.onNodeWithContentDescription("Share").assertDoesNotExist()
        composeTestRule.runOnIdle { assertTrue(serviceClient.shareRequests.isEmpty()) }
        composeTestRule.onNode(
            withinTag(FloatingBarSemantics.TAG).and(hasContentDescription("More")),
        ).performClick()
        composeTestRule.onNodeWithText("Share").assertIsDisplayed()
        composeTestRule.runOnIdle {
            assertEquals(track.uri, serviceClient.shareRequests.single().args?.get("uri")?.jsonPrimitive?.content)
        }
        composeTestRule.onNodeWithText("Share").performClick()
        composeTestRule.onNodeWithText("Share My Spotify link").assertIsDisplayed()
        composeTestRule.onNodeWithText("Share YouTube Music link").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cancel").performClick()
    }

    @Test
    fun `radio player shares station URI instead of current ICY song`() {
        val station = ServerMediaItemFixtures.radio().copy(uri = "test_provider://radio/station")
        val player = ServerPlayerFixtures.player()
        serviceClient.schemaVersion = 100
        serviceClient.shareResponse = ServerShareItem(station.name, station.name, emptyList())
        val metadata = ServerPlayerMedia(
            uri = "https://example.com/current-song",
            mediaType = "track",
            title = "Different ICY Song",
            artist = "ICY Artist",
            sourceId = player.activeSource,
        )
        serviceClient.addItems(station)
        serviceClient.addToLibrary(station)
        serviceClient.addPlayers(player.copy(state = PlayerState.PLAYING, currentMedia = metadata))
        serviceClient.setCurrentQueueItem(player.activeSource!!, ServerQueueItem("station-queue-item", station))
        launchLoggedInApp(composeTestRule, serviceClient)
        composeTestRule.onNodeWithTag(FloatingBarSemantics.TAG).performClick()
        composeTestRule.waitUntil("ICY song metadata displayed") {
            composeTestRule.onNodeWithContentDescription("Playing Different ICY Song").isDisplayed()
        }
        composeTestRule.onNode(
            withinTag(FloatingBarSemantics.TAG).and(hasContentDescription("More")),
        ).performClick()
        composeTestRule.onNodeWithText("Share").assertIsDisplayed()
        composeTestRule.runOnIdle {
            assertEquals(station.uri, serviceClient.shareRequests.single().args?.get("uri")?.jsonPrimitive?.content)
        }
    }

    @Test
    fun `denied library scope hides sharing without requests`() {
        serviceClient.roleScopes = mapOf("user" to listOf("players.control"))
        assertSharingHidden()
    }

    @Test
    fun `unresolved scopes hide sharing without requests`() {
        serviceClient.roleScopes = emptyMap()
        assertSharingHidden()
    }

    @Test
    fun `item menu provider picker resolves repository instance and domain names`() {
        val album = ServerMediaItemFixtures.album()
        serviceClient.schemaVersion = 100
        serviceClient.addItems(ServerMediaItemFixtures.track(album = album))
        serviceClient.addProvider(ServerProviderInstance("spotify--account", "My Spotify", "spotify", "music"))
        serviceClient.addProvider(ServerProviderInstance("ytmusic--account", "YouTube Music", "ytmusic", "music"))
        serviceClient.shareResponse = ServerShareItem(
            album.name,
            null,
            listOf(
                ServerShareLink("spotify", "spotify--account", "https://example.com/a"),
                ServerShareLink("ytmusic", "missing-instance", "https://example.com/b"),
            ),
        )
        launchLoggedInApp(composeTestRule, serviceClient).clickOnMedia(album)
        composeTestRule.onNodeWithContentDescription("More").performClick()
        composeTestRule.onNodeWithText("Share").performClick()
        composeTestRule.onNodeWithText("Share My Spotify link").assertIsDisplayed()
        composeTestRule.onNodeWithText("Share YouTube Music link").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cancel").performClick()
    }

    private fun assertSharingHidden() {
        val album = ServerMediaItemFixtures.album()
        val track = ServerMediaItemFixtures.track(album = album)
        val player = ServerPlayerFixtures.player()
        serviceClient.schemaVersion = 100
        serviceClient.addItems(track)
        serviceClient.addPlayers(player)
        launchLoggedInApp(composeTestRule, serviceClient).clickOnMedia(album).clickPlay()
            .expandPlayer(player.displayName, playing = true, item = track.name)
        repeat(2) {
            composeTestRule.onNode(
                withinTag(FloatingBarSemantics.TAG).and(hasContentDescription("More")),
            ).performClick()
            composeTestRule.onNodeWithText("Share").assertDoesNotExist()
            composeTestRule.onNodeWithText("Sharing unavailable").assertDoesNotExist()
        }
        composeTestRule.runOnIdle { assertTrue(serviceClient.shareRequests.isEmpty()) }
    }

    @Test
    fun `old server menu has no share entry and makes no request`() {
        val album = ServerMediaItemFixtures.album()
        val track = ServerMediaItemFixtures.track(album = album)
        serviceClient.schemaVersion = 99
        serviceClient.addItems(track)
        launchLoggedInApp(composeTestRule, serviceClient).clickOnMedia(album)
        composeTestRule.onNodeWithContentDescription("More").performClick()
        composeTestRule.onNodeWithText("Share").assertDoesNotExist()
        composeTestRule.onNodeWithText("Sharing unavailable").assertDoesNotExist()
        composeTestRule.runOnIdle { assertTrue(serviceClient.shareRequests.isEmpty()) }
    }
}
