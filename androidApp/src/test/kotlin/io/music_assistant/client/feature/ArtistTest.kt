package io.music_assistant.client.feature

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.data.model.server.SERVER_2_11_SCHEMA
import io.music_assistant.client.data.model.server.ServerMediaItem
import io.music_assistant.client.support.FakeServiceClient
import io.music_assistant.client.support.Qualifiers
import io.music_assistant.client.support.ServerMediaItemFixtures
import io.music_assistant.client.support.get
import io.music_assistant.client.support.launchLoggedInApp
import io.music_assistant.client.support.pages.assertMediaDisplayed
import io.music_assistant.client.support.pages.assertMediaNotDisplayed
import io.music_assistant.client.support.pages.clickAudiobooks
import io.music_assistant.client.support.pages.clickOnMedia
import io.music_assistant.client.support.pages.enableFilter
import io.music_assistant.client.support.rules.createTestRuleChain
import io.music_assistant.client.ui.compose.home.HomeScreenSemantics
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.artist_section_albums
import musicassistantclient.composeapp.generated.resources.artist_section_all_audiobooks
import musicassistantclient.composeapp.generated.resources.artist_section_appears_on
import musicassistantclient.composeapp.generated.resources.artist_section_discography
import musicassistantclient.composeapp.generated.resources.artist_section_in_library
import musicassistantclient.composeapp.generated.resources.artist_section_singles_eps
import musicassistantclient.composeapp.generated.resources.artist_section_top
import musicassistantclient.composeapp.generated.resources.artist_source_all
import musicassistantclient.composeapp.generated.resources.artist_source_library
import musicassistantclient.composeapp.generated.resources.artist_type_authors
import musicassistantclient.composeapp.generated.resources.artist_type_narrators
import musicassistantclient.composeapp.generated.resources.nav_discover
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
    fun `shows the library and all audiobooks of an author and no top tracks`() {
        val author = ServerMediaItemFixtures.artist(artistType = "author")
        val libraryBook = ServerMediaItemFixtures.audiobook()
        val providerBook = ServerMediaItemFixtures.audiobook()
        serviceClient.addItems(author, libraryBook, providerBook)
        serviceClient.addToLibrary(author, libraryBook)
        serviceClient.setArtistAudiobooks(author, libraryBook, providerBook)

        // The library book is in both rows, so only the provider book is a unique node here.
        launchLoggedInApp(composeTestRule, serviceClient)
            .clickAudiobooks()
            .enableFilter { it.enableChip(Res.string.artist_type_authors.get()) }
            .clickOnMedia(author)
            .assertRowShown(Res.string.artist_section_in_library.get(), shown = true)
            .assertRowShown(Res.string.artist_section_all_audiobooks.get(), shown = true)
            .assertMediaDisplayed(providerBook)
            .assertRowShown(Res.string.artist_section_top.get(), shown = false)
            .assertRowShown(Res.string.artist_section_albums.get(), shown = false)
    }

    @Test
    fun `can view the library audiobooks of an author`() {
        val author = ServerMediaItemFixtures.artist(artistType = "author")
        val libraryBook = ServerMediaItemFixtures.audiobook()
        val providerBook = ServerMediaItemFixtures.audiobook()
        serviceClient.addItems(author, libraryBook, providerBook)
        serviceClient.addToLibrary(author, libraryBook)
        serviceClient.setArtistAudiobooks(author, libraryBook, providerBook)

        launchLoggedInApp(composeTestRule, serviceClient)
            .clickAudiobooks()
            .enableFilter { it.enableChip(Res.string.artist_type_authors.get()) }
            .clickOnMedia(author)
            .clickViewAll(Res.string.artist_section_in_library.get())
            .assertMediaDisplayed(libraryBook)
            .assertMediaNotDisplayed(providerBook)
    }

    @Test
    fun `can view all audiobooks of an author`() {
        val author = ServerMediaItemFixtures.artist(artistType = "narrator")
        val book = ServerMediaItemFixtures.audiobook()
        serviceClient.addItems(author, book)
        serviceClient.addToLibrary(author)
        serviceClient.setArtistAudiobooks(author, book)

        launchLoggedInApp(composeTestRule, serviceClient)
            .clickAudiobooks()
            .enableFilter { it.enableChip(Res.string.artist_type_narrators.get()) }
            .clickOnMedia(author)
            .clickViewAll(Res.string.artist_section_all_audiobooks.get())
            .assertMediaDisplayed(book)
    }

    @Test
    fun `can view all artist albums`() {
        val artist = ServerMediaItemFixtures.artist()
        val album = ServerMediaItemFixtures.album(artist = artist)
        serviceClient.addItems(album)

        launchLoggedInApp(composeTestRule, serviceClient)
            .clickOnMedia(artist, withinTag = HomeScreenSemantics.rowTag("recently_added_artists"))
            .clickViewAll(Res.string.artist_section_albums.get())
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
    fun `shows the library albums first and the provider albums on a switch`() {
        val artist = ServerMediaItemFixtures.artist()
        val libraryAlbum = ServerMediaItemFixtures.album(artist = artist)
        val nonLibraryAlbum = ServerMediaItemFixtures.album(artist = artist)
        serviceClient.addItems(libraryAlbum, nonLibraryAlbum)
        serviceClient.addToLibrary(libraryAlbum)

        launchLoggedInApp(composeTestRule, serviceClient)
            .clickOnMedia(artist, withinTag = HomeScreenSemantics.rowTag("recently_added_artists"))
            .assertMediaDisplayed(libraryAlbum, provider = ServerMediaItem.LIBRARY_PROVIDER)
            .assertMediaNotDisplayed(nonLibraryAlbum)
            .switchProvider(
                Res.string.artist_section_albums.get(),
                Res.string.artist_source_library.get(),
                nonLibraryAlbum.provider,
            )
            .assertMediaDisplayed(libraryAlbum)
            .assertMediaDisplayed(nonLibraryAlbum)
    }

    @Test
    fun `shows singles and EPs in their own row`() {
        val artist = ServerMediaItemFixtures.artist()
        val album = ServerMediaItemFixtures.album(artist = artist, albumType = "album")
        val single = ServerMediaItemFixtures.album(artist = artist, albumType = "single")
        val ep = ServerMediaItemFixtures.album(artist = artist, albumType = "ep")
        serviceClient.addItems(album, single, ep)

        launchLoggedInApp(composeTestRule, serviceClient)
            .clickOnMedia(artist, withinTag = HomeScreenSemantics.rowTag("recently_added_artists"))
            .assertRowShown(Res.string.artist_section_singles_eps.get(), shown = true)
            .clickViewAll(Res.string.artist_section_singles_eps.get())
            .assertMediaDisplayed(single)
            .assertMediaDisplayed(ep)
            .assertMediaNotDisplayed(album)
    }

    @Test
    fun `shows the albums an artist appears on and the discography on server 2_11`() {
        serviceClient.schemaVersion = SERVER_2_11_SCHEMA
        val artist = ServerMediaItemFixtures.artist()
        val otherAlbum = ServerMediaItemFixtures.album()
        val release = ServerMediaItemFixtures.musicBrainzRelease(artist = artist)
        serviceClient.addItems(artist, otherAlbum)
        serviceClient.addToLibrary(artist)
        serviceClient.setAppearsOn(artist, otherAlbum)
        serviceClient.setDiscography(artist, release)

        launchLoggedInApp(composeTestRule, serviceClient)
            .clickOnMedia(artist, withinTag = HomeScreenSemantics.rowTag("recently_added_artists"))
            .assertRowShown(Res.string.artist_section_appears_on.get(), shown = true)
            .assertMediaDisplayed(otherAlbum)
            .assertRowShown(Res.string.artist_section_discography.get(), shown = true)
            .assertMediaDisplayed(release)
    }

    @Test
    fun `does not show appears on and discography before server 2_11`() {
        val artist = ServerMediaItemFixtures.artist()
        val otherAlbum = ServerMediaItemFixtures.album()
        serviceClient.addItems(artist, otherAlbum)
        serviceClient.addToLibrary(artist)
        serviceClient.setAppearsOn(artist, otherAlbum)
        serviceClient.setDiscography(artist, ServerMediaItemFixtures.musicBrainzRelease(artist = artist))

        launchLoggedInApp(composeTestRule, serviceClient)
            .clickOnMedia(artist, withinTag = HomeScreenSemantics.rowTag("recently_added_artists"))
            .assertRowShown(Res.string.artist_section_appears_on.get(), shown = false)
            .assertRowShown(Res.string.artist_section_discography.get(), shown = false)
    }

    @Test
    fun `opens the album that a discography release resolves to`() {
        serviceClient.schemaVersion = SERVER_2_11_SCHEMA
        val artist = ServerMediaItemFixtures.artist()
        val release = ServerMediaItemFixtures.musicBrainzRelease(artist = artist)
        // Another artist keeps the resolved album out of the Albums row, so its name is unique.
        val resolved = ServerMediaItemFixtures.album(name = release.name)
        serviceClient.addItems(artist, resolved)
        serviceClient.addToLibrary(artist)
        serviceClient.setDiscography(artist, release)
        serviceClient.setMusicBrainzRelease(release.itemId, resolved)

        // The album page asserts its play button: the release itself has nothing to play.
        launchLoggedInApp(composeTestRule, serviceClient)
            .clickOnMedia(artist, withinTag = HomeScreenSemantics.rowTag("recently_added_artists"))
            .assertMediaDisplayed(release) // scrolls the row into view
            .clickOnMedia(release, Res.string.nav_discover.get())
    }

    @Test
    fun `can view all library albums`() {
        val artist = ServerMediaItemFixtures.artist()
        val album = ServerMediaItemFixtures.album(artist = artist)
        serviceClient.addItems(album)
        serviceClient.addToLibrary(album)

        launchLoggedInApp(composeTestRule, serviceClient)
            .clickOnMedia(artist, withinTag = HomeScreenSemantics.rowTag("recently_added_artists"))
            .clickViewAll(Res.string.artist_section_albums.get())
            .assertMediaDisplayed(album, provider = ServerMediaItem.LIBRARY_PROVIDER)
    }

    @Test
    fun `can switch providers to see albums from them when artists are matched across providers`() {
        val provider1 = ServerMediaItemFixtures.provider(
            domain = "domain1",
            instance = "instance1",
            name = "Instance 1",
        )
        val provider2 = ServerMediaItemFixtures.provider(
            domain = "domain2",
            instance = "instance2",
            name = "Instance 2",
        )
        serviceClient.addProvider(provider1)
        serviceClient.addProvider(provider2)

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
            .switchProvider(Res.string.artist_section_albums.get(), provider1.name, provider2.name)
            .assertMediaNotDisplayed(album1, provider = provider1.domain)
            .assertMediaDisplayed(album2, provider = provider2.domain)
    }

    @Test
    fun `can switch providers to see top tracks from them when artists are matched across providers`() {
        val provider1 = ServerMediaItemFixtures.provider(
            domain = "domain1",
            instance = "instance1",
            name = "Instance 1",
            supportedFeatures = listOf(ARTIST_TOPTRACKS_FEATURE),
        )
        val provider2 = ServerMediaItemFixtures.provider(
            domain = "domain2",
            instance = "instance2",
            name = "Instance 2",
            supportedFeatures = listOf(ARTIST_TOPTRACKS_FEATURE),
        )
        serviceClient.addProvider(provider1)
        serviceClient.addProvider(provider2)

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
            .assertMediaDisplayed(track2, provider = provider2.domain)
            .switchProvider(Res.string.artist_section_top.get(), Res.string.artist_source_all.get(), provider2.name)
            .assertMediaNotDisplayed(track1, provider = provider1.domain)
            .assertMediaDisplayed(track2, provider = provider2.domain)
    }

    @Test
    fun `does not offer a provider without top tracks for the artist`() {
        val provider1 = ServerMediaItemFixtures.provider(
            domain = "domain1",
            instance = "instance1",
            name = "Instance 1",
            supportedFeatures = listOf(ARTIST_TOPTRACKS_FEATURE),
        )
        val provider2 = ServerMediaItemFixtures.provider(
            domain = "domain2",
            instance = "instance2",
            name = "Instance 2",
            supportedFeatures = listOf(ARTIST_TOPTRACKS_FEATURE),
        )
        serviceClient.addProvider(provider1)
        serviceClient.addProvider(provider2)

        val artist1 = ServerMediaItemFixtures.artist(provider = provider1)
        val artist2 = ServerMediaItemFixtures.artist(provider = provider2)
        val track1 = ServerMediaItemFixtures.track(artists = listOf(artist1), provider = provider1)

        serviceClient.addItems(track1, artist2)
        serviceClient.setTopTracks(artist1, track1)
        serviceClient.addToLibrary(artist1)
        serviceClient.matchItem(artist1, artist2)

        launchLoggedInApp(composeTestRule, serviceClient)
            .clickOnMedia(artist1, withinTag = HomeScreenSemantics.rowTag("recently_added_artists"))
            .assertMediaDisplayed(track1, provider = provider1.domain)
            .assertProviderNotOffered(
                Res.string.artist_section_top.get(),
                Res.string.artist_source_all.get(),
                provider2.name,
            )
    }

    @Test
    fun `does not show singles and EPs where there is none`() {
        val artist = ServerMediaItemFixtures.artist()
        val album = ServerMediaItemFixtures.album(artist = artist, albumType = "album")
        serviceClient.addItems(album)

        launchLoggedInApp(composeTestRule, serviceClient)
            .clickOnMedia(artist, withinTag = HomeScreenSemantics.rowTag("recently_added_artists"))
            .assertMediaDisplayed(album)
            .assertRowShown(Res.string.artist_section_singles_eps.get(), false)
    }

    @Test
    fun `does not show albums where there is none`() {
        val artist = ServerMediaItemFixtures.artist()
        serviceClient.addItems(artist)
        serviceClient.addToLibrary(artist)

        launchLoggedInApp(composeTestRule, serviceClient)
            .clickOnMedia(artist, withinTag = HomeScreenSemantics.rowTag("recently_added_artists"))
            .assertRowShown(Res.string.artist_section_albums.get(), false)
    }

    @Test
    fun `does not show top tracks where there is none`() {
        val artist = ServerMediaItemFixtures.artist()
        serviceClient.addItems(artist)
        serviceClient.addToLibrary(artist)

        launchLoggedInApp(composeTestRule, serviceClient)
            .clickOnMedia(artist, withinTag = HomeScreenSemantics.rowTag("recently_added_artists"))
            .assertRowShown(Res.string.artist_section_top.get(), false)
    }

    @Test
    fun `clicking refresh updates providers`() {
        val provider1 = ServerMediaItemFixtures.provider(
            domain = "domain1",
            instance = "instance1",
            name = "Instance 1",
        )
        val provider2 = ServerMediaItemFixtures.provider(
            domain = "domain2",
            instance = "instance2",
            name = "Instance 2",
        )
        serviceClient.addProvider(provider1)
        serviceClient.addProvider(provider2)

        val artist1 = ServerMediaItemFixtures.artist(provider = provider1)
        val album1 = ServerMediaItemFixtures.album(artist = artist1, provider = provider1)
        serviceClient.addItems(album1)
        serviceClient.addToLibrary(artist1)

        val artistPage = launchLoggedInApp(composeTestRule, serviceClient)
            .clickOnMedia(artist1, withinTag = HomeScreenSemantics.rowTag("recently_added_artists"))
            .assertMediaDisplayed(album1, provider = provider1.domain)

        val artist2 = ServerMediaItemFixtures.artist(provider = provider2)
        val album2 = ServerMediaItemFixtures.album(artist = artist2, provider = provider2)
        serviceClient.addItems(album2)
        serviceClient.matchItem(artist1, artist2)

        artistPage.refreshItem()
            .switchProvider(Res.string.artist_section_albums.get(), provider1.name, provider2.name)
            .assertMediaNotDisplayed(album1, provider = provider1.domain)
            .assertMediaDisplayed(album2, provider = provider2.domain)
    }
}

// Server ProviderFeature value of a provider that serves artist top tracks.
private const val ARTIST_TOPTRACKS_FEATURE = "artist_toptracks"
