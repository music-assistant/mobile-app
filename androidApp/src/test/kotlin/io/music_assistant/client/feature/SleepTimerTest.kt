package io.music_assistant.client.feature

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.support.FakeServiceClient
import io.music_assistant.client.support.Qualifiers
import io.music_assistant.client.support.ServerPlayerFixtures
import io.music_assistant.client.support.launchLoggedInApp
import io.music_assistant.client.support.pages.expandPlayer
import io.music_assistant.client.support.rules.createTestRuleChain
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent.inject
import org.robolectric.annotation.Config

/**
 * End-to-end cover for the server-side sleep timer: menu entry → dialog → set →
 * badge, and badge → dialog → clear → no badge. The badge only ever reflects
 * `sleep_timer_expires_at` arriving on a `PlayerUpdatedEvent`, so this also pins
 * the DTO → model → UI mapping.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = Qualifiers.MEDIUM_PHONE)
class SleepTimerTest {
    @get:Rule
    val testRuleChain = createTestRuleChain()

    @get:Rule
    val composeTestRule = createComposeRule()

    private val serviceClient: FakeServiceClient by inject(ServiceClient::class.java)

    @Test
    fun `can set and clear a sleep timer`() {
        val player = ServerPlayerFixtures.player()
        serviceClient.addPlayers(player)

        launchLoggedInApp(composeTestRule, serviceClient)
            .expandPlayer(player.displayName, playing = false, item = null)
            .assertSleepTimerBadge(active = false)
            .openSleepTimerFromMenu()
            .assertClearOffered(offered = false)
            .chooseMinutes(15)
            .assertSleepTimerBadge(active = true)
            .openSleepTimerFromBadge()
            .assertClearOffered(offered = true)
            .clearTimer()
            .assertSleepTimerBadge(active = false)
    }
}
