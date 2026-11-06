package io.music_assistant.client.feature

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.data.model.server.ServerPlayer
import io.music_assistant.client.nav.NavigationBarSemantics
import io.music_assistant.client.support.FakeServiceClient
import io.music_assistant.client.support.Qualifiers
import io.music_assistant.client.support.ServerMediaItemFixtures
import io.music_assistant.client.support.ServerPlayerFixtures
import io.music_assistant.client.support.get
import io.music_assistant.client.support.launchLoggedInApp
import io.music_assistant.client.support.pages.HomePage
import io.music_assistant.client.support.pages.assertPlayer
import io.music_assistant.client.support.pages.expandPlayer
import io.music_assistant.client.support.rules.createTestRuleChain
import io.music_assistant.client.support.withinTag
import io.music_assistant.client.ui.compose.home.players.FullPlayerSemantics
import io.music_assistant.client.ui.utils.DeviceChrome
import io.music_assistant.client.ui.utils.FoldRegion
import io.music_assistant.client.ui.utils.VerticalBarArea
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.action_pause
import musicassistantclient.composeapp.generated.resources.cd_more
import musicassistantclient.composeapp.generated.resources.nav_menu
import musicassistantclient.composeapp.generated.resources.nav_search
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent.inject
import org.robolectric.annotation.Config

/**
 * The layouts for the geometry that iOS reports on iPhone Duo. Each test starts with the usual
 * layout, because the page objects expect the bottom bar, and then sets the geometry.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = Qualifiers.MEDIUM_PHONE)
class DeviceChromeTest {
    @get:Rule
    val testRuleChain = createTestRuleChain()

    @get:Rule
    val composeTestRule = createComposeRule()

    private val serviceClient: FakeServiceClient by inject(ServiceClient::class.java)

    private var deviceChrome by mutableStateOf(DeviceChrome.None)

    @Test
    fun `a vertical bar area moves the navigation bar into the strip at the trailing edge`() {
        playTrack()

        deviceChrome = DeviceChrome(verticalBarArea = BAR)

        // The collapsed player leaves its transport buttons to the bar.
        composeTestRule.onNode(inBar(Res.string.action_pause.get())).assertIsDisplayed()
        composeTestRule.onNode(inBar(Res.string.nav_menu.get())).assertIsDisplayed()
        val root = composeTestRule.onRoot().getUnclippedBoundsInRoot()
        val bar = composeTestRule.onNodeWithTag(NavigationBarSemantics.TAG).getUnclippedBoundsInRoot()
        assertEquals(VERTICAL_BAR_WIDTH_DP, bar.width.value, TOLERANCE_DP)
        assertTrue("The bar is taller than it is wide", bar.height > bar.width)
        assertEquals((root.right - BAR.end).value, bar.right.value, TOLERANCE_DP)
        assertEquals((root.bottom - BAR.bottom).value, bar.bottom.value, TOLERANCE_DP)
    }

    @Test
    fun `a short vertical bar area puts the buttons that do not fit into the More menu`() {
        launch()
        val rootHeight = composeTestRule.onRoot().getUnclippedBoundsInRoot().height

        // Room for two buttons: one item and the More button.
        deviceChrome = DeviceChrome(
            verticalBarArea = BAR.copy(top = rootHeight - BAR.bottom - VERTICAL_BAR_WIDTH_DP.dp * 2.5f),
        )

        composeTestRule.onNode(inBar(Res.string.cd_more.get())).assertIsDisplayed().performClick()
        composeTestRule.onNode(hasAnyAncestor(isPopup()).and(hasText(Res.string.nav_search.get()))).assertIsDisplayed()
    }

    @Test
    fun `a group keeps its count in overflow and its badge in bottom navigation`() {
        assertPlayerSelectionLabels(
            player = ServerPlayerFixtures.player(id = "kitchen", name = PLAYER_NAME).copy(
                type = "group",
                groupMembers = setOf("a", "b", "c"),
            ),
            overflowLabel = "Kitchen (3)",
            badge = "3",
        )
    }

    @Test
    fun `a sync leader keeps its bound count in overflow and its badge in bottom navigation`() {
        assertPlayerSelectionLabels(
            player = ServerPlayerFixtures.player(id = "kitchen", name = PLAYER_NAME).copy(
                groupMembers = setOf("kitchen", "a", "b"),
                canGroupWith = listOf("spare"),
            ),
            overflowLabel = "Kitchen + 2",
            badge = "+2",
        )
    }

    @Test
    fun `a solo player keeps its plain name in overflow and bottom navigation`() {
        assertPlayerSelectionLabels(
            player = ServerPlayerFixtures.player(id = "kitchen", name = PLAYER_NAME),
            overflowLabel = "Kitchen",
            badge = null,
        )
    }

    @Test
    fun `a horizontal fold keeps the artwork of the full player above the fold`() {
        playTrack().expandPlayer(PLAYER_NAME, playing = true, item = TRACK_NAME)
        val flat = artworkInWindow()
        // A fold through the middle of the artwork.
        val foldTop = flat.top + flat.height / 2

        deviceChrome = DeviceChrome(
            horizontalFoldRegion = with(composeTestRule.density) {
                FoldRegion(start = foldTop.toDp(), end = foldTop.toDp() + 20.dp)
            },
        )
        composeTestRule.waitForIdle()
        val folded = artworkInWindow()
        assertTrue("Artwork ends at ${folded.bottom}, the fold starts at $foldTop", folded.bottom <= foldTop + 1)
        assertTrue("The artwork keeps a size", folded.height > 0)

        deviceChrome = DeviceChrome.None
        composeTestRule.waitForIdle()
        assertEquals(flat.bottom, artworkInWindow().bottom, 1f)
    }

    private fun assertPlayerSelectionLabels(player: ServerPlayer, overflowLabel: String, badge: String?) {
        val members = player.groupMembers.orEmpty().filter { it != player.playerId }.map {
            ServerPlayerFixtures.player(id = it, name = "Member $it")
        }
        serviceClient.addPlayers(
            player,
            *members.toTypedArray(),
            ServerPlayerFixtures.player(id = "spare", name = "Spare"),
        )
        launchLoggedInApp(composeTestRule, serviceClient) { deviceChrome }

        // The bottom bar keeps the plain name and the separate count badge.
        val nameInBar = withinTag(NavigationBarSemantics.TAG).and(hasText(PLAYER_NAME))
        composeTestRule.waitUntil("the selected player appears in bottom navigation") {
            composeTestRule.onNode(nameInBar, useUnmergedTree = true).isDisplayed()
        }
        if (badge != null) {
            composeTestRule.onNode(
                withinTag(NavigationBarSemantics.TAG).and(hasText(badge)),
                useUnmergedTree = true,
            ).assertIsDisplayed()
            composeTestRule.onNode(
                withinTag(NavigationBarSemantics.TAG).and(hasText(overflowLabel)),
                useUnmergedTree = true,
            ).assertDoesNotExist()
        } else {
            listOf("3", "+2").forEach {
                composeTestRule.onNode(
                    withinTag(NavigationBarSemantics.TAG).and(hasText(it)),
                    useUnmergedTree = true,
                ).assertDoesNotExist()
            }
        }

        val rootHeight = composeTestRule.onRoot().getUnclippedBoundsInRoot().height
        deviceChrome = DeviceChrome(
            verticalBarArea = BAR.copy(top = rootHeight - BAR.bottom - VERTICAL_BAR_WIDTH_DP.dp * 2.5f),
        )
        composeTestRule.onNode(inBar(Res.string.cd_more.get())).assertIsDisplayed().performClick()
        composeTestRule.onNode(
            hasAnyAncestor(isPopup()).and(hasText(overflowLabel)),
        ).assertIsDisplayed()
    }

    private fun launch(): HomePage {
        serviceClient.addPlayers(ServerPlayerFixtures.player(name = PLAYER_NAME))
        return launchLoggedInApp(composeTestRule, serviceClient) { deviceChrome }
    }

    private fun playTrack(): HomePage {
        val album = ServerMediaItemFixtures.album()
        serviceClient.addItems(ServerMediaItemFixtures.track(name = TRACK_NAME, album = album))
        val home = launch()
        home.clickOnMedia(album).clickPlay().assertPlayer(PLAYER_NAME, playing = true, item = TRACK_NAME)
        return home
    }

    private fun inBar(contentDescription: String) =
        withinTag(NavigationBarSemantics.TAG).and(hasContentDescription(contentDescription))

    private fun artworkInWindow(): Rect =
        composeTestRule.onNodeWithTag(FullPlayerSemantics.ARTWORK_TAG).fetchSemanticsNode().boundsInWindow

    private companion object {
        const val PLAYER_NAME = "Kitchen"
        const val TRACK_NAME = "Song"
        const val VERTICAL_BAR_WIDTH_DP = 56f
        const val TOLERANCE_DP = 0.5f
        val BAR = VerticalBarArea(trailingInset = 64.dp, top = 120.dp, bottom = 24.dp, end = 4.dp)
    }
}
