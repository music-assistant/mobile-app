// Compose layout values (spacing, padding) are visual design tokens.
@file:Suppress("MagicNumber")

package io.music_assistant.client.wear.ui

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffoldDefaults
import androidx.wear.compose.material3.Text
import io.music_assistant.client.wear.R

/**
 * The cover on its own: no controls, no scrim, nothing over it but the clock and the two
 * lines that say what it is.
 *
 * The page draws no cover of its own - [NowPlayingPager] holds one behind both pages, so the
 * swipe uncovers it rather than sliding a second copy over the first. What is left here is
 * the title and the artist, at the bottom where they are out of the way of the art, and
 * nothing at all when there is no track to name. They fade in with [albumFraction] as the
 * page is pulled down, so the cover is bare at the end of the swipe and nothing has to be
 * read while it is moving.
 *
 * The cover is the play button. There is no control drawn on this page and nowhere on it a
 * tap could mean anything else, so the whole of it takes one - without a ripple, which over
 * a photograph reads as a flash rather than as a press, and with the tick the watch gives for
 * its own controls instead.
 *
 * The page is transparent, so on a low-bit ambient panel it is the cover behind it that is
 * dimmed and these two lines that stay legible over it.
 */
@Composable
fun AlbumArtPage(
    state: WearUiState,
    albumFraction: () -> Float,
    onPlayPause: () -> Unit,
    ambient: AmbientState = LocalAmbientState.current,
) {
    val view = LocalView.current
    val playing = state.nowPlaying?.isPlaying == true
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                enabled = state.isConnected && state.selected != null,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClickLabel = stringResource(if (playing) R.string.cd_pause else R.string.cd_play),
            ) {
                view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                onPlayPause()
            },
    ) {
        val track = state.nowPlaying ?: return@Box
        Column(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = if (albumFraction() < 0.4f) 0f else (albumFraction() - 0.4f) / 0.6f }
                .padding(bottom = ScreenScaffoldDefaults.contentPadding.calculateBottomPadding()),
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .roundScreenPadding()
                    .scrollingTitle(enabled = !ambient.isAmbient),
            )
            track.artist?.let { artist ->
                Text(
                    text = artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .roundScreenPadding(),
                )
            }
        }
    }
}
