package io.music_assistant.client.ui.compose.home.players

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.music_assistant.client.data.model.client.AppMediaItemFixtures
import io.music_assistant.client.data.model.client.PlayerData
import io.music_assistant.client.data.model.client.PlayerDataFixtures
import io.music_assistant.client.data.model.client.PlayerDataFixtures.toQueue
import io.music_assistant.client.data.model.client.PlayerDataFixtures.toQueueTrack
import io.music_assistant.client.support.get
import io.music_assistant.client.ui.compose.common.action.PlayerAction
import io.music_assistant.client.ui.compose.common.providers.MdiCodepoints
import io.music_assistant.client.ui.compose.support.inScrollable
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.players_group_settings
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.compose.KoinApplication
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.koinConfiguration
import org.koin.dsl.module

@RunWith(AndroidJUnit4::class)
class SelectPlayerDialogTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `scrolls long list of players`() {
        val players = 0.until(25).map {
            PlayerDataFixtures.playerData()
        }

        composeTestRule.setContent {
            KoinApplication(
                configuration = koinConfiguration(declaration = {
                modules(
                    module {
                    singleOf(
                        ::MdiCodepoints,
                    )
                },
                )
            }),
                content = {
                SelectPlayerSheet(
                    selectedPlayer = players[0],
                    players,
                )
            },
            )
        }

        composeTestRule.inScrollable("PlayersList") {
            onNode(hasText(players.last().player.name)).assertIsDisplayed()
        }
    }

    @Test
    fun `lists unavailable players unless hidden`() {
        val (selected, available, unavailable) = playersWithOneUnavailable()
        val players = listOf(selected, available, unavailable)

        setDialog(selected, players, hideUnavailable = false)

        composeTestRule.onNodeWithText(unavailable.player.name).assertIsDisplayed()
    }

    @Test
    fun `hides unavailable players`() {
        val (selected, available, unavailable) = playersWithOneUnavailable()
        val players = listOf(selected, available, unavailable)

        setDialog(selected, players, hideUnavailable = true)

        composeTestRule.onNodeWithText(available.player.name).assertIsDisplayed()
        composeTestRule.onNodeWithText(unavailable.player.name).assertDoesNotExist()
    }

    @Test
    fun `keeps the selected player when it is unavailable`() {
        val (_, available, unavailable) = playersWithOneUnavailable()
        val players = listOf(available, unavailable)

        setDialog(unavailable, players, hideUnavailable = true)

        composeTestRule.onNodeWithText(unavailable.player.name).assertIsDisplayed()
    }

    @Test
    fun `opens on the group settings of the start player`() {
        val player = PlayerDataFixtures.playerData()
        composeTestRule.setContent {
            KoinApplication(
                configuration = koinConfiguration(declaration = {
                    modules(module { singleOf(::MdiCodepoints) })
                }),
                content = {
                    SelectPlayerSheet(
                        selectedPlayer = player,
                        players = listOf(player),
                        start = PlayerSheetStart.Group(player.playerId),
                    )
                },
            )
        }

        composeTestRule.onNodeWithText(Res.string.players_group_settings.get()).assertIsDisplayed()
    }

    @Test
    fun `shows nothing playing and no transport button for an empty queue`() {
        val idle = PlayerDataFixtures.playerData()

        setDialog(idle, listOf(idle), hideUnavailable = false)

        composeTestRule.onNodeWithText("Nothing playing").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Pause").assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription("Play").assertDoesNotExist()
    }

    @Test
    fun `shows the current title and sends play pause for the row player`() {
        val track = AppMediaItemFixtures.track()
        val playing = PlayerDataFixtures.playerData(listOf(track.toQueueTrack()).toQueue())
        val sent = mutableListOf<Pair<PlayerData, PlayerAction>>()

        setDialog(playing, listOf(playing), hideUnavailable = false) { data, action -> sent += data to action }
        composeTestRule.onNodeWithText(track.displayName).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Pause").performClick()

        assertEquals(listOf(playing to PlayerAction.TogglePlayPause), sent)
    }

    private fun playersWithOneUnavailable(): Triple<PlayerData, PlayerData, PlayerData> {
        val unavailable = PlayerDataFixtures.playerData().let {
            it.copy(player = it.player.copy(isAvailable = false))
        }
        return Triple(PlayerDataFixtures.playerData(), PlayerDataFixtures.playerData(), unavailable)
    }

    private fun setDialog(
        selected: PlayerData,
        players: List<PlayerData>,
        hideUnavailable: Boolean,
        onTransportAction: (PlayerData, PlayerAction) -> Unit = { _, _ -> },
    ) {
        composeTestRule.setContent {
            KoinApplication(
                configuration = koinConfiguration(declaration = {
                    modules(module { singleOf(::MdiCodepoints) })
                }),
                content = {
                    SelectPlayerSheet(
                        selectedPlayer = selected,
                        players = players,
                        hideUnavailable = hideUnavailable,
                        onTransportAction = onTransportAction,
                    )
                },
            )
        }
    }
}
