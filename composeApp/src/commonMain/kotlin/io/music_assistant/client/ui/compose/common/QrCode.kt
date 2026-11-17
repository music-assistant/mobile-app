package io.music_assistant.client.ui.compose.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.github.alexzhirkevich.qrose.QroseEncoders
import io.github.alexzhirkevich.qrose.matrix.QR
import io.github.alexzhirkevich.qrose.matrix.qr.QrErrorCorrection
import kotlin.math.floor

/** The light border around the symbol, in modules. Scanners need 4. */
private const val QUIET_ZONE_MODULES = 4

/**
 * A square QR code of [data]. It is always black on white, in both themes, because many
 * scanners do not read an inverted code. Draws nothing if [data] does not fit in a QR code.
 */
@Composable
fun QrCode(data: String, contentDescription: String, modifier: Modifier = Modifier) {
    val matrix = remember(data) {
        runCatching { QroseEncoders.QR(QrErrorCorrection.M).encode(data) }.getOrNull()
    } ?: return
    Canvas(
        modifier = modifier
            .aspectRatio(1f)
            .background(Color.White)
            .semantics { this.contentDescription = contentDescription },
    ) {
        val modules = matrix.width + 2 * QUIET_ZONE_MODULES
        // Whole pixels per module, so the modules leave no hairline seams between them.
        val cell = floor(size.minDimension / modules)
        val origin = Offset(
            x = (size.width - cell * modules) / 2 + cell * QUIET_ZONE_MODULES,
            y = (size.height - cell * modules) / 2 + cell * QUIET_ZONE_MODULES,
        )
        val module = Size(cell, cell)
        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                if (matrix[x, y]) drawRect(Color.Black, origin + Offset(x * cell, y * cell), module)
            }
        }
    }
}
