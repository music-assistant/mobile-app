package io.music_assistant.client.feature

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.support.FakeServiceClient
import io.music_assistant.client.support.Qualifiers
import io.music_assistant.client.support.ServerMediaItemFixtures
import io.music_assistant.client.support.launchLoggedInApp
import io.music_assistant.client.support.pages.MediaItemPage
import io.music_assistant.client.support.pages.clickHome
import io.music_assistant.client.support.pages.clickSearch
import io.music_assistant.client.support.rules.createTestRuleChain
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent.inject
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = Qualifiers.MEDIUM_PHONE)
class SearchTest {
    @get:Rule
    val testRuleChain = createTestRuleChain()

    @get:Rule
    val composeTestRule = createComposeRule()

    val serviceClient: FakeServiceClient by inject(ServiceClient::class.java)

    @Test
    fun `can navigate to items via search`() {
        val album = ServerMediaItemFixtures.album(name = "The Exploding Onion Conspiracy")
        serviceClient.addToLibrary(album)

        launchLoggedInApp(composeTestRule, serviceClient)
            .clickSearch()
            .search("onion")
            .assertResult(album.name)
            .clickOnMedia(album)
    }

    @Test
    fun `search has its own backstack`() {
        val album1 = ServerMediaItemFixtures.album()
        val album2 = ServerMediaItemFixtures.album()
        serviceClient.addToLibrary(album1, album2)

        launchLoggedInApp(composeTestRule, serviceClient)
            .clickOnMedia(album1)
            .clickSearch()
            .search(album2.name.substring(3))
            .clickOnMedia(album2)
            .clickHome(
                MediaItemPage(
                album1,
                navigationItem = "Home",
                composeTestRule = composeTestRule,
            ),
            )
            .clickSearch(
                MediaItemPage(
                album2,
                navigationItem = "Search",
                composeTestRule = composeTestRule,
            ),
            )
    }
}
