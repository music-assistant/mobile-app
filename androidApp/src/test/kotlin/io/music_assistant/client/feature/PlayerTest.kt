package io.music_assistant.client.feature

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.data.model.server.PlayerState
import io.music_assistant.client.data.model.server.ServerMediaItem
import io.music_assistant.client.support.FakeServiceClient
import io.music_assistant.client.support.Qualifiers
import io.music_assistant.client.support.ServerMediaItemFixtures
import io.music_assistant.client.support.ServerPlayerFixtures
import io.music_assistant.client.support.launchLoggedInApp
import io.music_assistant.client.support.pages.Page
import io.music_assistant.client.support.pages.assertPlayer
import io.music_assistant.client.support.pages.expandPlayer
import io.music_assistant.client.support.pages.pause
import io.music_assistant.client.support.pages.playMedia
import io.music_assistant.client.support.rules.createTestRuleChain
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent.inject
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = Qualifiers.MEDIUM_PHONE)
class PlayerTest {
    @get:Rule
    val testRuleChain = createTestRuleChain()

    @get:Rule
    val composeTestRule = createComposeRule()

    private val serviceClient: FakeServiceClient by inject(ServiceClient::class.java)

    @Test
    fun `shows message when nothing is playing`() {
        val player = ServerPlayerFixtures.player()
        serviceClient.addPlayers(player)

        launchLoggedInApp(composeTestRule, serviceClient)
            .assertPlayer(player.displayName, playing = false, item = null)
            .expandPlayer(player.displayName, playing = false, item = null)
    }

    @Test
    fun `can play album`() {
        val album = ServerMediaItemFixtures.album()
        val track = ServerMediaItemFixtures.track(album = album)
        serviceClient.addToLibrary(track)

        val player = ServerPlayerFixtures.player()
        serviceClient.addPlayers(player)

        launchLoggedInApp(composeTestRule, serviceClient)
            .clickOnMedia(album)
            .clickPlay()
            .assertPlayer(player.displayName, playing = true, item = track.name)
            .assertPlayerState(
                serviceClient,
                player.playerId,
                playerState = PlayerState.PLAYING,
                serverMediaItem = track,
            )
    }

    @Test
    fun `can play track from album`() {
        val album = ServerMediaItemFixtures.album()
        val track1 = ServerMediaItemFixtures.track(album = album)
        val track2 = ServerMediaItemFixtures.track(album = album)
        serviceClient.addToLibrary(track1, track2)

        val player = ServerPlayerFixtures.player()
        serviceClient.addPlayers(player)

        launchLoggedInApp(composeTestRule, serviceClient)
            .clickOnMedia(album)
            .playMedia(track2)
            .assertPlayer(player.displayName, playing = true, item = track2.name)
            .assertPlayerState(
                serviceClient,
                player.playerId,
                playerState = PlayerState.PLAYING,
                serverMediaItem = track2,
            )
            .playMedia(track1)
            .assertPlayer(player.displayName, playing = true, item = track1.name)
            .assertPlayerState(
                serviceClient,
                player.playerId,
                playerState = PlayerState.PLAYING,
                serverMediaItem = track1,
            )
    }

    @Test
    fun `can pause playback`() {
        val track = ServerMediaItemFixtures.track()
        serviceClient.addToLibrary(track)

        val player = ServerPlayerFixtures.player()
        serviceClient.addPlayers(player)

        launchLoggedInApp(composeTestRule, serviceClient)
            .playMedia(track)
            .assertPlayer(player.displayName, playing = true, item = track.name)
            .pause()
            .assertPlayer(player.displayName, playing = false, item = track.name)
            .assertPlayerState(
                serviceClient,
                player.playerId,
                playerState = PlayerState.PAUSED,
                serverMediaItem = track,
            )
    }
}

private fun <T : Page> T.assertPlayerState(
    serviceClient: FakeServiceClient,
    playerId: String,
    playerState: PlayerState,
    serverMediaItem: ServerMediaItem?,
): T {
    assertThat(serviceClient.getState(playerId), equalTo(playerState))
    assertThat(serviceClient.getCurrentlyPlaying(playerId), equalTo(serverMediaItem))
    return this
}
