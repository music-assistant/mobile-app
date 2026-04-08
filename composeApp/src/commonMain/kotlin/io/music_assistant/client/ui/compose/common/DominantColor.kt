package io.music_assistant.client.ui.compose.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.ui.graphics.Color
import com.kmpalette.extensions.network.rememberNetworkDominantColorState
import io.ktor.http.Url

@Composable
fun rememberAnimatedDominantColor(
    imageUrl: String?,
    fallback: Color,
): State<Color> {
    val dominantColorState = rememberNetworkDominantColorState(
        defaultColor = fallback,
        defaultOnColor = Color.White,
    )

    LaunchedEffect(imageUrl) {
        if (imageUrl != null) {
            try {
                dominantColorState.updateFrom(Url(imageUrl))
            } catch (_: Exception) {
                dominantColorState.reset()
            }
        } else {
            dominantColorState.reset()
        }
    }

    val extractedColor = Color(dominantColorState.color.value as ULong)
    val targetColor = extractedColor.takeIf {
        imageUrl != null && it != Color.Unspecified && it != Color.Transparent
    } ?: fallback

    return animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(durationMillis = 500)
    )
}
