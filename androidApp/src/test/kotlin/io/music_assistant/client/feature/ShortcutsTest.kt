package io.music_assistant.client.feature

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.support.FakeServiceClient
import io.music_assistant.client.support.Qualifiers
import io.music_assistant.client.support.ServerMediaItemFixtures
import io.music_assistant.client.support.ServerPlayerFixtures
import io.music_assistant.client.support.launchLoggedInApp
import io.music_assistant.client.support.pages.assertPlayer
import io.music_assistant.client.support.rules.createTestRuleChain
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent.inject
import org.robolectric.annotation.Config
import kotlin.getValue

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = Qualifiers.MEDIUM_PHONE)
class ShortcutsTest {
    @get:Rule
    val testRuleChain = createTestRuleChain()

    @get:Rule
    val composeTestRule = createComposeRule()

    val serviceClient: FakeServiceClient by inject(ServiceClient::class.java)

    @Test
    fun `can navigate to shortcut items`() {
        val album = ServerMediaItemFixtures.album()
        serviceClient.addToLibrary(album)
        serviceClient.addShortcut(album)

        launchLoggedInApp(composeTestRule, serviceClient)
            .clickOnShortcut(album)
    }

    @Test
    fun `can play shortcut items`() {
        val track = ServerMediaItemFixtures.track()
        serviceClient.addToLibrary(track)
        serviceClient.addShortcut(track)

        val player = ServerPlayerFixtures.player()
        serviceClient.addPlayers(player)

        launchLoggedInApp(composeTestRule, serviceClient)
            .playShortcut(track)
            .assertPlayer(player.displayName, playing = true, item = track.name)
    }
}
