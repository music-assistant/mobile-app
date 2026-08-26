package io.music_assistant.client.ui.compose.home.players

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.music_assistant.client.data.model.client.PlayerData
import io.music_assistant.client.data.model.client.PlayerDataFixtures
import io.music_assistant.client.ui.compose.common.providers.MdiCodepoints
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.compose.KoinApplication
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.koinConfiguration
import org.koin.dsl.module

/**
 * Regression guard for the 0.12.0 player-switching crash (Compose node-reuse invariant
 * failure). Churns keyed pager pages — reorder, remove, add, swipes — around the real
 * [PlayerIcon] stack while MDI glyph resolution flips asynchronously, mirroring player
 * selection/switching. Full diagnosis lives in the fix commit message.
 */
@RunWith(AndroidJUnit4::class)
class PlayerIconPagerReuseTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun playerIconsInKeyedPagerSurviveSelectionChurn() {
        val playersState = mutableStateOf(
            listOf(
                player(id = "a", icon = null),
                player(id = "b", icon = "mdi-speaker"),
                player(id = "c", icon = null),
                player(id = "d", icon = "mdi-cast"),
                player(id = "e", icon = "mdi-unknown-glyph"),
            ),
        )

        composeTestRule.setContent {
            KoinApplication(
                configuration = koinConfiguration(
                    declaration = {
                        modules(
                            module {
                                singleOf(::MdiCodepoints)
                            },
                        )
                    },
                ),
                content = {
                    val pagerState = rememberPagerState(initialPage = 1) { playersState.value.size }
                    Column {
                        HorizontalPager(
                            modifier = Modifier.testTag("pager"),
                            state = pagerState,
                            key = { page -> playersState.value.getOrNull(page)?.player?.id ?: page },
                            beyondViewportPageCount = 1,
                        ) { page ->
                            val player = playersState.value.getOrNull(page)
                                ?: return@HorizontalPager
                            PlayerIcon(
                                player = player.player,
                                isLocal = player.isLocal,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                },
            )
        }
        composeTestRule.waitForIdle()

        repeat(10) { round ->
            composeTestRule.runOnIdle {
                // Mirrors a live session: the MDI table lands mid-session (unknown names
                // and null icons keep resolving to the fallback afterwards), and player
                // data updates reorder/remove/add keyed pages while the user switches.
                playersState.value = when (round % 4) {
                    0 -> playersState.value.reversed()
                    1 -> playersState.value.drop(1) + player(
                        id = "new$round",
                        icon = if (round % 2 == 0) "mdi-speaker" else null,
                    )

                    2 -> listOf(playersState.value.last()) + playersState.value.dropLast(1)
                    else -> playersState.value.shuffled()
                }
            }
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("pager").performTouchInput {
                if (round % 2 == 0) swipeLeft() else swipeRight()
            }
            composeTestRule.waitForIdle()
        }

        composeTestRule.onNodeWithTag("pager").assertExists()
    }

    private fun player(id: String, icon: String?): PlayerData =
        PlayerDataFixtures.playerData().let { data ->
            data.copy(
                player = data.player.copy(id = id, icon = icon),
            )
        }
}
