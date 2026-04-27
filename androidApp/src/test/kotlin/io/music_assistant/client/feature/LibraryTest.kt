package io.music_assistant.client.feature

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.support.FakeServiceClient
import io.music_assistant.client.support.Qualifiers
import io.music_assistant.client.support.ServerMediaItemFixtures
import io.music_assistant.client.support.launchLoggedInApp
import io.music_assistant.client.support.pages.LibraryPage
import io.music_assistant.client.support.pages.MediaItemPage
import io.music_assistant.client.support.pages.assertMediaDisplayed
import io.music_assistant.client.support.pages.clickHome
import io.music_assistant.client.support.pages.clickLibrary
import io.music_assistant.client.support.rules.createTestRuleChain
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent.inject
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = Qualifiers.MEDIUM_PHONE)
class LibraryTest {
    @get:Rule
    val testRuleChain = createTestRuleChain()

    @get:Rule
    val composeTestRule = createComposeRule()

    val serviceClient: FakeServiceClient by inject(ServiceClient::class.java)

    @Test
    fun `can browse albums`() {
        val album1 = ServerMediaItemFixtures.album()
        val album2 = ServerMediaItemFixtures.album()
        serviceClient.addToLibrary(album1, album2)

        launchLoggedInApp(composeTestRule, serviceClient)
            .clickLibrary()
            .clickAlbums()
            .assertMediaDisplayed(album1.name)
            .assertMediaDisplayed(album2.name)
    }

    @Test
    fun `can browse artists`() {
        val artist1 = ServerMediaItemFixtures.artist()
        val artist2 = ServerMediaItemFixtures.artist()
        serviceClient.addToLibrary(artist1, artist2)

        launchLoggedInApp(composeTestRule, serviceClient)
            .clickLibrary()
            .clickArtists()
            .assertMediaDisplayed(artist1.name)
            .assertMediaDisplayed(artist2.name)
            .clickOnMedia(artist1)
    }

    @Test
    fun `library has its own backstack`() {
        val album1 = ServerMediaItemFixtures.album()
        val album2 = ServerMediaItemFixtures.album()
        serviceClient.addToLibrary(album1, album2)

        launchLoggedInApp(composeTestRule, serviceClient)
            .clickOnMedia(album1)
            .clickLibrary()
            .clickAlbums()
            .clickOnMedia(album2)
            .clickHome(
                MediaItemPage(
                album1,
                navigationItem = "Home",
                composeTestRule = composeTestRule,
            ),
            )
            .clickLibrary(
                MediaItemPage(
                album2,
                navigationItem = "Library",
                composeTestRule = composeTestRule,
            ),
            )
    }

    @Test
    fun `clicking library while on it clears backstack`() {
        val album = ServerMediaItemFixtures.album()
        serviceClient.addToLibrary(album)

        launchLoggedInApp(composeTestRule, serviceClient)
            .clickLibrary()
            .clickAlbums()
            .clickOnMedia(album)
            .clickLibrary(LibraryPage("Albums", composeTestRule))
    }
}
