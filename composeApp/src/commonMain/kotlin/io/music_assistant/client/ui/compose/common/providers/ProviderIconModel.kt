package io.music_assistant.client.ui.compose.common.providers

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import co.touchlab.kermit.Logger
import kotlin.io.encoding.Base64

/**
 * Sealed class representing different types of provider icons
 */
sealed class ProviderIconModel {
    /**
     * Pre-built [ImageVector] type - for app-local icons (e.g. the "library" bookshelf).
     */
    data class Mdi(val icon: ImageVector, val tint: Color = Color.White) : ProviderIconModel()

    /**
     * Material Design Icons community-pack glyph, referenced by its server-provided name
     * (e.g. "mdi-speaker"). Resolved to a font glyph at render time by [MdiIcon].
     */
    data class MdiGlyph(val name: String, val tint: Color = Color.White) : ProviderIconModel()

    /**
     * PNG type - contains decoded PNG bytes ready for Coil
     */
    data class Png(val imageBytes: ByteArray) : ProviderIconModel() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false
            other as Png
            return imageBytes.contentEquals(other.imageBytes)
        }

        override fun hashCode(): Int {
            return imageBytes.contentHashCode()
        }
    }

    /**
     * SVG type - contains SVG bytes ready for Coil
     */
    data class Svg(val svgBytes: ByteArray) : ProviderIconModel() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false
            other as Svg
            return svgBytes.contentEquals(other.svgBytes)
        }

        override fun hashCode(): Int {
            return svgBytes.contentHashCode()
        }
    }

    companion object Companion {
        /**
         * Factory method to create ProviderIconModel from a manifest's icon fields.
         *
         * Rules (highest fidelity first):
         * 1. If iconSvg is present, decode it (base64 PNG, else raw SVG bytes).
         * 2. Else if a MDI icon name is present, defer to a font glyph ([MdiGlyph]),
         *    resolved against the full MDI codepoint table at render time.
         * 3. If both are null, return null.
         */
        fun from(mdiIcon: String?, iconSvg: String?): ProviderIconModel? {
            iconSvg ?: return mdiIcon?.let { MdiGlyph(it) }
            return iconSvg.let { svgString ->
                val b64i = svgString.indexOf("base64,")
                if (b64i > 0) {
                    val base64Data = iconSvg.substring(b64i + 7) // Skip "base64,"
                    // Remove any closing quotes or XML tags
                    val cleanedData = base64Data.substringBefore("\"").substringBefore("<")
                    try {
                        val bytes = Base64.Default.decode(cleanedData)
                        Png(bytes)
                    } catch (e: Exception) {
                        Logger.e("Cannot decode base64 PNG: ${e.message}")
                        null
                    }
                } else {
                    try {
                        val svgBytes = svgString.encodeToByteArray()
                        Svg(svgBytes)
                    } catch (e: Exception) {
                        Logger.e("Cannot encode SVG to bytes: ${e.message}")
                        null
                    }
                }
            }
        }
    }
}
