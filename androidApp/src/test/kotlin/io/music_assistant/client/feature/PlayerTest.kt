package io.music_assistant.client.feature

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.support.FakeServiceClient
import io.music_assistant.client.support.Qualifiers
import io.music_assistant.client.support.ServerMediaItemFixtures
import io.music_assistant.client.support.ServerPlayerFixtures
import io.music_assistant.client.support.launchLoggedInApp
import io.music_assistant.client.support.pages.assertCurrentPlayer
import io.music_assistant.client.support.pages.clickOnMedia
import io.music_assistant.client.support.pages.pause
import io.music_assistant.client.support.pages.playMedia
import io.music_assistant.client.support.rules.createTestRuleChain
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
    fun `can play album`() {
        val album = ServerMediaItemFixtures.album()
        val track = ServerMediaItemFixtures.track(album = album)
        serviceClient.addToLibrary(track)

        val player = ServerPlayerFixtures.player()
        serviceClient.addPlayer(player)

        launchLoggedInApp(composeTestRule, serviceClient)
            .clickOnMedia(album)
            .clickPlay()
            .assertCurrentPlayer(player.displayName, playing = true, item = track.name)
    }

    @Test
    fun `can play track from album`() {
        val album = ServerMediaItemFixtures.album()
        val track1 = ServerMediaItemFixtures.track(album = album)
        val track2 = ServerMediaItemFixtures.track(album = album)
        serviceClient.addToLibrary(track1, track2)

        val player = ServerPlayerFixtures.player()
        serviceClient.addPlayer(player)

        launchLoggedInApp(composeTestRule, serviceClient)
            .clickOnMedia(album)
            .playMedia(track2)
            .assertCurrentPlayer(player.displayName, playing = true, item = track2.name)
            .playMedia(track1)
            .assertCurrentPlayer(player.displayName, playing = true, item = track1.name)
    }

    @Test
    fun `can pause playback`() {
        val track = ServerMediaItemFixtures.track()
        serviceClient.addToLibrary(track)

        val player = ServerPlayerFixtures.player()
        serviceClient.addPlayer(player)

        launchLoggedInApp(composeTestRule, serviceClient)
            .playMedia(track)
            .assertCurrentPlayer(player.displayName, playing = true, item = track.name)
            .pause()
            .assertCurrentPlayer(player.displayName, playing = false, item = track.name)
    }
}
