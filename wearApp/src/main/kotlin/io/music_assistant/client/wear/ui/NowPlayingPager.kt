package io.music_assistant.client.wear.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.wear.compose.foundation.pager.PagerState
import androidx.wear.compose.foundation.pager.VerticalPager
import androidx.wear.compose.material3.VerticalPagerScaffold

/** The cover, which a swipe down uncovers. It sits above the controls, so its dot does too. */
internal const val ALBUM_PAGE = 0

/** The controls, and where the watch starts. */
internal const val NOW_PLAYING_PAGE = 1

internal const val NOW_PLAYING_PAGE_COUNT = 2

/**
 * How far the album page has been swiped in: 0 while the controls are up, 1 while the cover
 * is, and everything between while a finger is on it.
 *
 * The queue sheet reads it as well as this screen does - its handle belongs to the controls
 * and goes down with them - which is why it is a function of the pager rather than a flag.
 */
internal fun PagerState.albumFraction(): Float =
    (NOW_PLAYING_PAGE - (currentPage + currentPageOffsetFraction)).coerceIn(0f, 1f)

/**
 * The two faces of the watch's main screen: the controls, and the cover art on its own.
 *
 * A swipe down takes the controls off the bottom of the screen and leaves the cover, which is
 * why the cover is drawn *behind* the pager rather than inside a page of it. The pages slide
 * and the cover does not, so swiping down uncovers it - losing the blur and the scrim that
 * only ever existed to keep text readable over it - instead of carrying a second copy of the
 * same image in from the top. The two dots ride the right rim, the cover's above the
 * controls', because that is the direction the swipe goes.
 *
 * The queue is not a page here. It is a sheet over the whole of this, and it covers the dots
 * along with everything else when it is up.
 */
@Composable
fun NowPlayingPager(
    state: WearUiState,
    pagerState: PagerState,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onOpenVolume: () -> Unit,
    ambient: AmbientState = LocalAmbientState.current,
) {
    Box(Modifier.fillMaxSize()) {
        // Held in ambient only on the album page, which is the one page that is nothing but
        // the cover. A full-screen image is otherwise the one thing an OLED watch must not
        // hold up for an hour, so the controls page still goes dark behind them.
        if (!ambient.isAmbient || pagerState.currentPage == ALBUM_PAGE) {
            ArtworkBackground(
                artwork = state.artwork,
                modifier = Modifier.fillMaxSize(),
                veil = { 1f - pagerState.albumFraction() },
            )
        }
        VerticalPagerScaffold(pagerState = pagerState) {
            VerticalPager(
                state = pagerState,
                // The crown turns no pages: the queue claims it while it is up, and on this
                // screen it has never done anything.
                rotaryScrollableBehavior = null,

            ) { page ->
                when (page) {
                    ALBUM_PAGE -> AlbumArtPage(
                        state = state,
                        albumFraction = pagerState::albumFraction,
                        onPlayPause = onPlayPause,
                    )
                    else -> NowPlayingScreen(
                        state = state,
                        onPlayPause = onPlayPause,
                        onNext = onNext,
                        onPrevious = onPrevious,
                        onOpenVolume = onOpenVolume,
                    )
                }
            }
        }
    }
}
