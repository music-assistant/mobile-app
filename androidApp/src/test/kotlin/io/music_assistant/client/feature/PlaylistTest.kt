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
import io.music_assistant.client.support.pages.clickItemOption
import io.music_assistant.client.support.pages.clickLibrary
import io.music_assistant.client.support.pages.playMedia
import io.music_assistant.client.support.rules.createTestRuleChain
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.action_play_playlist_from_here
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent.inject
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = Qualifiers.MEDIUM_PHONE)
class PlaylistTest {
    @get:Rule
    val testRuleChain = createTestRuleChain()

    @get:Rule
    val composeTestRule = createComposeRule()

    private val serviceClient: FakeServiceClient by inject(ServiceClient::class.java)

    @Test
    fun `clicking on a track plays just that track`() {
        val playlist = ServerMediaItemFixtures.playlist()
        val track1 = ServerMediaItemFixtures.track()
        val track2 = ServerMediaItemFixtures.track()
        val track3 = ServerMediaItemFixtures.track()
        serviceClient.addToLibrary(playlist, track1, track2, track3)
        serviceClient.setPlaylist(playlist, track1, track2, track3)

        val player = ServerPlayerFixtures.player()
        serviceClient.addPlayers(player)

        launchLoggedInApp(composeTestRule, serviceClient)
            .clickLibrary()
            .clickPlaylists()
            .clickOnMedia(playlist)
            .playMedia(track2)

        assertThat(serviceClient.getQueueForPlayer(player), equalTo(listOf(track2)))
    }

    @Test
    fun `long pressing a track and clicking 'Play album from here' queues the rest of the album`() {
        val playlist = ServerMediaItemFixtures.playlist()
        val track1 = ServerMediaItemFixtures.track()
        val track2 = ServerMediaItemFixtures.track()
        val track3 = ServerMediaItemFixtures.track()
        serviceClient.addToLibrary(playlist, track1, track2, track3)
        serviceClient.setPlaylist(playlist, track1, track2, track3)

        val player = ServerPlayerFixtures.player()
        serviceClient.addPlayers(player)

        launchLoggedInApp(composeTestRule, serviceClient)
            .clickLibrary()
            .clickPlaylists()
            .clickOnMedia(playlist)
            .clickItemOption(track2, Res.string.action_play_playlist_from_here.get())

        assertThat(serviceClient.getQueueForPlayer(player), equalTo(listOf(track2, track3)))
    }
}
