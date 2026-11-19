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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.MaterialTheme
import io.music_assistant.client.wear.data.Artwork

/**
 * The cover art behind the controls: blurred, dimmed, and covered by a scrim that darkens
 * towards the edges, so the text and the buttons stay readable over any album.
 */
@Composable
fun ArtworkBackground(artwork: Artwork?, modifier: Modifier = Modifier) {
    val background = MaterialTheme.colorScheme.background
    Box(modifier) {
        Crossfade(targetState = artwork, animationSpec = tween(400), label = "artwork") { current ->
            current?.let {
                Image(
                    bitmap = it.image,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(1.dp)
                        .alpha(0.6f),
                )
            }
        }
        Box(
            Modifier
                .fillMaxSize()
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
