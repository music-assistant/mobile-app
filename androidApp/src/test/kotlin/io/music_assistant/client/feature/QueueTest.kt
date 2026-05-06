package io.music_assistant.client.feature

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.support.FakeServiceClient
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
import kotlin.getValue

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = Qualifiers.MEDIUM_PHONE)
class QueueTest {
    @get:Rule
    val testRuleChain = createTestRuleChain()

    @get:Rule
    val composeTestRule = createComposeRule()

    private val serviceClient: FakeServiceClient by inject(ServiceClient::class.java)

    @Test
    fun `can clear current player queue`() {
        val album = ServerMediaItemFixtures.album()
        val track = ServerMediaItemFixtures.track(album = album)
        serviceClient.addToLibrary(track)

        val player = ServerPlayerFixtures.player()
        serviceClient.addPlayer(player)

        launchLoggedInApp(composeTestRule, serviceClient)
            .clickOnMedia(album)
            .clickPlay()
            .expandPlayer(player.displayName, playing = true, item = track.name)
            .clearQueue()
    }
}
