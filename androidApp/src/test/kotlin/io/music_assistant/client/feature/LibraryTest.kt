package io.music_assistant.client.feature

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.support.FakeServiceClient
import io.music_assistant.client.support.Qualifiers
import io.music_assistant.client.support.ServerMediaItemFixtures
import io.music_assistant.client.support.get
import io.music_assistant.client.support.launchLoggedInApp
import io.music_assistant.client.support.pages.ItemPage
import io.music_assistant.client.support.pages.LibraryPage
import io.music_assistant.client.support.pages.assertMediaDisplayed
import io.music_assistant.client.support.pages.assertMediaNotDisplayed
import io.music_assistant.client.support.pages.assertNoItems
import io.music_assistant.client.support.pages.clickHome
import io.music_assistant.client.support.pages.clickLibrary
import io.music_assistant.client.support.pages.enableFilter
import io.music_assistant.client.support.rules.createTestRuleChain
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.filter_favorites
import musicassistantclient.composeapp.generated.resources.nav_home
import musicassistantclient.composeapp.generated.resources.nav_library
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
        serviceClient.addItems(album1, album2)
        serviceClient.addToLibrary(album1, album2)

        launchLoggedInApp(composeTestRule, serviceClient)
            .clickLibrary()
            .clickAlbums()
            .assertMediaDisplayed(album1)
            .assertMediaDisplayed(album2)
    }

    @Test
    fun `can browse artists`() {
        val artist1 = ServerMediaItemFixtures.artist()
        val artist2 = ServerMediaItemFixtures.artist()
        serviceClient.addItems(artist1, artist2)
        serviceClient.addToLibrary(artist1, artist2)

        launchLoggedInApp(composeTestRule, serviceClient)
            .clickLibrary()
            .clickArtists()
            .assertMediaDisplayed(artist1)
            .assertMediaDisplayed(artist2)
    }

    @Test
    fun `can browse playlists`() {
        val playlist1 = ServerMediaItemFixtures.playlist()
        val playlist2 = ServerMediaItemFixtures.playlist()
        serviceClient.addItems(playlist1, playlist2)
        serviceClient.addToLibrary(playlist1, playlist2)

        launchLoggedInApp(composeTestRule, serviceClient)
            .clickLibrary()
            .clickPlaylists()
            .assertMediaDisplayed(playlist1)
            .assertMediaDisplayed(playlist2)
    }

    @Test
    fun `can browse tracks`() {
        val track1 = ServerMediaItemFixtures.track()
        val track2 = ServerMediaItemFixtures.track()
        serviceClient.addItems(track1, track2)
        serviceClient.addToLibrary(track1, track2)

        launchLoggedInApp(composeTestRule, serviceClient)
            .clickLibrary()
            .clickTracks()
            .assertMediaDisplayed(track1)
            .assertMediaDisplayed(track2)
    }

    @Test
    fun `can browse audiobooks`() {
        val audiobook1 = ServerMediaItemFixtures.audiobook()
        val audiobook2 = ServerMediaItemFixtures.audiobook()
        serviceClient.addItems(audiobook1, audiobook2)
        serviceClient.addToLibrary(audiobook1, audiobook2)

        launchLoggedInApp(composeTestRule, serviceClient)
            .clickLibrary()
            .clickAudiobooks()
            .assertMediaDisplayed(audiobook1)
            .assertMediaDisplayed(audiobook2)
    }

    @Test
    fun `can browse podcasts`() {
        val podcast1 = ServerMediaItemFixtures.podcast()
        val podcast2 = ServerMediaItemFixtures.podcast()
        serviceClient.addItems(podcast1, podcast2)
        serviceClient.addToLibrary(podcast1, podcast2)

        launchLoggedInApp(composeTestRule, serviceClient)
            .clickLibrary()
            .clickPodcasts()
            .assertMediaDisplayed(podcast1)
            .assertMediaDisplayed(podcast2)
    }

    @Test
    fun `can browse radio`() {
        val radio1 = ServerMediaItemFixtures.radio()
        val radio2 = ServerMediaItemFixtures.radio()
        serviceClient.addItems(radio1, radio2)
        serviceClient.addToLibrary(radio1, radio2)

        launchLoggedInApp(composeTestRule, serviceClient)
            .clickLibrary()
            .clickRadio()
            .assertMediaDisplayed(radio1)
            .assertMediaDisplayed(radio2)
    }

    @Test
    fun `can browse genres`() {
        val genre1 = ServerMediaItemFixtures.genre()
        val genre2 = ServerMediaItemFixtures.genre()
        serviceClient.addItems(genre1, genre2)
        serviceClient.addToLibrary(genre1, genre2)

        launchLoggedInApp(composeTestRule, serviceClient)
            .clickLibrary()
            .clickGenres()
            .assertMediaDisplayed(genre1)
            .assertMediaDisplayed(genre2)
    }

    @Test
    fun `can filter to only favorites`() {
        val album1 = ServerMediaItemFixtures.album(favorite = false)
        val album2 = ServerMediaItemFixtures.album(favorite = true)
        serviceClient.addItems(album1, album2)
        serviceClient.addToLibrary(album1, album2)

        launchLoggedInApp(composeTestRule, serviceClient)
            .clickLibrary()
            .clickAlbums()
            .enableFilter {
                it.enableSwitch(Res.string.filter_favorites.get())
            }
            .assertMediaNotDisplayed(album1)
            .assertMediaDisplayed(album2)
    }

    @Test
    fun `can search within items`() {
        val album1 = ServerMediaItemFixtures.album(name = "Balloon Trapeze Experience")
        val album2 = ServerMediaItemFixtures.album(name = "Frontal Lobe Annihilation Puzzle")
        serviceClient.addItems(album1, album2)
        serviceClient.addToLibrary(album1, album2)

        launchLoggedInApp(composeTestRule, serviceClient)
            .clickLibrary()
            .clickAlbums()
            .openSearch()
            .search("lobe")
            .assertMediaDisplayed(album2)
            .assertMediaNotDisplayed(album1)
    }

    @Test
    fun `can search outside of library if there are no results`() {
        val album =
            ServerMediaItemFixtures.album(name = "Balloon Trapeze Experience")
        serviceClient.addItems(album)

        launchLoggedInApp(composeTestRule, serviceClient)
            .clickLibrary()
            .clickAlbums()
            .openSearch()
            .search("balloon")
            .assertNoItems()
            .clickSearchEverywhere("balloon")
            .assertMediaDisplayed(album)
    }

    @Test
    fun `library has its own backstack`() {
        val album1 = ServerMediaItemFixtures.album()
        val album2 = ServerMediaItemFixtures.album()
        serviceClient.addItems(album1, album2)
        serviceClient.addToLibrary(album1, album2)

        launchLoggedInApp(composeTestRule, serviceClient)
            .clickOnMedia(album1)
            .clickLibrary()
            .clickAlbums()
            .clickOnMedia(album2)
            .clickHome(
                ItemPage(
                    album1,
                    navigationItem = Res.string.nav_home.get(),
                    composeTestRule = composeTestRule,
                ),
            )
            .clickLibrary(
                ItemPage(
                    album2,
                    navigationItem = Res.string.nav_library.get(),
                    composeTestRule = composeTestRule,
                ),
            )
    }

    @Test
    fun `clicking library while on it clears backstack`() {
        val album = ServerMediaItemFixtures.album()
        serviceClient.addItems(album)
        serviceClient.addToLibrary(album)

        launchLoggedInApp(composeTestRule, serviceClient)
            .clickLibrary()
            .clickAlbums()
            .clickOnMedia(album)
            .clickLibrary(LibraryPage(composeTestRule))
    }
}
