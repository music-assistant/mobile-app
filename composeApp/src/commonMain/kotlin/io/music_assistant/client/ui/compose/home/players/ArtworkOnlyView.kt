package io.music_assistant.client.ui.compose.home.players

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import coil3.compose.AsyncImage
import io.music_assistant.client.data.model.client.PlayerData
import io.music_assistant.client.imageloader.rememberArtworkRequest
import io.music_assistant.client.imageloader.rememberBackdropArtworkRequest
import io.music_assistant.client.nav.BackHandler
import io.music_assistant.client.ui.compose.common.icons.AlbumIcon
import io.music_assistant.client.utils.SystemBarsHidden
import kotlinx.coroutines.delay
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.action_show_player_controls
import org.jetbrains.compose.resources.stringResource
import kotlin.math.PI
import kotlin.math.cos

private const val COVER_FRACTION = 0.9f
private val CoverShape = RoundedCornerShape(16.dp)
private const val CROSSFADE_MS = 800

// As blurred as the web player's backdrop: CSS blur(56px) is a standard deviation, while a
// Compose blur radius is about 1.7 standard deviations.
private val BackdropBlur = 96.dp

// Livelier and darker than the cover, so the cover stands out from its own colors.
private val BackdropColorFilter = ColorFilter.colorMatrix(
    ColorMatrix().apply {
        setToSaturation(1.7f)
        timesAssign(ColorMatrix().apply { setToScale(0.68f, 0.68f, 0.68f, 1f) })
    },
)
private val Scrim = Color(0xFF0B0B0F).copy(alpha = 0.45f)

// One breath takes nine seconds: the backdrop swells by a few percent while the scrim over it
// thins, then both settle again.
private const val BREATH_PERIOD_MS = 9_000f
private const val BREATH_STEP_MS = 100L
private const val BREATH_SWELL = 0.06f
private const val BREATH_SCRIM_THINNING = 0.2f

/**
 * The cover of what [player] plays, alone on the screen: large, over a blurred and darkened copy
 * of itself. A tap anywhere or back leaves it, and the cover cross-fades when the music changes.
 *
 * It is there to be looked at, so the system bars hide and, while the music plays, the backdrop
 * slowly breathes. Paused, it holds still.
 */
@Composable
fun ArtworkOnlyView(
    player: PlayerData,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val media = player.player.currentMedia
    val isPlaying = player.player.isPlaying
    BackHandler(onBack = onDismiss)
    SystemBarsHidden()
    val breath = rememberBreath(isPlaying)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = null,
                indication = null,
                onClickLabel = stringResource(Res.string.action_show_player_controls),
                onClick = onDismiss,
            ),
    ) {
        Crossfade(targetState = media?.imageUrl, animationSpec = tween(CROSSFADE_MS)) { url ->
            BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                url?.let {
                    AsyncImage(
                        model = rememberBackdropArtworkRequest(it),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        colorFilter = BackdropColorFilter,
                        modifier = Modifier
                            .fillMaxSize()
                            // Outside the blur, so the breath moves the blurred picture instead
                            // of blurring it anew.
                            .graphicsLayer {
                                val scale = 1f + BREATH_SWELL * breath.floatValue
                                scaleX = scale
                                scaleY = scale
                            }
                            .blur(BackdropBlur, BlurredEdgeTreatment.Rectangle),
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = 1f - BREATH_SCRIM_THINNING * breath.floatValue }
                        .background(Scrim),
                )
                Box(
                    modifier = Modifier
                        .size(min(maxWidth, maxHeight) * COVER_FRACTION)
                        .shadow(elevation = 24.dp, shape = CoverShape)
                        .clip(CoverShape),
                    contentAlignment = Alignment.Center,
                ) {
                    url?.let {
                        AsyncImage(
                            model = rememberArtworkRequest(it),
                            contentDescription = media?.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } ?: Icon(
                        imageVector = media?.defaultIcon ?: AlbumIcon,
                        contentDescription = null,
                        modifier = Modifier.size(120.dp),
                        tint = Color.White.copy(alpha = 0.6f),
                    )
                }
            }
        }
    }
}

/**
 * How far into a breath the backdrop is, from 0 at rest to 1 at its fullest. It moves only while
 * [playing]: paused, it holds where it is, and the music picks it up from there. It holds still
 * as well when the system asks for no animations.
 *
 * Ten steps a second: the swell is slow and the backdrop blurred, so more would look the same
 * and only cost the battery frames. Each step waits for a frame, so nothing runs while the app is
 * not on screen.
 */
@Composable
private fun rememberBreath(playing: Boolean): FloatState {
    val breath = remember { mutableFloatStateOf(0f) }
    var phase by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(playing) {
        if (!playing || coroutineContext[MotionDurationScale]?.scaleFactor == 0f) return@LaunchedEffect
        var last = withFrameMillis { it }
        while (true) {
            delay(BREATH_STEP_MS)
            val now = withFrameMillis { it }
            phase = (phase + (now - last) / BREATH_PERIOD_MS) % 1f
            last = now
            breath.floatValue = (1f - cos(2 * PI * phase).toFloat()) / 2f
        }
    }
    return breath
}
