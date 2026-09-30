package io.music_assistant.client.api

import co.touchlab.kermit.Logger
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Server default page size for `library_items` and `player_queues/items`. Those commands answer
 * in ONE message (only async-generator commands such as `playlist_tracks` are streamed in
 * `partial` batches), so a single oversized `limit` is what trips the server's per-message cap.
 */
const val SERVER_PAGE_SIZE = 500

/** Defensive ceiling on sequential pages, so a server that ignores `offset` can't loop forever. */
private const val MAX_PAGES = 100

private val logger = Logger.withTag("Paging")

/** Copy of this request with `limit` and `offset` overwritten; every other arg is kept as is. */
fun Request.paged(limit: Int, offset: Int): Request = Request(
    command = command,
    args = JsonObject(
        args.orEmpty() + mapOf(
            "limit" to JsonPrimitive(limit),
            "offset" to JsonPrimitive(offset),
        ),
    ),
)

/**
 * Fetches every page of a `limit`/`offset` listing through [fetchPage], stopping at the first
 * page shorter than [pageSize]. Returns null when the first page fails; a later failure returns
 * what was collected so far, so callers still get a usable (if truncated) list.
 */
suspend fun <T> Request.fetchAllPages(
    pageSize: Int = SERVER_PAGE_SIZE,
    fetchPage: suspend (Request) -> List<T>?,
): List<T>? {
    val items = mutableListOf<T>()
    repeat(MAX_PAGES) { page ->
        val chunk = fetchPage(paged(limit = pageSize, offset = page * pageSize))
            ?: return items.takeIf { page > 0 }.also {
                logger.w { "$command: page $page failed, returning ${items.size} items" }
            }
        items += chunk
        if (chunk.size < pageSize) return items
    }
    logger.w { "$command: hit MAX_PAGES ($MAX_PAGES); list truncated at ${items.size} items" }
    return items
}
