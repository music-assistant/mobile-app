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
import io.music_assistant.client.support.pages.assertMediaDisplayed
import io.music_assistant.client.support.pages.assertMediaNotDisplayed
import io.music_assistant.client.support.rules.createTestRuleChain
import io.music_assistant.client.ui.compose.home.HomeScreenSemantics
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.artist_section_all
import musicassistantclient.composeapp.generated.resources.artist_section_in_library
import musicassistantclient.composeapp.generated.resources.artist_section_top
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent.inject
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = Qualifiers.MEDIUM_PHONE)
class ArtistTest {
    @get:Rule
    val testRuleChain = createTestRuleChain()

    @get:Rule
    val composeTestRule = createComposeRule()

    private val serviceClient: FakeServiceClient by inject(ServiceClient::class.java)

    @Test
    fun `shows artist albums`() {
        val artist = ServerMediaItemFixtures.artist()
        val album = ServerMediaItemFixtures.album(artist = artist)
        serviceClient.addItems(album)

        launchLoggedInApp(composeTestRule, serviceClient)
            .clickOnMedia(artist, withinTag = HomeScreenSemantics.rowTag("recently_added_artists"))
            .assertMediaDisplayed(album)
    }

    @Test
    fun `can view all artist albums`() {
        val artist = ServerMediaItemFixtures.artist()
        val album = ServerMediaItemFixtures.album(artist = artist)
        serviceClient.addItems(album)

        launchLoggedInApp(composeTestRule, serviceClient)
            .clickOnMedia(artist, withinTag = HomeScreenSemantics.rowTag("recently_added_artists"))
            .clickViewAll(Res.string.artist_section_all.get())
            .assertMediaDisplayed(album)
    }

    @Test
    fun `shows artist top tracks`() {
        val artist = ServerMediaItemFixtures.artist()
        val track1 = ServerMediaItemFixtures.track(artists = listOf(artist))
        val track2 = ServerMediaItemFixtures.track(artists = listOf(artist))
        serviceClient.addItems(track1, track2)
        serviceClient.setTopTracks(artist, track2)

        launchLoggedInApp(composeTestRule, serviceClient)
            .clickOnMedia(artist, withinTag = HomeScreenSemantics.rowTag("recently_added_artists"))
            .assertMediaDisplayed(track2)
            .assertMediaNotDisplayed(track1)
    }

    @Test
    fun `can view all artist top tracks`() {
        val artist = ServerMediaItemFixtures.artist()
        val track = ServerMediaItemFixtures.track(artists = listOf(artist))
        serviceClient.addItems(track)
        serviceClient.setTopTracks(artist, track)

        launchLoggedInApp(composeTestRule, serviceClient)
            .clickOnMedia(artist, withinTag = HomeScreenSemantics.rowTag("recently_added_artists"))
            .clickViewAll(Res.string.artist_section_top.get())
            .assertMediaDisplayed(track)
    }

    @Test
    fun `shows in library and all artist albums on provider`() {
        val artist = ServerMediaItemFixtures.artist()
        val libraryAlbum = ServerMediaItemFixtures.album(artist = artist)
        val nonLibraryAlbum = ServerMediaItemFixtures.album(artist = artist)
        serviceClient.addItems(libraryAlbum, nonLibraryAlbum)
        serviceClient.addToLibrary(libraryAlbum)

        launchLoggedInApp(composeTestRule, serviceClient)
            .clickOnMedia(artist, withinTag = HomeScreenSemantics.rowTag("recently_added_artists"))
            .assertMediaDisplayed(libraryAlbum, provider = ServerMediaItem.LIBRARY_PROVIDER)
            .assertMediaDisplayed(libraryAlbum)
            .assertMediaDisplayed(nonLibraryAlbum)
    }

    @Test
    fun `can view all library albums`() {
        val artist = ServerMediaItemFixtures.artist()
        val album = ServerMediaItemFixtures.album(artist = artist)
        serviceClient.addItems(album)
        serviceClient.addToLibrary(album)

        launchLoggedInApp(composeTestRule, serviceClient)
            .clickOnMedia(artist, withinTag = HomeScreenSemantics.rowTag("recently_added_artists"))
            .clickViewAll(Res.string.artist_section_in_library.get())
            .assertMediaDisplayed(album, provider = ServerMediaItem.LIBRARY_PROVIDER)
    }

    @Test
    fun `can switch providers to see albums from them when artists are matched across providers`() {
        val provider1 = ServerMediaItemFixtures.provider(domain = "domain1", instance = "instance1")
        val provider2 = ServerMediaItemFixtures.provider(domain = "domain2", instance = "instance2")
        val artist1 = ServerMediaItemFixtures.artist(provider = provider1)
        val artist2 = ServerMediaItemFixtures.artist(provider = provider2)
        val album1 = ServerMediaItemFixtures.album(artist = artist1, provider = provider1)
        val album2 = ServerMediaItemFixtures.album(artist = artist2, provider = provider2)

        serviceClient.addItems(album1, album2)
        serviceClient.addToLibrary(artist1)
        serviceClient.matchItem(artist1, artist2)

        launchLoggedInApp(composeTestRule, serviceClient)
            .clickOnMedia(artist1, withinTag = HomeScreenSemantics.rowTag("recently_added_artists"))
            .assertMediaDisplayed(album1, provider = provider1.domain)
            .assertMediaNotDisplayed(album2, provider = provider2.domain)
            .switchProvider(Res.string.artist_section_all.get(), provider1.domain, provider2.domain)
            .assertMediaNotDisplayed(album1, provider = provider1.domain)
            .assertMediaDisplayed(album2, provider = provider2.domain)
    }

    @Test
    fun `can switch providers to see top tracks from them when artists are matched across providers`() {
        val provider1 = ServerMediaItemFixtures.provider(domain = "domain1", instance = "instance1")
        val provider2 = ServerMediaItemFixtures.provider(domain = "domain2", instance = "instance2")
        val artist1 = ServerMediaItemFixtures.artist(provider = provider1)
        val artist2 = ServerMediaItemFixtures.artist(provider = provider2)
        val track1 = ServerMediaItemFixtures.track(artists = listOf(artist1), provider = provider1)
        val track2 = ServerMediaItemFixtures.track(artists = listOf(artist2), provider = provider2)

        serviceClient.addItems(track1, track2)
        serviceClient.setTopTracks(artist1, track1)
        serviceClient.setTopTracks(artist2, track2)
        serviceClient.addToLibrary(artist1)
        serviceClient.matchItem(artist1, artist2)

        launchLoggedInApp(composeTestRule, serviceClient)
            .clickOnMedia(artist1, withinTag = HomeScreenSemantics.rowTag("recently_added_artists"))
            .assertMediaDisplayed(track1, provider = provider1.domain)
            .assertMediaNotDisplayed(track2, provider = provider2.domain)
            .switchProvider(Res.string.artist_section_top.get(), provider1.domain, provider2.domain)
            .assertMediaNotDisplayed(track1, provider = provider1.domain)
            .assertMediaDisplayed(track2, provider = provider2.domain)
    }
}
