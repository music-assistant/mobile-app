package io.music_assistant.client.ui.compose.common.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val SpeakerMultipleIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "SpeakerMultiple",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            // Inner circle (small driver)
            moveTo(14f, 10f)
            arcTo(3f, 3f, 0f, false, false, 11f, 13f)
            arcTo(3f, 3f, 0f, false, false, 14f, 16f)
            arcTo(3f, 3f, 0f, false, false, 17f, 13f)
            arcTo(3f, 3f, 0f, false, false, 14f, 10f)
            // Outer circle (large driver)
            moveTo(14f, 18f)
            arcTo(5f, 5f, 0f, false, true, 9f, 13f)
            arcTo(5f, 5f, 0f, false, true, 14f, 8f)
            arcTo(5f, 5f, 0f, false, true, 19f, 13f)
            arcTo(5f, 5f, 0f, false, true, 14f, 18f)
            // Tweeter
            moveTo(14f, 2f)
            arcTo(2f, 2f, 0f, false, true, 16f, 4f)
            arcTo(2f, 2f, 0f, false, true, 14f, 6f)
            arcTo(2f, 2f, 0f, false, true, 12f, 4f)
            arcTo(2f, 2f, 0f, false, true, 14f, 2f)
            // Front cabinet
            moveTo(19f, 0f)
            horizontalLineTo(9f)
            arcTo(2f, 2f, 0f, false, false, 7f, 2f)
            verticalLineTo(18f)
            arcTo(2f, 2f, 0f, false, false, 9f, 20f)
            horizontalLineTo(19f)
            arcTo(2f, 2f, 0f, false, false, 21f, 18f)
            verticalLineTo(2f)
            arcTo(2f, 2f, 0f, false, false, 19f, 0f)
            // Back cabinet
            moveTo(5f, 22f)
            horizontalLineTo(17f)
            verticalLineTo(24f)
            horizontalLineTo(5f)
            arcTo(2f, 2f, 0f, false, true, 3f, 22f)
            verticalLineTo(4f)
            horizontalLineTo(5f)
        }
    }.build()
}
