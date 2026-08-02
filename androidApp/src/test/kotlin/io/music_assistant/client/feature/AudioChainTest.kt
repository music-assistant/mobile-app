package io.music_assistant.client.feature

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.data.model.server.AudioFormat
import io.music_assistant.client.support.FakeServiceClient
import io.music_assistant.client.support.FakeServiceClient.LegacyVersion
import io.music_assistant.client.support.Qualifiers
import io.music_assistant.client.support.ServerMediaItemFixtures
import io.music_assistant.client.support.ServerPlayerFixtures
import io.music_assistant.client.support.launchLoggedInApp
import io.music_assistant.client.support.pages.expandPlayer
import io.music_assistant.client.support.rules.createTestRuleChain
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent.inject
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = Qualifiers.MEDIUM_PHONE)
class AudioChainTest {
    @get:Rule
    val testRuleChain = createTestRuleChain()

    @get:Rule
    val composeTestRule = createComposeRule()

    private val serviceClient: FakeServiceClient by inject(ServiceClient::class.java)

    @Test
    fun `does not crash`() {
        val album = ServerMediaItemFixtures.album()
        val track = ServerMediaItemFixtures.track(album = album)
        serviceClient.addItems(track)

        val audioFormat = AudioFormat(
            contentType = "s16le",
            sampleRate = 48000,
            bitDepth = 16,
        )

        val player = ServerPlayerFixtures.player()
        serviceClient.addPlayers(player)
        serviceClient.setPlayerAudioFormat(player, audioFormat)

        launchLoggedInApp(composeTestRule, serviceClient)
            .clickOnMedia(album)
            .clickPlay()
            .expandPlayer(player.displayName, playing = true, item = track.name)
    }

    @Test
    fun `can view output format for current queue item in legacy versions`() {
        serviceClient.setLegacyVersion(LegacyVersion.V2_9)

        val album = ServerMediaItemFixtures.album()
        val track = ServerMediaItemFixtures.track(album = album)
        serviceClient.addItems(track)

        val audioFormat = AudioFormat(
            contentType = "s16le",
            sampleRate = 48000,
            bitDepth = 16,
        )

        val player = ServerPlayerFixtures.player()
        serviceClient.addPlayers(player)
        serviceClient.setPlayerAudioFormat(player, audioFormat)

        launchLoggedInApp(composeTestRule, serviceClient)
            .clickOnMedia(album)
            .clickPlay()
            .expandPlayer(player.displayName, playing = true, item = track.name)
            .clickQualityTier("HQ")
            .assertFormatDisplayed(audioFormat)
    }
}
