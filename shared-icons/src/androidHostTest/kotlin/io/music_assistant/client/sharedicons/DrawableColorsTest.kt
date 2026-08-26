package io.music_assistant.client.sharedicons

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Guards the drawables against SVG-isms that survive an Android Studio "Vector Asset" import.
 *
 * The Compose Resources vector parser accepts only `#`-hex color literals. A leftover
 * `currentColor` compiles and lints fine, then throws `IllegalArgumentException` the moment
 * `painterResource` loads the drawable at runtime. This test turns that crash into a build
 * failure. See the shared-icons README for the import procedure.
 */
class DrawableColorsTest {
    @Test
    fun everyDrawableColorIsHexLiteral() {
        val files = drawableDir().listFiles { file -> file.extension == "xml" }?.sorted().orEmpty()
        assertTrue(files.isNotEmpty(), "No drawables found in ${drawableDir().absolutePath}")

        val offenders = files.flatMap { file ->
            COLOR_ATTRIBUTE.findAll(file.readText())
                .map { it.groupValues[1] }
                .filterNot { it.matches(HEX_COLOR) }
                .map { "${file.name}: $it" }
        }
        assertTrue(
            offenders.isEmpty(),
            "Non-hex color values (Compose Resources cannot parse these):\n" +
                offenders.joinToString("\n"),
        )
    }

    private fun drawableDir(): File =
        listOf(DRAWABLE_PATH, "shared-icons/$DRAWABLE_PATH")
            .map { File(it) }
            .firstOrNull { it.isDirectory }
            ?: File(DRAWABLE_PATH)

    private companion object {
        const val DRAWABLE_PATH = "src/commonMain/composeResources/drawable"
        val COLOR_ATTRIBUTE = Regex("""android:\w*Color="([^"]*)"""")
        val HEX_COLOR = Regex("#[0-9a-fA-F]{6,8}")
    }
}
