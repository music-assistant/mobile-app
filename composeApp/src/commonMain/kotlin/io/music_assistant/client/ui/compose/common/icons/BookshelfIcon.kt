package io.music_assistant.client.ui.compose.common.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val BookshelfIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Bookshelf",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(9f, 3f)
            verticalLineTo(18f)
            horizontalLineTo(12f)
            verticalLineTo(3f)
            horizontalLineTo(9f)
            moveTo(12f, 5f)
            lineTo(16f, 18f)
            lineTo(19f, 17f)
            lineTo(15f, 4f)
            lineTo(12f, 5f)
            moveTo(5f, 5f)
            verticalLineTo(18f)
            horizontalLineTo(8f)
            verticalLineTo(5f)
            horizontalLineTo(5f)
            moveTo(3f, 19f)
            verticalLineTo(21f)
            horizontalLineTo(21f)
            verticalLineTo(19f)
            horizontalLineTo(3f)
            close()
        }
    }.build()
}
