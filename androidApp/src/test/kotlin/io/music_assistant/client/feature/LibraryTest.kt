package io.music_assistant.client.feature

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.support.FakeServiceClient
import io.music_assistant.client.support.Qualifiers
import io.music_assistant.client.support.ServerMediaItemFixtures
import io.music_assistant.client.support.get
import io.music_assistant.client.support.launchLoggedInApp
import io.music_assistant.client.support.pages.assertMediaDisplayed
import io.music_assistant.client.support.pages.assertMediaNotDisplayed
import io.music_assistant.client.support.pages.assertNoItems
import io.music_assistant.client.support.pages.clickAlbums
import io.music_assistant.client.support.pages.clickArtists
import io.music_assistant.client.support.pages.clickAudiobooks
import io.music_assistant.client.support.pages.clickGenres
import io.music_assistant.client.support.pages.clickPlaylists
import io.music_assistant.client.support.pages.clickPodcasts
import io.music_assistant.client.support.pages.clickRadio
import io.music_assistant.client.support.pages.clickTracks
import io.music_assistant.client.support.pages.enableFilter
import io.music_assistant.client.support.rules.createTestRuleChain
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.artist_type_authors
import musicassistantclient.composeapp.generated.resources.filter_favorites
import musicassistantclient.composeapp.generated.resources.media_type_podcasts
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
            .clickAlbums()
            .enableFilter {
                it.enableSwitch(Res.string.filter_favorites.get())
            }
            .assertMediaNotDisplayed(album1)
            .assertMediaDisplayed(album2)
    }

    @Test
    fun `can list the authors instead of the audiobooks`() {
        val audiobook = ServerMediaItemFixtures.audiobook()
        val author = ServerMediaItemFixtures.artist(artistType = "author")
        val singer = ServerMediaItemFixtures.artist()
        serviceClient.addItems(audiobook, author, singer)
        serviceClient.addToLibrary(audiobook, author, singer)

        launchLoggedInApp(composeTestRule, serviceClient)
            .clickAudiobooks()
            .assertMediaDisplayed(audiobook)
            .enableFilter {
                it.enableChip(Res.string.artist_type_authors.get())
            }
            .assertMediaDisplayed(author)
            .assertMediaNotDisplayed(singer)
            .assertMediaNotDisplayed(audiobook)
    }

    @Test
    fun `can filter genres by content type`() {
        val musicGenre = ServerMediaItemFixtures.genre()
        val podcastGenre = ServerMediaItemFixtures.genre()
        serviceClient.addItems(musicGenre, podcastGenre)
        serviceClient.addToLibrary(musicGenre, podcastGenre)
        serviceClient.setGenreContentType(podcastGenre, "podcast")

        launchLoggedInApp(composeTestRule, serviceClient)
            .clickGenres()
            .assertMediaDisplayed(musicGenre)
            .assertMediaDisplayed(podcastGenre)
            .enableFilter {
                it.enableChip(Res.string.media_type_podcasts.get())
            }
            .assertMediaDisplayed(podcastGenre)
            .assertMediaNotDisplayed(musicGenre)
    }

    @Test
    fun `can search within items`() {
        val album1 = ServerMediaItemFixtures.album(name = "Balloon Trapeze Experience")
        val album2 = ServerMediaItemFixtures.album(name = "Frontal Lobe Annihilation Puzzle")
        serviceClient.addItems(album1, album2)
        serviceClient.addToLibrary(album1, album2)

        launchLoggedInApp(composeTestRule, serviceClient)
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
            .clickAlbums()
            .openSearch()
            .search("balloon")
            .assertNoItems()
            .clickSearchEverywhere("balloon")
            .assertMediaDisplayed(album)
    }
}
