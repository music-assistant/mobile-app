package io.music_assistant.client.feature

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.support.FakeServiceClient
import io.music_assistant.client.support.Qualifiers
import io.music_assistant.client.support.ServerMediaItemFixtures
import io.music_assistant.client.support.get
import io.music_assistant.client.support.launchLoggedInApp
import io.music_assistant.client.support.pages.ConnectPage
import io.music_assistant.client.support.pages.HomePage
import io.music_assistant.client.support.pages.assertNoNetworkBanner
import io.music_assistant.client.support.pages.assertOnPage
import io.music_assistant.client.support.pages.assertReconnectingBanner
import io.music_assistant.client.support.pages.clickBack
import io.music_assistant.client.support.pages.clickOnMedia
import io.music_assistant.client.support.pages.clickSettings
import io.music_assistant.client.support.rules.createTestRuleChain
import io.music_assistant.client.ui.compose.home.HomeScreenSemantics
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.nav_discover
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent.inject
import org.robolectric.annotation.Config
import kotlin.getValue

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = Qualifiers.MEDIUM_PHONE)
class ConnectionErrorTest {
    @get:Rule
    val testRuleChain = createTestRuleChain()

    @get:Rule
    val composeTestRule = createComposeRule()

    val serviceClient: FakeServiceClient by inject(ServiceClient::class.java)

    @Test
    fun `shows error when connection fails`() {
        launchLoggedInApp(composeTestRule, serviceClient)

        serviceClient.setConnectionError(Exception("OH NO!"))
        val connectPage = ConnectPage(composeTestRule, savedCredentials = true)
            .assertOnPage()
            .connectWithError("OH NO!")

        serviceClient.setConnectionError(null)
        connectPage.connect(HomePage(composeTestRule))
    }

    @Test
    fun `shows reconnecting banner while reconnecting`() {
        val homePage = launchLoggedInApp(composeTestRule, serviceClient)

        serviceClient.setReconnecting(true)
        homePage.assertReconnectingBanner(true)

        serviceClient.setReconnecting(false)
        homePage.assertReconnectingBanner(false)
    }

    @Test
    fun `maintains the navigation stack after reconnecting`() {
        val artist = ServerMediaItemFixtures.artist()
        val album = ServerMediaItemFixtures.album(artist = artist)
        serviceClient.addItems(album)

        val homePage = launchLoggedInApp(composeTestRule, serviceClient)
        val artistPage = homePage
            .clickOnMedia(artist, withinTag = HomeScreenSemantics.rowTag("recently_added_artists"))
        val albumPage = artistPage
            .clickOnMedia(album, Res.string.nav_discover.get())

        serviceClient.setReconnecting(true)
        albumPage.assertReconnectingBanner(true)

        serviceClient.setReconnecting(false)
        albumPage.assertOnPage()
            .clickBack(artistPage)
    }

    @Test
    fun `shows no network banner when network is unavailable`() {
        val homePage = launchLoggedInApp(composeTestRule, serviceClient)

        serviceClient.setNetworkAvailable(false)
        homePage.assertNoNetworkBanner(true)

        serviceClient.setNetworkAvailable(true)
        homePage.assertNoNetworkBanner(false)
    }

    @Test
    fun `asks to log in when the address now hosts a different server`() {
        serviceClient.serverId = "1"
        val connectPage = launchLoggedInApp(composeTestRule, serviceClient)
            .clickSettings()
            .disconnect()

        // Tokens are keyed by server id, so server "2" has none: the login form
        // opens instead of the connection failing.
        serviceClient.serverId = "2"
        connectPage.connect()
    }
}
