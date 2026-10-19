package io.music_assistant.client.ui.compose.home.players

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.music_assistant.client.data.model.client.PlayerDataFixtures
import io.music_assistant.client.ui.compose.common.providers.MdiCodepoints
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.compose.KoinApplication
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.koinConfiguration
import org.koin.dsl.module
import kotlin.test.assertEquals

/**
 * Regression coverage for the reported Android TV bug (a remote's D-pad up/down leaking past
 * the player-switcher dialog into the now-playing volume slider behind it) because nothing in
 * the dialog claimed keyboard/D-pad focus.
 */
@RunWith(AndroidJUnit4::class)
class SelectPlayerDialogFocusTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `d-pad down moves focus to the next row and center selects it`() {
        val kitchen = PlayerDataFixtures.playerData(name = "Kitchen")
        val livingRoom = PlayerDataFixtures.playerData(name = "Living Room")
        var selectedPlayerId: String? = null

        composeTestRule.setContent {
            KoinApplication(
                configuration = koinConfiguration(declaration = {
                    modules(module { singleOf(::MdiCodepoints) })
                }),
                content = {
                    SelectPlayerDialog(
                        selectedPlayer = kitchen,
                        players = listOf(kitchen, livingRoom),
                        onMoveToPlayer = { selectedPlayerId = it },
                    )
                },
            )
        }

        composeTestRule.onNodeWithTag("PlayersList").performKeyInput {
            pressKey(Key.DirectionDown)
            pressKey(Key.DirectionCenter)
        }

        assertEquals(livingRoom.player.id, selectedPlayerId)
    }
}
