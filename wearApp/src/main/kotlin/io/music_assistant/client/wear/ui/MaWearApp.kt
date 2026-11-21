package io.music_assistant.client.wear.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController

private object Routes {
    const val NOW_PLAYING = "now_playing"
    const val VOLUME = "volume"
    const val OUTPUT = "output"
}

/**
 * The whole watch app: a now-playing root, with the volume stepper pushed on top of it and
 * the output picker one step further still. Each is dismissed with the system swipe, which is
 * what a Wear user expects instead of a back button.
 *
 * The queue is not one of those screens. It is a sheet over the now-playing root, dragged by
 * the handle at the top of the queue itself - see [QueueSheet] - so the track it belongs to
 * is still there behind it while it moves.
 *
 * The colour scheme is rebuilt from each cover, so the controls take on the album — except
 * on a low-bit ambient panel, which has too few colours to render a seeded scheme as anything
 * but dithering, and falls back to Wear's own.
 *
 * The always-on screen is always the root: the wrist drops while the volume stepper or the
 * queue is up, and what stays lit for the next hours should be the track, not a list nobody
 * can scroll. Entering ambient therefore pops back to the root and puts the queue sheet away.
 * It does not touch the pager, though - a wrist that drops on the cover is meant to keep it.
 */
@Composable
fun MaWearApp(viewModel: WearPlayerViewModel, ambient: AmbientState = AmbientState.Interactive) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val seed = state.artwork?.seedArgb
        .takeUnless { (ambient as? AmbientState.Ambient)?.lowBitAmbient == true }
    val colorScheme = remember(seed) { seed?.let(::artworkColorScheme) ?: ColorScheme() }

    MaterialTheme(colorScheme = colorScheme) {
        CompositionLocalProvider(LocalAmbientState provides ambient) {
            val navController = rememberSwipeDismissableNavController()
            val queueSheet = rememberQueueSheetState()
            // Hoisted: the queue sheet is not one of the pages, but its handle belongs to the
            // controls and goes off the bottom with them.
            val pagerState = rememberPagerState(initialPage = NOW_PLAYING_PAGE) {
                NOW_PLAYING_PAGE_COUNT
            }
            val screenHeightPx = with(LocalDensity.current) {
                LocalConfiguration.current.screenHeightDp.dp.roundToPx()
            }
            // Read here, in the scope that the activity recomposes: a destination lambda
            // holds the value it was built with and would never see the change.
            LaunchedEffect(navController, ambient.isAmbient) {
                if (ambient.isAmbient) {
                    queueSheet.close()
                    navController.popBackStack(Routes.NOW_PLAYING, inclusive = false)
                }
            }
            AppScaffold {
                SwipeDismissableNavHost(
                    navController = navController,
                    startDestination = Routes.NOW_PLAYING,
                ) {
                    composable(Routes.NOW_PLAYING) {
                        Box(Modifier.fillMaxSize()) {
                            NowPlayingPager(
                                state = state,
                                pagerState = pagerState,
                                onPlayPause = viewModel::togglePlayPause,
                                onNext = viewModel::next,
                                onPrevious = viewModel::previous,
                                onOpenVolume = { navController.navigate(Routes.VOLUME) },
                            )
                            QueueSheet(
                                state = state,
                                sheetState = queueSheet,
                                swipeAwayPx = {
                                    (pagerState.albumFraction() * screenHeightPx).toInt()
                                },
                                onPlayItem = viewModel::playQueueItem,
                                onRemoveItem = viewModel::removeQueueItem,
                            )
                        }
                    }
                    composable(Routes.VOLUME) {
                        VolumeScreen(
                            state = state,
                            onSetVolume = viewModel::setVolume,
                            onOpenOutput = { navController.navigate(Routes.OUTPUT) },
                        )
                    }
                    composable(Routes.OUTPUT) {
                        // Picking a player goes back to the volume screen: the volume there is
                        // the volume of whatever was just picked, which is usually the next thing
                        // the user reaches for. A transfer lands in the same place, and for the
                        // same reason - it moves the sound to that player as well.
                        OutputScreen(
                            state = state,
                            onSelect = {
                                viewModel.selectPlayer(it)
                                navController.popBackStack()
                            },
                            onTransfer = {
                                viewModel.transferPlayback(it)
                                navController.popBackStack()
                            },
                        )
                    }
                }
            }
        }
    }
}
