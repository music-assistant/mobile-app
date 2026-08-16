package io.music_assistant.client.feature

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.data.model.server.ServerMediaItem
import io.music_assistant.client.support.FakeServiceClient
import io.music_assistant.client.support.Qualifiers
import io.music_assistant.client.support.ServerMediaItemFixtures
import io.music_assistant.client.support.get
import io.music_assistant.client.support.launchLoggedInApp
import io.music_assistant.client.support.pages.ItemPage
import io.music_assistant.client.support.pages.assertMediaDisplayed
import io.music_assistant.client.support.pages.assertMediaNotDisplayed
import io.music_assistant.client.support.pages.clickHome
import io.music_assistant.client.support.pages.clickSearch
import io.music_assistant.client.support.pages.enableFilter
import io.music_assistant.client.support.rules.createTestRuleChain
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.media_type_albums
import musicassistantclient.composeapp.generated.resources.nav_home
import musicassistantclient.composeapp.generated.resources.nav_search
import musicassistantclient.composeapp.generated.resources.search_in_library_only
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
        serviceClient.addItems(album)

        launchLoggedInApp(composeTestRule, serviceClient)
            .clickSearch()
            .search("onion")
            .assertMediaDisplayed(album)
            .clickOnMedia(album)
    }

    @Test
    fun `can filter search results by media type`() {
        val album = ServerMediaItemFixtures.album(name = "The Exploding Onion Conspiracy")
        val track = ServerMediaItemFixtures.track(name = "Onion Dip")
        serviceClient.addItems(album, track)

        launchLoggedInApp(composeTestRule, serviceClient)
            .clickSearch()
            .search("onion")
            .assertMediaDisplayed(album)
            .assertMediaDisplayed(track)
            .enableFilter {
                it.enableChip(Res.string.media_type_albums.get())
            }
            .assertMediaDisplayed(album)
            .assertMediaNotDisplayed(track)
            .clickOnMedia(album)
    }

    @Test
    fun `can filter search results to library only`() {
        val libraryAlbum = ServerMediaItemFixtures.album(name = "The Exploding Onion Conspiracy")
        val globalAlbum =
            ServerMediaItemFixtures.album(name = "A Tale of Onions")
        serviceClient.addItems(libraryAlbum)
        serviceClient.addToLibrary(libraryAlbum)
        serviceClient.addItems(globalAlbum)

        launchLoggedInApp(composeTestRule, serviceClient)
            .clickSearch()
            .search("onion")
            .enableFilter {
                it.enableSwitch(Res.string.search_in_library_only.get())
            }
            .assertMediaDisplayed(libraryAlbum, provider = ServerMediaItem.LIBRARY_PROVIDER)
            .assertMediaNotDisplayed(globalAlbum)
    }

    @Test
    fun `clicking clear clears results`() {
        val album = ServerMediaItemFixtures.album(name = "Blast from Dastardly Past")
        serviceClient.addItems(album)

        launchLoggedInApp(composeTestRule, serviceClient)
            .clickSearch()
            .search("blast")
            .clearQuery()
            .assertNoResults()
    }

    @Test
    fun `search has its own backstack`() {
        val album1 = ServerMediaItemFixtures.album()
        val album2 = ServerMediaItemFixtures.album()
        serviceClient.addItems(album1, album2)

        launchLoggedInApp(composeTestRule, serviceClient)
            .clickOnMedia(album1)
            .clickSearch()
            .search(album2.name.substring(3))
            .clickOnMedia(album2)
            .clickHome(
                ItemPage(
                    album1,
                    navigationItem = Res.string.nav_home.get(),
                    composeTestRule = composeTestRule,
                ),
            )
            .clickSearch(
                ItemPage(
                    album2,
                    navigationItem = Res.string.nav_search.get(),
                    composeTestRule = composeTestRule,
                ),
            )
    }
}
