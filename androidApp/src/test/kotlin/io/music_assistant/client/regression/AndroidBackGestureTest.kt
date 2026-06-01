package io.music_assistant.client.regression

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.support.FakeServiceClient
import io.music_assistant.client.support.Qualifiers
import io.music_assistant.client.support.ServerMediaItemFixtures
import io.music_assistant.client.support.ServerPlayerFixtures
import io.music_assistant.client.support.launchLoggedInApp
import io.music_assistant.client.support.pages.assertOnPage
import io.music_assistant.client.support.pages.expandPlayer
import io.music_assistant.client.support.pages.playMedia
import io.music_assistant.client.support.rules.createTestRuleChain
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent.inject
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = Qualifiers.MEDIUM_PHONE)
class AndroidBackGestureTest {
    @get:Rule
    val testRuleChain = createTestRuleChain()

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val serviceClient: FakeServiceClient by inject(ServiceClient::class.java)

    @Test
    fun `using back while on expanded player closes the player instead of popping nav stack`() {
        val album = ServerMediaItemFixtures.album()
        val track = ServerMediaItemFixtures.track(album = album)
        serviceClient.addToLibrary(track)

        val player = ServerPlayerFixtures.player()
        serviceClient.addPlayers(player)

        val itemDetailsPage = launchLoggedInApp(composeTestRule, serviceClient)
            .clickOnMedia(album)
        itemDetailsPage
            .clickPlay()
            .expandPlayer(player.displayName, playing = true, item = track.name)

        androidBack()
        itemDetailsPage.assertOnPage()
    }

    @Test
    fun `using back while on expanded player doesn't close app`() {
        val album = ServerMediaItemFixtures.album()
        val track = ServerMediaItemFixtures.track(album = album)
        serviceClient.addToLibrary(track)

        val player = ServerPlayerFixtures.player()
        serviceClient.addPlayers(player)

        val homePage = launchLoggedInApp(composeTestRule, serviceClient)
        homePage
            .playMedia(track)
            .expandPlayer(player.displayName, playing = true, item = track.name)

        androidBack()
        homePage.assertOnPage()
    }

    private fun androidBack() {
        composeTestRule.activityRule.scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }
    }
}
