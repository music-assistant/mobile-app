package io.music_assistant.client.feature

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.support.FakeServiceClient
import io.music_assistant.client.support.Qualifiers
import io.music_assistant.client.support.ServerMediaItemFixtures
import io.music_assistant.client.support.launchLoggedInApp
import io.music_assistant.client.support.pages.assertMediaDisplayed
import io.music_assistant.client.support.pages.assertMediaNotDisplayed
import io.music_assistant.client.support.rules.createTestRuleChain
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent.inject
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = Qualifiers.MEDIUM_PHONE)
class HomeTest {
    @get:Rule
    val testRuleChain = createTestRuleChain()

    @get:Rule
    val composeTestRule = createComposeRule()

    val serviceClient: FakeServiceClient by inject(ServiceClient::class.java)

    @Test
    fun `can refresh home recommendations`() {
        val album1 = ServerMediaItemFixtures.album()
        serviceClient.addItems(album1)

        val homePage = launchLoggedInApp(composeTestRule, serviceClient)
            .assertMediaDisplayed(album1)

        val album2 = ServerMediaItemFixtures.album()
        serviceClient.addItems(album2)

        homePage.refresh()
            .assertMediaDisplayed(album2)
    }

    @Test
    fun `can refresh home shortcuts`() {
        val album = ServerMediaItemFixtures.album()
        serviceClient.addItems(album)

        val homePage = launchLoggedInApp(composeTestRule, serviceClient)

        serviceClient.addShortcut(album)

        homePage.refresh()
            .assertShortcutDisplayed(album)
    }

    @Test
    fun `shows error if data can't be loaded and can recover with refresh`() {
        val album = ServerMediaItemFixtures.album()
        serviceClient.addItems(album)
        val homePage = launchLoggedInApp(composeTestRule, serviceClient)

        serviceClient.setRequestErrors(true)
        homePage.refresh()
            .assertErrorLoadingData()
            .assertMediaNotDisplayed(album)

        serviceClient.setRequestErrors(false)
        homePage.refresh()
            .assertMediaDisplayed(album)
    }
}
