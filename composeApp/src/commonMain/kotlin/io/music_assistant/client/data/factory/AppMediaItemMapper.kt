package io.music_assistant.client.data.factory

import io.music_assistant.client.data.model.client.items.MarkableItem
import io.music_assistant.client.data.model.server.ServerMediaItem
import io.music_assistant.client.utils.mediaItemEchoJson
import kotlinx.serialization.json.JsonElement

/**
 * Builds the `media_item` payload for `music/mark_played` / `music/mark_unplayed` by
 * echoing the original server DTO back verbatim (nulls omitted). The server expects a
 * full media item it can deserialize into a typed dataclass; reconstructing one from
 * the decomposed [io.music_assistant.client.data.model.client.items.AppMediaItem]
 * loses required fields (e.g. a podcast episode's `position`/`podcast`), so we return
 * exactly what we received.
 */
fun MarkableItem.toMarkMediaItem(): JsonElement =
    mediaItemEchoJson.encodeToJsonElement(ServerMediaItem.serializer(), source)
