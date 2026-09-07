package io.music_assistant.client.feature

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.support.FakeServiceClient
import io.music_assistant.client.support.Qualifiers
import io.music_assistant.client.support.ServerMediaItemFixtures
import io.music_assistant.client.support.ServerPlayerFixtures
import io.music_assistant.client.support.get
import io.music_assistant.client.support.launchLoggedInApp
import io.music_assistant.client.support.pages.HomePage
import io.music_assistant.client.support.pages.clickBack
import io.music_assistant.client.support.pages.clickItemNavigationOption
import io.music_assistant.client.support.pages.expandPlayer
import io.music_assistant.client.support.pages.playMedia
import io.music_assistant.client.support.rules.createTestRuleChain
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.action_go_to_album
import musicassistantclient.composeapp.generated.resources.action_go_to_artist
import musicassistantclient.composeapp.generated.resources.nav_home
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent.inject
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = Qualifiers.MEDIUM_PHONE)
class ItemNavigationTest {
    @get:Rule
    val testRuleChain = createTestRuleChain()

    @get:Rule
    val composeTestRule = createComposeRule()

    val serviceClient: FakeServiceClient by inject(ServiceClient::class.java)

    @Test
    fun `can navigate from album to artist`() {
        val artist = ServerMediaItemFixtures.artist()
        val album = ServerMediaItemFixtures.album(artist = artist)
        serviceClient.addItems(album)

        launchLoggedInApp(composeTestRule, serviceClient)
            .clickOnMedia(album)
            .clickGoToArtist(artist.name)
    }

    @Test
    fun `can navigate from an album long-click menu to its artist`() {
        val artist = ServerMediaItemFixtures.artist()
        val album = ServerMediaItemFixtures.album(artist = artist)
        serviceClient.addItems(album)

        launchLoggedInApp(composeTestRule, serviceClient)
            .clickItemNavigationOption(
                serverMediaItem = album,
                action = Res.string.action_go_to_artist.get(),
                target = artist,
                navigationItem = Res.string.nav_home.get(),
            )
    }

    @Test
    fun `can navigate from a track long-click menu to its album`() {
        val album = ServerMediaItemFixtures.album()
        val track = ServerMediaItemFixtures.track(album = album)
        serviceClient.addItems(track)

        launchLoggedInApp(composeTestRule, serviceClient)
            .clickItemNavigationOption(
                serverMediaItem = track,
                action = Res.string.action_go_to_album.get(),
                target = album,
                navigationItem = Res.string.nav_home.get(),
            )
    }

    @Test
    fun `can navigate to artist from expanded player`() {
        val artist = ServerMediaItemFixtures.artist()
        val track = ServerMediaItemFixtures.track(artists = listOf(artist))
        serviceClient.addItems(track)

        val player = ServerPlayerFixtures.player()
        serviceClient.addPlayers(player)

        launchLoggedInApp(composeTestRule, serviceClient)
            .playMedia(track)
            .expandPlayer(player.displayName, playing = true, item = track.name)
            .goToArtist(artist.name, navigationItem = "Home")
    }

    @Test
    fun `can navigate to album from expanded player`() {
        val album = ServerMediaItemFixtures.album()
        val track = ServerMediaItemFixtures.track(album = album)
        serviceClient.addItems(track)

        val player = ServerPlayerFixtures.player()
        serviceClient.addPlayers(player)

        launchLoggedInApp(composeTestRule, serviceClient)
            .playMedia(track)
            .expandPlayer(player.displayName, playing = true, item = track.name)
            .goToAlbum(album.name, navigationItem = "Home")
    }

    @Test
    fun `can return from item`() {
        val album = ServerMediaItemFixtures.album()
        serviceClient.addItems(album)

        launchLoggedInApp(composeTestRule, serviceClient)
            .clickOnMedia(album)
            .clickBack(HomePage(composeTestRule))
    }
}
