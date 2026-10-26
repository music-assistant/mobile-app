package io.music_assistant.client.ui.compose.item

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.music_assistant.client.data.model.client.AppMediaItemFixtures
import io.music_assistant.client.data.model.client.Lyrics
import io.music_assistant.client.data.model.client.items.Album
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.client.items.RefreshItemAction
import io.music_assistant.client.data.model.client.items.Track
import io.music_assistant.client.support.get
import io.music_assistant.client.support.isTab
import io.music_assistant.client.ui.compose.common.DataState
import io.music_assistant.client.ui.compose.common.ExtractedColors
import io.music_assistant.client.ui.compose.common.ExtractedColorsSource
import io.music_assistant.client.ui.compose.support.inScrollable
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.action_choose_artist
import musicassistantclient.composeapp.generated.resources.action_find_on_music_services
import musicassistantclient.composeapp.generated.resources.action_go_to_album
import musicassistantclient.composeapp.generated.resources.action_go_to_artist
import musicassistantclient.composeapp.generated.resources.action_go_to_track
import musicassistantclient.composeapp.generated.resources.action_lyrics
import musicassistantclient.composeapp.generated.resources.action_refresh_item
import musicassistantclient.composeapp.generated.resources.cd_more
import musicassistantclient.composeapp.generated.resources.cd_refreshing_item
import musicassistantclient.composeapp.generated.resources.item_tab_similar_tracks
import musicassistantclient.composeapp.generated.resources.item_tab_versions
import musicassistantclient.composeapp.generated.resources.media_type_albums
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private val NoColors = object : ExtractedColorsSource {
    override fun peek(imageUrl: String): ExtractedColors? = null
    override suspend fun fetch(imageUrl: String): ExtractedColors? = null
}

/**
 * Composes with inspection mode on so the Koin-backed dynamic-colors seams
 * (`rememberDynamicColorsEnabled`, `dynamicColorsMenuOption`) short-circuit to their
 * @Preview defaults instead of hitting a Koin graph this test doesn't start.
 */
private fun ComposeContentTestRule.setInspectableContent(content: @Composable () -> Unit) =
    setContent {
        CompositionLocalProvider(LocalInspectionMode provides true, content = content)
    }

@RunWith(AndroidJUnit4::class)
class ItemDetailsTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `displays album version`() {
        val artist = AppMediaItemFixtures.artist()
        val album = AppMediaItemFixtures.album(artist = artist, version = "Best Version")

        composeTestRule.setInspectableContent {
            ItemDetails(
                state = ItemDetailsViewModel.State(
                    itemState = DataState.Data(album),
                    albumsState = DataState.NoData(),
                    playableItemsState = DataState.Data(emptyList()),
                ),
                geEditablePlaylists = suspend { emptyList() },
                fetchColors = NoColors,
            )
        }

        composeTestRule.onAllNodes(hasText(album.name)).onFirst().assertIsDisplayed()
        composeTestRule.onAllNodes(hasText(album.version!!)).onFirst().assertIsDisplayed()
    }

    @Test
    fun `does not show go to artist button if there are none`() {
        val album = AppMediaItemFixtures.album(artist = null)

        composeTestRule.setInspectableContent {
            ItemDetails(
                state = ItemDetailsViewModel.State(
                    itemState = DataState.Data(album),
                    albumsState = DataState.NoData(),
                    playableItemsState = DataState.Data(emptyList()),
                ),
                fetchColors = NoColors,
            )
        }

        composeTestRule.onNodeWithContentDescription(Res.string.cd_more.get()).performClick()
        composeTestRule.onNodeWithText(Res.string.action_go_to_artist.get()).assertIsNotDisplayed()
    }

    @Test
    fun `tapping the album artist line navigates to the single artist`() {
        val artist = AppMediaItemFixtures.artist(name = "Solo")
        val album = AppMediaItemFixtures.album(artist = artist)
        var navigatedTo: AppMediaItem? = null

        composeTestRule.setInspectableContent {
            ItemHeader(item = album, navigateToItem = { navigatedTo = it })
        }

        composeTestRule.onNodeWithText("Solo").performClick()

        composeTestRule.onNodeWithText(Res.string.action_choose_artist.get()).assertIsNotDisplayed()
        assertEquals(artist, navigatedTo)
    }

    @Test
    fun `tapping a multi-artist album line opens the choose-artist dialog`() {
        val first = AppMediaItemFixtures.artist(name = "First")
        val second = AppMediaItemFixtures.artist(name = "Second")
        val album = AppMediaItemFixtures.album().copy(artists = listOf(first, second))
        var navigatedTo: AppMediaItem? = null

        composeTestRule.setInspectableContent {
            ItemHeader(item = album, navigateToItem = { navigatedTo = it })
        }

        composeTestRule.onNodeWithText("First, Second").performClick()
        composeTestRule.onNodeWithText(Res.string.action_choose_artist.get()).assertIsDisplayed()
        assertNull(navigatedTo)

        composeTestRule.onNodeWithText("Second").performClick()

        assertEquals(second, navigatedTo)
    }

    @Test
    fun `displays podcasts`() {
        val podcast = AppMediaItemFixtures.podcast()
        val episodes =
            AppMediaItemFixtures.episodes(listOf("Episode 1", "Episode 2"), podcast = podcast)

        composeTestRule.setInspectableContent {
            ItemDetails(
                state = ItemDetailsViewModel.State(
                    itemState = DataState.Data(podcast),
                    albumsState = DataState.NoData(),
                    playableItemsState = DataState.Data(episodes),
                ),
                geEditablePlaylists = suspend { emptyList() },
                fetchColors = NoColors,
            )
        }

        composeTestRule.onAllNodes(hasText(podcast.displayName)).onFirst().assertIsDisplayed()
        composeTestRule.inScrollable("LazyVerticalGrid") {
            onNode(hasContentDescription(episodes[0].displayName)).assertIsDisplayed()
            onNode(hasContentDescription(episodes[1].displayName)).assertIsDisplayed()
        }
    }

    @Test
    fun `displays audiobooks`() {
        val audiobook = AppMediaItemFixtures.audiobook(chapters = listOf("Chapter 1", "Chapter 2"))

        composeTestRule.setInspectableContent {
            ItemDetails(
                state = ItemDetailsViewModel.State(
                    itemState = DataState.Data(audiobook),
                    albumsState = DataState.NoData(),
                    playableItemsState = DataState.NoData(),
                ),
                geEditablePlaylists = suspend { emptyList() },
                fetchColors = NoColors,
            )
        }

        composeTestRule.onAllNodes(hasText(audiobook.displayName)).onFirst().assertIsDisplayed()
        val chapters = audiobook.chapters!!
        composeTestRule.inScrollable("LazyVerticalGrid") {
            onNode(hasText(chapters[0].name)).assertIsDisplayed()
            onNode(hasText(chapters[1].name)).assertIsDisplayed()
        }
    }

    @Test
    fun `track shows albums and versions but no similar tab without provider support`() {
        val album = AppMediaItemFixtures.album(name = "On Album")
        val track = AppMediaItemFixtures.track(name = "Song", album = album)
        val version = AppMediaItemFixtures.track(name = "Song (Live)")

        composeTestRule.setInspectableContent {
            var state by remember {
                mutableStateOf(
                    trackState(
                        track,
                        albumsState = DataState.Data(listOf(album)),
                        trackVersionsState = DataState.Data(listOf(version)),
                    ),
                )
            }
            ItemDetails(
                state = state,
                onTabSelected = { state = state.copy(userSelectedTab = it) },
                fetchColors = NoColors,
            )
        }

        composeTestRule.onNode(isTab(Res.string.media_type_albums.get())).assertIsDisplayed()
        composeTestRule.onNode(isTab(Res.string.item_tab_versions.get())).assertIsDisplayed()
        composeTestRule.onNode(isTab(Res.string.item_tab_similar_tracks.get())).assertDoesNotExist()
        composeTestRule.inScrollable("LazyVerticalGrid") {
            onNode(hasContentDescription(album.name, substring = true)).assertIsDisplayed()
        }

        composeTestRule.onNode(isTab(Res.string.item_tab_versions.get())).performClick()
        composeTestRule.inScrollable("LazyVerticalGrid") {
            onNode(hasContentDescription(version.name, substring = true)).assertIsDisplayed()
        }
    }

    @Test
    fun `track shows the similar tab once similar tracks load`() {
        val track = AppMediaItemFixtures.track(name = "Song")
        val similar = AppMediaItemFixtures.track(name = "Like Song")

        composeTestRule.setInspectableContent {
            var state by remember {
                mutableStateOf(
                    trackState(
                        track,
                        albumsState = DataState.Data(listOf(AppMediaItemFixtures.album())),
                        similarTracksState = DataState.Data(listOf(similar)),
                    ),
                )
            }
            ItemDetails(
                state = state,
                onTabSelected = { state = state.copy(userSelectedTab = it) },
                fetchColors = NoColors,
            )
        }

        composeTestRule.onNode(isTab(Res.string.item_tab_similar_tracks.get())).performClick()
        composeTestRule.inScrollable("LazyVerticalGrid") {
            onNode(hasContentDescription(similar.name, substring = true)).assertIsDisplayed()
        }
    }

    @Test
    fun `track overflow opens the lyrics and has no entry for the track itself`() {
        val track = AppMediaItemFixtures.track(name = "Song", album = AppMediaItemFixtures.album())

        composeTestRule.setInspectableContent {
            ItemDetails(
                state = trackState(track, lyrics = Lyrics.Plain("First line of the song")),
                fetchColors = NoColors,
            )
        }

        composeTestRule.onNodeWithContentDescription(Res.string.cd_more.get()).performClick()
        composeTestRule.onNodeWithText(Res.string.action_go_to_album.get()).assertIsDisplayed()
        composeTestRule.onNodeWithText(Res.string.action_go_to_track.get()).assertDoesNotExist()
        composeTestRule.onNodeWithText(Res.string.action_lyrics.get()).performClick()

        composeTestRule.onNodeWithText("First line of the song").assertIsDisplayed()
    }

    @Test
    fun `track without lyrics has no lyrics entry`() {
        val track = AppMediaItemFixtures.track(name = "Song")

        composeTestRule.setInspectableContent {
            ItemDetails(state = trackState(track), fetchColors = NoColors)
        }

        composeTestRule.onNodeWithContentDescription(Res.string.cd_more.get()).performClick()
        composeTestRule.onNodeWithText(Res.string.action_lyrics.get()).assertDoesNotExist()
    }

    @Test
    fun `refresh entry runs the refresh and shows progress while it runs`() {
        var refreshes = 0
        var refreshing by mutableStateOf(false)
        val track = AppMediaItemFixtures.track(name = "Song")

        composeTestRule.setInspectableContent {
            ItemDetails(
                state = trackState(track).copy(refreshingItem = refreshing),
                fetchColors = NoColors,
                refreshAction = RefreshItemAction.REFRESH,
                onRefreshItem = { refreshes++ },
            )
        }

        composeTestRule.onNodeWithContentDescription(Res.string.cd_more.get()).performClick()
        composeTestRule.onNodeWithText(Res.string.action_refresh_item.get()).performClick()
        assertEquals(1, refreshes)

        refreshing = true
        composeTestRule.onNodeWithContentDescription(Res.string.cd_refreshing_item.get()).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(Res.string.cd_more.get()).performClick()
        composeTestRule.onNodeWithText(Res.string.action_refresh_item.get()).assertIsNotEnabled()
    }

    @Test
    fun `unavailable item offers find on music services instead of refresh`() {
        val track = AppMediaItemFixtures.track(name = "Song")

        composeTestRule.setInspectableContent {
            ItemDetails(
                state = trackState(track),
                fetchColors = NoColors,
                refreshAction = RefreshItemAction.FIND_ON_MUSIC_SERVICES,
            )
        }

        composeTestRule.onNodeWithContentDescription(Res.string.cd_more.get()).performClick()
        composeTestRule.onNodeWithText(Res.string.action_find_on_music_services.get()).assertIsDisplayed()
        composeTestRule.onNodeWithText(Res.string.action_refresh_item.get()).assertDoesNotExist()
    }

    @Test
    fun `no refresh entry without a refresh action`() {
        val track = AppMediaItemFixtures.track(name = "Song")

        composeTestRule.setInspectableContent {
            ItemDetails(state = trackState(track), fetchColors = NoColors)
        }

        composeTestRule.onNodeWithContentDescription(Res.string.cd_more.get()).performClick()
        composeTestRule.onNodeWithText(Res.string.action_refresh_item.get()).assertDoesNotExist()
        composeTestRule.onNodeWithText(Res.string.action_find_on_music_services.get()).assertDoesNotExist()
    }

    private fun trackState(
        track: Track,
        albumsState: DataState<List<Album>> = DataState.Data(emptyList()),
        trackVersionsState: DataState<List<Track>> = DataState.Data(emptyList()),
        similarTracksState: DataState<List<Track>> = DataState.NoData(),
        lyrics: Lyrics? = null,
    ) = ItemDetailsViewModel.State(
        itemState = DataState.Data(track),
        albumsState = albumsState,
        playableItemsState = DataState.NoData(),
        trackVersionsState = trackVersionsState,
        similarTracksState = similarTracksState,
        lyrics = lyrics,
    )
}
