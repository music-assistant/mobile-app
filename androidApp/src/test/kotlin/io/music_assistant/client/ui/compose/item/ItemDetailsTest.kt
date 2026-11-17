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
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.music_assistant.client.data.model.client.AlbumType
import io.music_assistant.client.data.model.client.AppMediaItemFixtures
import io.music_assistant.client.data.model.client.Lyrics
import io.music_assistant.client.data.model.client.MediaType
import io.music_assistant.client.data.model.client.items.Album
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.client.items.PlaylistAccessPart
import io.music_assistant.client.data.model.client.items.PlaylistOwnerLine
import io.music_assistant.client.data.model.client.items.PodcastEpisode
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
import musicassistantclient.composeapp.generated.resources.action_go_to_episode
import musicassistantclient.composeapp.generated.resources.action_go_to_podcast
import musicassistantclient.composeapp.generated.resources.action_go_to_track
import musicassistantclient.composeapp.generated.resources.action_lyrics
import musicassistantclient.composeapp.generated.resources.action_refresh_item
import musicassistantclient.composeapp.generated.resources.action_transcript
import musicassistantclient.composeapp.generated.resources.album_type_ep
import musicassistantclient.composeapp.generated.resources.cd_more
import musicassistantclient.composeapp.generated.resources.cd_refreshing_item
import musicassistantclient.composeapp.generated.resources.episode_number_season
import musicassistantclient.composeapp.generated.resources.item_description_more
import musicassistantclient.composeapp.generated.resources.item_duration_minutes
import musicassistantclient.composeapp.generated.resources.item_narrated_by
import musicassistantclient.composeapp.generated.resources.item_publisher
import musicassistantclient.composeapp.generated.resources.item_section_review
import musicassistantclient.composeapp.generated.resources.item_tab_other_episodes
import musicassistantclient.composeapp.generated.resources.item_tab_similar_tracks
import musicassistantclient.composeapp.generated.resources.item_tab_versions
import musicassistantclient.composeapp.generated.resources.item_track_count
import musicassistantclient.composeapp.generated.resources.media_type_albums
import musicassistantclient.composeapp.generated.resources.playlist_access_collaborative
import musicassistantclient.composeapp.generated.resources.playlist_access_personal
import musicassistantclient.composeapp.generated.resources.playlist_sharing_selected
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

        composeTestRule.onAllNodes(hasText("Best Version")).onFirst().assertIsDisplayed()
    }

    @Test
    fun `displays album year`() {
        val artist = AppMediaItemFixtures.artist()
        val album = AppMediaItemFixtures.album(artist = artist, year = 1992)

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

        composeTestRule.onAllNodes(hasText("1992", substring = true)).onFirst().assertIsDisplayed()
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
        composeTestRule.onNodeWithContentDescription(Res.string.cd_refreshing_item.get())
            .assertIsDisplayed()
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
        composeTestRule.onNodeWithText(Res.string.action_find_on_music_services.get())
            .assertIsDisplayed()
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
        composeTestRule.onNodeWithText(Res.string.action_find_on_music_services.get())
            .assertDoesNotExist()
    }

    @Test
    fun `album header shows the type and the track count and the duration and the review`() {
        val album = AppMediaItemFixtures.album().copy(
            albumType = AlbumType.EP,
            metadata = AppMediaItemFixtures.metadata(review = "A short review."),
        )
        val tracks = listOf(
            AppMediaItemFixtures.track().copy(duration = 200.0),
            AppMediaItemFixtures.track().copy(duration = 220.0),
        )

        composeTestRule.setInspectableContent {
            ItemDetails(state = albumState(album, tracks), fetchColors = NoColors)
        }

        val facts = listOf(
            Res.string.album_type_ep.get(),
            Res.plurals.item_track_count.get(2, 2),
            Res.string.item_duration_minutes.get(7),
        ).joinToString(" · ")
        composeTestRule.onNodeWithText(facts).assertIsDisplayed()
        composeTestRule.onNodeWithText(Res.string.item_section_review.get()).performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("A short review.").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText(Res.string.item_description_more.get()).assertDoesNotExist()
    }

    @Test
    fun `item without a text shows no block`() {
        composeTestRule.setInspectableContent {
            ItemDetails(state = albumState(AppMediaItemFixtures.album()), fetchColors = NoColors)
        }

        composeTestRule.onNodeWithText(Res.string.item_section_review.get()).assertDoesNotExist()
    }

    @Test
    fun `genre chip opens the genre`() {
        val genre = AppMediaItemFixtures.genre(name = "Rock")
        var navigatedTo: AppMediaItem? = null

        composeTestRule.setInspectableContent {
            ItemHeader(
                item = AppMediaItemFixtures.artist(),
                genres = listOf(genre),
                navigateToItem = { navigatedTo = it },
            )
        }

        composeTestRule.onNodeWithText("Rock").performClick()

        assertEquals(genre, navigatedTo)
    }

    @Test
    fun `playlist header shows its owner line`() {
        composeTestRule.setInspectableContent {
            ItemHeader(
                item = AppMediaItemFixtures.playlist(),
                ownerLine = PlaylistOwnerLine.Access(
                    listOf(
                        PlaylistAccessPart.Personal,
                        PlaylistAccessPart.SharedWith(2),
                        PlaylistAccessPart.Collaborative,
                    ),
                ),
            )
        }

        val line = listOf(
            Res.string.playlist_access_personal.get(),
            Res.plurals.playlist_sharing_selected.get(2, 2),
            Res.string.playlist_access_collaborative.get(),
        ).joinToString(" · ")
        composeTestRule.onNodeWithText(line).assertIsDisplayed()
    }

    @Test
    fun `audiobook header shows the narrators and the publisher`() {
        val book = AppMediaItemFixtures.audiobook()
            .copy(narrators = listOf("Ann", "Bob"), publisher = "Pub House")

        composeTestRule.setInspectableContent { ItemHeader(item = book) }

        composeTestRule.onNodeWithText(Res.string.item_narrated_by.get("Ann, Bob"))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(Res.string.item_publisher.get("Pub House"))
            .assertIsDisplayed()
    }

    @Test
    fun `episode header shows the podcast and the other episodes and opens the podcast`() {
        val podcast = AppMediaItemFixtures.podcast(name = "The Show")
        val episode = AppMediaItemFixtures.episode(name = "Pilot", podcast = podcast, season = 1, episodeNumber = 3)
        val other = AppMediaItemFixtures.episode(name = "Second", podcast = podcast)
        var navigatedTo: Pair<String, MediaType>? = null

        composeTestRule.setInspectableContent {
            ItemDetails(
                state = episodeState(episode, otherEpisodes = listOf(other)),
                onNavigateToItem = { id, type, _ -> navigatedTo = id to type },
                fetchColors = NoColors,
            )
        }

        composeTestRule.onNodeWithText(Res.string.episode_number_season.get(1, 3), substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("The Show").performClick()
        // The taller episode header pushes the tabs below the fold of the test window.
        composeTestRule.inScrollable("LazyVerticalGrid") {
            onNode(isTab(Res.string.item_tab_other_episodes.get())).assertIsDisplayed()
            onNode(hasContentDescription(other.name, substring = true)).assertIsDisplayed()
        }
        assertEquals(podcast.itemId to MediaType.PODCAST, navigatedTo)
    }

    @Test
    fun `episode overflow opens the transcript and goes to the podcast but not to the episode itself`() {
        val episode = AppMediaItemFixtures.episode(name = "Pilot")

        composeTestRule.setInspectableContent {
            ItemDetails(
                state = episodeState(episode, lyrics = Lyrics.Plain("First line of the transcript")),
                fetchColors = NoColors,
            )
        }

        composeTestRule.onNodeWithContentDescription(Res.string.cd_more.get()).performClick()
        composeTestRule.onNodeWithText(Res.string.action_go_to_podcast.get()).assertIsDisplayed()
        composeTestRule.onNodeWithText(Res.string.action_go_to_episode.get()).assertDoesNotExist()
        composeTestRule.onNodeWithText(Res.string.action_lyrics.get()).assertDoesNotExist()
        composeTestRule.onNodeWithText(Res.string.action_transcript.get()).performClick()

        composeTestRule.onNodeWithText("First line of the transcript").assertIsDisplayed()
    }

    @Test
    fun `episode without a transcript has no transcript entry`() {
        composeTestRule.setInspectableContent {
            ItemDetails(state = episodeState(AppMediaItemFixtures.episode()), fetchColors = NoColors)
        }

        composeTestRule.onNodeWithContentDescription(Res.string.cd_more.get()).performClick()
        composeTestRule.onNodeWithText(Res.string.action_transcript.get()).assertDoesNotExist()
    }

    private fun albumState(
        album: Album,
        tracks: List<Track> = emptyList(),
    ) = ItemDetailsViewModel.State(
        itemState = DataState.Data(album),
        albumsState = DataState.NoData(),
        playableItemsState = DataState.Data(tracks),
    )

    private fun episodeState(
        episode: PodcastEpisode,
        otherEpisodes: List<PodcastEpisode> = emptyList(),
        lyrics: Lyrics? = null,
    ) = ItemDetailsViewModel.State(
        itemState = DataState.Data(episode),
        albumsState = DataState.NoData(),
        playableItemsState = DataState.Data(otherEpisodes),
        lyrics = lyrics,
    )

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
