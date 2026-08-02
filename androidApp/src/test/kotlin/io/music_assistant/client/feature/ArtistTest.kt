package io.music_assistant.client.feature

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.data.model.server.ServerMediaItem
import io.music_assistant.client.support.FakeServiceClient
import io.music_assistant.client.support.Qualifiers
import io.music_assistant.client.support.ServerMediaItemFixtures
import io.music_assistant.client.support.launchLoggedInApp
import io.music_assistant.client.support.pages.assertMediaNotDisplayed
import io.music_assistant.client.support.rules.createTestRuleChain
import io.music_assistant.client.ui.compose.home.HomeScreenSemantics
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
    fun `show artist albums`() {
        val artist = ServerMediaItemFixtures.artist()
        val album = ServerMediaItemFixtures.album(artist = artist)
        serviceClient.addItems(album)

        launchLoggedInApp(composeTestRule, serviceClient)
            .clickOnMedia(artist, withinTag = HomeScreenSemantics.rowTag("recently_added_artists"))
            .assertMediaDisplayed(album)
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

    /**
     * This is not the ideal behavior - we should show albums from all providers in someway. This
     * will be fixed by https://github.com/music-assistant/mobile-app/issues/801.
     */
    @Test
    fun `shows albums for one provider when artists are matched across providers`() {
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
            .assertMediaDisplayed(album1, provider = provider1.first)
            .assertMediaNotDisplayed(album2, provider = provider2.first)
    }
}
