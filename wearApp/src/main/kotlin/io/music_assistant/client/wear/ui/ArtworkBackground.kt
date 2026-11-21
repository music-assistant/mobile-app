// Compose layout and opacity values are visual design tokens.
@file:Suppress("MagicNumber")

package io.music_assistant.client.wear.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.MaterialTheme
import io.music_assistant.client.wear.data.Artwork

/**
 * The cover art behind the controls: blurred, dimmed, and covered by a scrim that darkens
 * towards the edges, so the text and the buttons stay readable over any album.
 *
 * [veil] is how much of that treatment to apply: 1 behind the controls, 0 on the album page,
 * where there is nothing left to keep readable and the cover is the point. The scrim's alpha
 * is read in the draw phase, so swiping between the two costs no recomposition; the blur has
 * to be a modifier value, so it is quantised to tenths of a dp and changes a handful of times
 * across the swipe instead of once a frame.
 */
@Composable
fun ArtworkBackground(
    artwork: Artwork?,
    modifier: Modifier = Modifier,
    veil: () -> Float = { 1f },
) {
    val background = MaterialTheme.colorScheme.background
    val blur by remember(veil) {
        derivedStateOf { (BLUR_RADIUS * veil()).let { (it.value * 10).toInt() / 10f }.dp }
    }
    Box(modifier) {
        Crossfade(targetState = artwork, animationSpec = tween(400), label = "artwork") { current ->
            current?.let {
                Image(
                    bitmap = it.image,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(blur),
                )
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = veil() }
                .background(
                    Brush.radialGradient(
                        0.0f to background.copy(alpha = 0.25f),
                        0.6f to background.copy(alpha = 0.55f),
                        1.0f to background.copy(alpha = 0.9f),
                    ),
                ),
        )
    }
}

/** How far the cover is blurred behind the controls. */
private val BLUR_RADIUS = 2.dp
