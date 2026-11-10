package io.music_assistant.client.ui.compose.common.providers

import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Data
import org.jetbrains.skia.Surface
import org.jetbrains.skia.svg.SVGDOM
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals

/** Coil decodes SVG with Skia SVGDOM on iOS, which ignores `<style>` blocks. */
class ProviderSvgSkiaRenderTest {
    @Test
    fun plexIconRendersItsClassColorsWithSkia() {
        val dataUri = "data:image/svg+xml;base64," + Base64.encode(PLEX_ICON.encodeToByteArray())
        val model = ProviderIconModel.fromSvg(dataUri) as ProviderIconModel.Svg

        val bitmap = render(model.svgBytes)

        assertEquals(0xFF282A2Du, bitmap.getColor(10, 61).toUInt(), "background")
        assertEquals(0xFFE5A00Du, bitmap.getColor(74, 61).toUInt(), "chevron")
    }

    private fun render(svg: ByteArray): Bitmap {
        val dom = SVGDOM(Data.makeFromBytes(svg))
        dom.setContainerSize(SIZE.toFloat(), SIZE.toFloat())
        val surface = Surface.makeRasterN32Premul(SIZE, SIZE)
        dom.render(surface.canvas)
        return Bitmap().apply {
            allocN32Pixels(SIZE, SIZE)
            surface.readPixels(this, 0, 0)
        }
    }

    private companion object {
        const val SIZE = 123

        // music_assistant/providers/plex/icon.svg as the server sends it (newlines removed).
        const val PLEX_ICON = """<?xml version="1.0" encoding="utf-8"?><svg version="1.1" id="Layer_1" xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" x="0px" y="0px" viewBox="0 0 122.88 122.88" style="enable-background:new 0 0 122.88 122.88" xml:space="preserve"><style type="text/css"><![CDATA[	.st0{fill:#E5A00D;}	.st1{fill:#282A2D;}]]></style><g><path class="st1" d="M18.43,0h86.02c10.18,0,18.43,8.25,18.43,18.43v86.02c0,10.18-8.25,18.43-18.43,18.43H18.43 C8.25,122.88,0,114.63,0,104.45l0-86.02C0,8.25,8.25,0,18.43,0L18.43,0z"/><polygon class="st0" points="61.44,16.8 35.52,16.8 61.44,61.44 35.52,106.08 61.44,106.08 87.36,61.44 61.44,16.8"/></g></svg>"""
    }
}
