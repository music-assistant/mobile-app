package io.music_assistant.client.ui.compose.common.providers

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Hardware
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NetworkWifi
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import co.touchlab.kermit.Logger
import compose.icons.TablerIcons
import compose.icons.tablericons.Microphone
import kotlin.io.encoding.Base64

/**
 * Sealed class representing different types of provider icons
 */
sealed class ProviderIconModel {
    /**
     * MDI (Material Design Icon) type - uses TablerIcons
     */
    data class Mdi(val icon: ImageVector, val tint: Color = Color.White) : ProviderIconModel()

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
         * Factory method to create ProviderIconType from ProviderManifest
         *
         * Rules:
         * 1. If icon is not null, use corresponding MDI Icon
         * 2. Else if iconSvg contains "base64", extract and decode base64 PNG
         * 3. Otherwise, encode iconSvg as SVG bytes
         * 4. If both are null, return null
         */
        fun from(mdiIcon: String?, iconSvg: String?): ProviderIconModel? {
            // Rule 1: If icon is not null, use MDI icon
            if (mdiIcon != null) {
                val icon = when (mdiIcon) {
                    "harddisk" -> Icons.Default.Hardware
                    "network" -> Icons.Default.NetworkWifi
                    "podcast" -> TablerIcons.Microphone
                    "mdi:radio",
                    "radio",
                    -> Icons.Default.Mic
                    else -> {
                        Logger.e("Cannot find MDI icon for $mdiIcon.")
                        return null
                    }
                }
                return Mdi(icon)
            }
            return iconSvg?.let { svgString ->
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
