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
        serviceClient.addToLibrary(album1)

        val homePage = launchLoggedInApp(composeTestRule, serviceClient)
            .assertMediaDisplayed(album1.name)

        val album2 = ServerMediaItemFixtures.album()
        serviceClient.addToLibrary(album2)

        homePage.refresh()
            .assertMediaDisplayed(album2.name)
    }

    @Test
    fun `can refresh home shortcuts`() {
        val album = ServerMediaItemFixtures.album()
        serviceClient.addToLibrary(album)

        val homePage = launchLoggedInApp(composeTestRule, serviceClient)

        serviceClient.addShortcut(album)

        homePage.refresh()
            .assertShortcutDisplayed(album)
    }

    @Test
    fun `shows error if data can't be loaded and can recover with refresh`() {
        val album = ServerMediaItemFixtures.album()
        serviceClient.addToLibrary(album)
        val homePage = launchLoggedInApp(composeTestRule, serviceClient)

        serviceClient.setRequestErrors(true)
        homePage.refresh()
            .assertErrorLoadingData()
            .assertMediaNotDisplayed(album.name)

        serviceClient.setRequestErrors(false)
        homePage.refresh()
            .assertMediaDisplayed(album.name)
    }

    @Test
    fun `shows progress while reconnecting`() {
        val album = ServerMediaItemFixtures.album()
        serviceClient.addToLibrary(album)
        val homePage = launchLoggedInApp(composeTestRule, serviceClient)

        serviceClient.setConnected(false)
        homePage.assertProgress()

        serviceClient.setConnected(true)
        homePage.assertMediaDisplayed(album.name)
    }
}
