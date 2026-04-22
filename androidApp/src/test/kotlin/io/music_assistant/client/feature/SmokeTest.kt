package io.music_assistant.client.feature

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.data.model.client.AppMediaItemFixtures
import io.music_assistant.client.support.FakeServiceClient
import io.music_assistant.client.support.Qualifiers
import io.music_assistant.client.support.launchApp
import io.music_assistant.client.support.pages.assertMediaDisplayed
import io.music_assistant.client.support.rules.createTestRuleChain
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent.inject
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = Qualifiers.MEDIUM_PHONE)
class SmokeTest {

    @get:Rule
    val testRuleChain = createTestRuleChain()

    @get:Rule
    val composeTestRule = createComposeRule()

    val serviceClient: FakeServiceClient by inject(ServiceClient::class.java)

    @Test
    fun `can connect and login to server`() {
        val album1 = AppMediaItemFixtures.album()
        val album2 = AppMediaItemFixtures.album()
        serviceClient.addToLibrary(album1, album2)

        launchApp(composeTestRule)
            .connect()
            .login(serviceClient.username, serviceClient.password)
            .assertMediaDisplayed(album1.name)
            .assertMediaDisplayed(album2.name)
    }
}