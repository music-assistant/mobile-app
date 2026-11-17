package io.music_assistant.client.ui.compose.common.providers

/**
 * Copies the class rules of the `<style>` blocks of an SVG into the `style` attribute of each
 * element that has the class.
 *
 * The iOS SVG renderer (Skia SVGDOM) ignores `<style>` blocks, so an icon that colors its shapes
 * through classes renders black. The class declarations go before the element's own inline
 * style, so the inline style still wins, as in CSS.
 *
 * Only simple class selectors (`.a`, `.a, .b`) are applied. At-rules (`@media`), element,
 * id and compound selectors are skipped.
 */
internal fun inlineSvgClassStyles(svg: String): String {
    val css = STYLE_BLOCK.findAll(svg).joinToString("\n") { it.groupValues[1] }
        .replace("<![CDATA[", "")
        .replace("]]>", "")
        .replace(CSS_COMMENT, "")
    val rules = parseClassRules(css).takeIf { it.isNotEmpty() } ?: return svg
    return START_TAG.replace(svg) { tag -> inlineIntoTag(tag.value, rules) }
}

private class ClassRule(val classes: Set<String>, val declarations: String)

private fun parseClassRules(css: String): List<ClassRule> {
    val rules = mutableListOf<ClassRule>()
    var index = 0
    while (index < css.length) {
        val open = css.indexOf('{', index).takeIf { it >= 0 } ?: break
        val selector = css.substring(index, open).trim()
        val close = matchingBrace(css, open) ?: break
        if (!selector.startsWith("@")) {
            val classes = selector.split(',').map { it.trim() }.filter { CLASS_SELECTOR.matches(it) }
                .map { it.substring(1) }.toSet()
            val declarations = css.substring(open + 1, close).trim().trim(';').replace('"', '\'')
            if (classes.isNotEmpty() && declarations.isNotBlank()) rules += ClassRule(classes, declarations)
        }
        index = close + 1
    }
    return rules
}

private fun matchingBrace(css: String, open: Int): Int? {
    var depth = 0
    for (i in open until css.length) {
        when (css[i]) {
            '{' -> depth++
            '}' -> if (--depth == 0) return i
        }
    }
    return null
}

private fun inlineIntoTag(tag: String, rules: List<ClassRule>): String {
    val classes = CLASS_ATTR.find(tag)?.groupValues?.get(2)?.split(WHITESPACE)?.toSet() ?: return tag
    val declarations = rules.filter { rule -> rule.classes.any { it in classes } }
        .joinToString(";") { it.declarations }
        .takeIf { it.isNotEmpty() } ?: return tag
    return STYLE_ATTR.find(tag)?.let { existing ->
        val merged = "$declarations;${existing.groupValues[2]}"
        tag.replaceRange(existing.range, "${existing.groupValues[1]}style=\"$merged\"")
    } ?: tag.replaceFirst(TAG_NAME, "$0 style=\"$declarations\"")
}

// `(?s)` lets `.` match a line break. The common stdlib has no RegexOption.DOT_MATCHES_ALL.
private val STYLE_BLOCK = Regex("(?s)<style\\b[^>]*>(.*?)</style>")
private val CSS_COMMENT = Regex("(?s)/\\*.*?\\*/")
private val CLASS_SELECTOR = Regex("\\.[A-Za-z_][\\w-]*")
private val START_TAG = Regex("<[A-Za-z][^>]*>")
private val TAG_NAME = Regex("^<[A-Za-z][\\w:.-]*")
private val CLASS_ATTR = Regex("(\\s)class\\s*=\\s*\"([^\"]*)\"")
private val STYLE_ATTR = Regex("(\\s)style\\s*=\\s*\"([^\"]*)\"")
private val WHITESPACE = Regex("\\s+")
