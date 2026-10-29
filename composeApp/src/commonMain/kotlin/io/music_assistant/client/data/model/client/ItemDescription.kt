package io.music_assistant.client.data.model.client

import io.music_assistant.client.data.model.client.items.Album
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.client.items.Artist
import io.music_assistant.client.data.model.client.items.Audiobook
import io.music_assistant.client.data.model.client.items.Playlist
import io.music_assistant.client.data.model.client.items.Podcast
import io.music_assistant.client.data.model.client.items.PodcastEpisode

enum class ItemDescriptionKind { BIOGRAPHY, REVIEW, DESCRIPTION, ABOUT_EPISODE }

/** The long text of an item details page, as plain text. */
data class ItemDescription(val kind: ItemDescriptionKind, val text: String)

/**
 * The text that the details page of [this] shows, like the web frontend: an artist's biography,
 * an album's review (or its description), the description of a playlist, a podcast or an
 * audiobook, and the "about" text of a podcast episode. Null for the other types and when the item has no readable text.
 */
fun AppMediaItem.itemDescription(): ItemDescription? = when (this) {
    is Artist -> metadata?.description?.toPlainItemText()?.let { ItemDescription(ItemDescriptionKind.BIOGRAPHY, it) }
    is Album -> (metadata?.review?.toPlainItemText() ?: metadata?.description?.toPlainItemText())
        ?.let { ItemDescription(ItemDescriptionKind.REVIEW, it) }

    is Playlist, is Podcast, is Audiobook ->
        metadata?.description?.toPlainItemText()?.let { ItemDescription(ItemDescriptionKind.DESCRIPTION, it) }

    is PodcastEpisode ->
        metadata?.description?.toPlainItemText()?.let { ItemDescription(ItemDescriptionKind.ABOUT_EPISODE, it) }

    else -> null
}

private val LINE_BREAK_TAGS = Regex("""<\s*(br\s*/?|/p|/li|/div|/h[1-6])\s*>""", RegexOption.IGNORE_CASE)
private val ANY_TAG = Regex("""<[^>]+>""")
private val MARKDOWN_LINK = Regex("""\[([^\]]*)\]\([^)]*\)""")
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
 * The text as plain text. A provider sends Markdown, HTML or both, and some encode a line break as
 * a literal `\n` (the web frontend reads it the same way). Tags are dropped before the entities
 * decode, so an escaped tag stays visible text. Null when nothing readable is left.
 */
fun String.toPlainItemText(): String? = this
    .replace("\\n", "\n")
    .replace("\r\n", "\n")
    .replace(LINE_BREAK_TAGS, "\n")
    .replace(ANY_TAG, "")
    .replace(MARKDOWN_LINK) { it.groupValues[1] }
    .replace(NUMERIC_ENTITY) { decodeNumericEntity(it.groupValues[1]) ?: it.value }
    .replace(NAMED_ENTITY) { NAMED_ENTITIES.getValue(it.groupValues[1]) }
    .replace(TRAILING_SPACES, "\n")
    .replace(MANY_BLANK_LINES, "\n\n")
    .trim()
    .takeIf { it.isNotEmpty() }

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
