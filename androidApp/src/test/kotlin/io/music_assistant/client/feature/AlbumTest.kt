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
import io.music_assistant.client.support.pages.playMedia
import io.music_assistant.client.support.rules.createTestRuleChain
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.action_play_album_from_here
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent.inject
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = Qualifiers.MEDIUM_PHONE)
class AlbumTest {
    @get:Rule
    val testRuleChain = createTestRuleChain()

    @get:Rule
    val composeTestRule = createComposeRule()

    private val serviceClient: FakeServiceClient by inject(ServiceClient::class.java)

    @Test
    fun `clicking on a track plays just that track`() {
        val album = ServerMediaItemFixtures.album()
        val track1 = ServerMediaItemFixtures.track(album = album)
        val track2 = ServerMediaItemFixtures.track(album = album)
        val track3 = ServerMediaItemFixtures.track(album = album)
        serviceClient.addToLibrary(track1, track2, track3)

        val player = ServerPlayerFixtures.player()
        serviceClient.addPlayers(player)

        launchLoggedInApp(composeTestRule, serviceClient)
            .clickOnMedia(album)
            .playMedia(track2)

        assertThat(serviceClient.getQueueForPlayer(player), equalTo(listOf(track2)))
    }

    @Test
    fun `long pressing a track and clicking 'Play album from here' queues the rest of the album`() {
        val album = ServerMediaItemFixtures.album()
        val track1 = ServerMediaItemFixtures.track(album = album)
        val track2 = ServerMediaItemFixtures.track(album = album)
        val track3 = ServerMediaItemFixtures.track(album = album)
        serviceClient.addToLibrary(track1, track2, track3)

        val player = ServerPlayerFixtures.player()
        serviceClient.addPlayers(player)

        launchLoggedInApp(composeTestRule, serviceClient)
            .clickOnMedia(album)
            .clickItemOption(track2, Res.string.action_play_album_from_here.get())

        assertThat(serviceClient.getQueueForPlayer(player), equalTo(listOf(track2, track3)))
    }
}
