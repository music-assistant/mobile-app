package io.music_assistant.client.data.model.client

import io.music_assistant.client.data.model.client.items.Album
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.client.items.Artist
import io.music_assistant.client.data.model.client.items.Audiobook
import io.music_assistant.client.data.model.client.items.Playlist
import io.music_assistant.client.data.model.client.items.Podcast
import io.music_assistant.client.data.model.client.items.PodcastEpisode

enum class ItemDescriptionKind { BIOGRAPHY, REVIEW, DESCRIPTION, ABOUT_EPISODE }

/** The long text of an item details page. */
data class ItemDescription(val kind: ItemDescriptionKind, val text: ItemText)

/** Plain text with the web links that a provider marked in it. */
data class ItemText(val text: String, val links: List<ItemLink> = emptyList())

/** A link over `text[start until end]`. The [url] is http, https or mailto. */
data class ItemLink(val start: Int, val end: Int, val url: String)

/**
 * The text that the details page of [this] shows, like the web frontend: an artist's biography,
 * an album's review (or its description), the description of a playlist, a podcast or an
 * audiobook, and the "about" text of a podcast episode. Null for the other types and when the item has no readable text.
 */
fun AppMediaItem.itemDescription(): ItemDescription? = when (this) {
    is Artist -> metadata?.description?.toItemText()?.let { ItemDescription(ItemDescriptionKind.BIOGRAPHY, it) }
    is Album -> (metadata?.review?.toItemText() ?: metadata?.description?.toItemText())
        ?.let { ItemDescription(ItemDescriptionKind.REVIEW, it) }

    is Playlist, is Podcast, is Audiobook ->
        metadata?.description?.toItemText()?.let { ItemDescription(ItemDescriptionKind.DESCRIPTION, it) }

    is PodcastEpisode ->
        metadata?.description?.toItemText()?.let { ItemDescription(ItemDescriptionKind.ABOUT_EPISODE, it) }

    else -> null
}

// A link travels through the text cleanup as LINK_START url LINK_LABEL label LINK_END, so the
// cleanup cannot shift its range. The source loses these control characters first.
private const val LINK_START = '\u0001'
private const val LINK_LABEL = '\u0002'
private const val LINK_END = '\u0003'
private val MARKER_CHARS = Regex("[$LINK_START$LINK_LABEL$LINK_END]")

private val HTML_LINK = Regex(
    """<a\b[^>]*?\bhref\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+))[^>]*>([\s\S]*?)</a\s*>""",
    RegexOption.IGNORE_CASE,
)
private val MARKDOWN_AUTOLINK = Regex("""<((?:https?|mailto):[^\s<>]+)>""", RegexOption.IGNORE_CASE)
private val LINE_BREAK_TAGS = Regex("""<\s*(br\s*/?|/p|/li|/div|/h[1-6])\s*>""", RegexOption.IGNORE_CASE)
private val ANY_TAG = Regex("""<[^>]+>""")
private val MARKDOWN_LINK = Regex("""\[([^\]]*)\]\(([^)]*)\)""")
private val BARE_URL = Regex("""https?://[^\s<>"]+""", RegexOption.IGNORE_CASE)
private val LINK_SCHEME = Regex("""^(https?://|mailto:)\S+$""", RegexOption.IGNORE_CASE)
private const val URL_TRAILING_PUNCTUATION = ".,;:!?'\")]}"
private val NUMERIC_ENTITY = Regex("""&#(x[0-9a-fA-F]+|\d+);""")
private val NAMED_ENTITY = Regex("""&(amp|lt|gt|quot|apos|nbsp);""")
private val NAMED_ENTITIES = mapOf(
    "amp" to "&",
    "lt" to "<",
    "gt" to ">",
    "quot" to "\"",
    "apos" to "'",
    "nbsp" to " ",
)
private val TRAILING_SPACES = Regex("""[ \t]+\n""")
private val MANY_BLANK_LINES = Regex("""\n{3,}""")

/**
 * The text as plain text with its links. A provider sends Markdown, HTML or both, and some encode a
 * line break as a literal `\n` (the web frontend reads it the same way). Tags are dropped before the
 * entities decode, so an escaped tag stays visible text. An HTML anchor, a Markdown link and a bare
 * http(s) URL become an [ItemLink]. A link with another scheme or with no label stays plain text.
 * Null when nothing readable is left.
 */
fun String.toItemText(): ItemText? = this
    .replace(MARKER_CHARS, "")
    .replace("\\n", "\n")
    .replace("\r\n", "\n")
    .replace(HTML_LINK) { match ->
        // The groups between the whole match and the label are the quoted and bare forms of href.
        // Only one of them matches.
        val groups = match.groupValues
        linkMarker(groups.drop(1).dropLast(1).joinToString(""), groups.last())
    }
    .replace(MARKDOWN_AUTOLINK) { it.groupValues[1] }
    .replace(MARKDOWN_LINK) { linkMarker(it.groupValues[2].trim().substringBefore(' '), it.groupValues[1]) }
    .replace(LINE_BREAK_TAGS, "\n")
    .replace(ANY_TAG, "")
    .replace(NUMERIC_ENTITY) { decodeNumericEntity(it.groupValues[1]) ?: it.value }
    .replace(NAMED_ENTITY) { NAMED_ENTITIES.getValue(it.groupValues[1]) }
    .replace(TRAILING_SPACES, "\n")
    .replace(MANY_BLANK_LINES, "\n\n")
    .trim()
    .resolveLinks()
    .takeIf { it.text.isNotBlank() }

private fun linkMarker(url: String, label: String) = "$LINK_START$url$LINK_LABEL$label$LINK_END"

/**
 * Removes the link markers and records their ranges, then links the bare URLs outside them. The
 * label of a link inside a link label stays text, and its URL is dropped.
 */
private fun String.resolveLinks(): ItemText {
    val text = StringBuilder(length)
    val links = mutableListOf<ItemLink>()
    var index = 0
    var depth = 0
    var labelStart = 0
    var url = ""
    while (index < length) {
        when (val char = this[index]) {
            LINK_START -> {
                val urlEnd = indexOf(LINK_LABEL, index).takeIf { it >= 0 } ?: length
                if (depth == 0) {
                    url = substring(index + 1, urlEnd).trim()
                    labelStart = text.length
                }
                depth++
                index = urlEnd
            }

            LINK_END -> if (depth > 0) {
                depth--
                if (depth == 0 && LINK_SCHEME.matches(url) && text.substring(labelStart).isNotBlank()) {
                    links += ItemLink(labelStart, text.length, url)
                }
            }

            LINK_LABEL -> Unit
            else -> text.append(char)
        }
        index++
    }
    val plain = text.toString()
    val bare = BARE_URL.findAll(plain)
        .map { it.range.first to it.range.first + it.value.trimUrlEnd().length }
        .filter { (start, end) -> links.none { start < it.end && it.start < end } }
        .map { (start, end) -> ItemLink(start, end, plain.substring(start, end)) }
    return ItemText(plain, (links + bare).sortedBy { it.start })
}

/** Drops the sentence punctuation after a bare URL. A `)` stays while it closes a `(` of the URL. */
private tailrec fun String.trimUrlEnd(): String {
    val last = lastOrNull() ?: return this
    val keep = last !in URL_TRAILING_PUNCTUATION || (last == ')' && count { it == '(' } >= count { it == ')' })
    return if (keep) this else dropLast(1).trimUrlEnd()
}

private const val HEX_RADIX = 16
private const val MAX_CODE_POINT = 0x10FFFF
private const val SUPPLEMENTARY_START = 0x10000
private const val HIGH_SURROGATE_BASE = 0xD800
private const val LOW_SURROGATE_BASE = 0xDC00
private const val LOW_SURROGATE_BITS = 10
private const val LOW_SURROGATE_MASK = 0x3FF

private fun decodeNumericEntity(raw: String): String? {
    val codePoint = if (raw.startsWith("x", ignoreCase = true)) {
        raw.drop(1).toIntOrNull(HEX_RADIX)
    } else {
        raw.toIntOrNull()
    }
    return codePoint?.takeIf { it in 1..MAX_CODE_POINT }?.let(::codePointToString)
}

private fun codePointToString(codePoint: Int): String =
    if (codePoint < SUPPLEMENTARY_START) {
        codePoint.toChar().toString()
    } else {
        val offset = codePoint - SUPPLEMENTARY_START
        charArrayOf(
            ((offset shr LOW_SURROGATE_BITS) + HIGH_SURROGATE_BASE).toChar(),
            ((offset and LOW_SURROGATE_MASK) + LOW_SURROGATE_BASE).toChar(),
        ).concatToString()
    }
