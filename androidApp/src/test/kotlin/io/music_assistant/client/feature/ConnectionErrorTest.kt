package io.music_assistant.client.feature

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.support.FakeServiceClient
import io.music_assistant.client.support.Qualifiers
import io.music_assistant.client.support.launchLoggedInApp
import io.music_assistant.client.support.pages.ConnectPage
import io.music_assistant.client.support.pages.assertNoNetworkBanner
import io.music_assistant.client.support.pages.assertOnPage
import io.music_assistant.client.support.pages.assertReconnectingBanner
import io.music_assistant.client.support.rules.createTestRuleChain
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
        val connectPage = ConnectPage(composeTestRule)
            .assertOnPage()
            .connectWithError("OH NO!")

        serviceClient.setConnectionError(null)
        connectPage.connect()
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
    fun `shows no network banner when network is unavailable`() {
        val homePage = launchLoggedInApp(composeTestRule, serviceClient)

        serviceClient.setNetworkAvailable(false)
        homePage.assertNoNetworkBanner(true)

        serviceClient.setNetworkAvailable(true)
        homePage.assertNoNetworkBanner(false)
    }
}
