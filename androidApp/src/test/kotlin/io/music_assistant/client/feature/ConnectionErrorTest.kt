package io.music_assistant.client.feature

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.support.FakeServiceClient
import io.music_assistant.client.support.Qualifiers
import io.music_assistant.client.support.launchLoggedInApp
import io.music_assistant.client.support.pages.ConnectPage
import io.music_assistant.client.support.pages.assertOnPage
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
        ConnectPage(composeTestRule)
            .assertOnPage()
            .connectWithError("OH NO!")
    }
}
