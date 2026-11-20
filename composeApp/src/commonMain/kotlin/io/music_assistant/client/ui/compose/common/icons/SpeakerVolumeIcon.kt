package io.music_assistant.client.ui.compose.common.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.PathData
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * A speaker with a volume badge: the button that opens the volume of one speaker. The cabinet
 * tells it apart from [VolumeIcon], which is the mute toggle.
 */
val SpeakerVolumeIcon: ImageVector by lazy {
    speakerWithBadge(name = "SpeakerVolume") {
        // Sound waves
        moveTo(19f, 17f)
        arcTo(1.6f, 1.6f, 0f, false, true, 19f, 20f)
        moveTo(21f, 15.5f)
        arcTo(3.5f, 3.5f, 0f, false, true, 21f, 21.5f)
    }
}

/** [SpeakerVolumeIcon] for a muted speaker: the badge has a cross instead of the waves. */
val SpeakerMutedIcon: ImageVector by lazy {
    speakerWithBadge(name = "SpeakerMuted") {
        moveTo(19f, 16.5f)
        lineTo(23f, 20.5f)
        moveTo(23f, 16.5f)
        lineTo(19f, 20.5f)
    }
}

private const val STROKE = 1.5f

private fun speakerWithBadge(name: String, badgeMark: PathBuilder.() -> Unit): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        // The cabinet leaves a gap around the badge: the inner outline runs against the outer
        // one, so the non-zero fill rule makes it a hole.
        group(
            clipPathData = PathData {
                moveTo(0f, 0f)
                verticalLineTo(24f)
                horizontalLineTo(24f)
                verticalLineTo(0f)
                close()
                moveTo(11f, 14.75f)
                horizontalLineTo(13.75f)
                lineTo(17.25f, 11.75f)
                horizontalLineTo(24f)
                verticalLineTo(24f)
                horizontalLineTo(11f)
                close()
            },
        ) {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                // Cabinet
                moveTo(5.5f, 2f)
                horizontalLineTo(16.5f)
                arcTo(2.5f, 2.5f, 0f, false, true, 19f, 4.5f)
                verticalLineTo(19.5f)
                arcTo(2.5f, 2.5f, 0f, false, true, 16.5f, 22f)
                horizontalLineTo(5.5f)
                arcTo(2.5f, 2.5f, 0f, false, true, 3f, 19.5f)
                verticalLineTo(4.5f)
                arcTo(2.5f, 2.5f, 0f, false, true, 5.5f, 2f)
                close()
                // Woofer, centered in the cabinet
                moveTo(11f, 8.5f)
                arcTo(4f, 4f, 0f, true, true, 11f, 16.5f)
                arcTo(4f, 4f, 0f, true, true, 11f, 8.5f)
                close()
                // Tweeter: a round-capped dot
                moveTo(11f, 5f)
                lineTo(11.01f, 5f)
            }
        }
        // Badge: a speaker and its mark
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = STROKE,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(17f, 14f)
            lineTo(14.5f, 16.5f)
            horizontalLineTo(13f)
            verticalLineTo(20.5f)
            horizontalLineTo(14.5f)
            lineTo(17f, 23f)
            close()
            badgeMark()
        }
    }.build()
