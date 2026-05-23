package io.music_assistant.client.ui.compose.common.items

import io.music_assistant.client.data.model.client.MediaType
import io.music_assistant.client.data.model.client.items.AppMediaItem

/**
 * Stable, unique key for an [AppMediaItem] inside a Compose `LazyColumn`,
 * `LazyRow`, or `LazyVerticalGrid`'s `key = { ... }` lambda.
 *
 * Combines `mediaType`, `provider`, and `itemId` so the same canonical
 * `itemId` carried by two different providers (e.g. a track with the same
 * server-side `itemId` from Spotify and Apple Music) produces distinct
 * keys. Without this, Compose's `SubcomposeLayout.subcompose` precondition
 * (`SubcomposeLayout.kt:614` — "Key X was already used") fires during
 * fling/prefetch on iOS and the app crashes.
 *
 * Returns a structured [Triple] rather than a delimited string so the
 * encoding cannot accidentally collide on field contents — e.g. without
 * this, `(provider="apple_music", itemId="xyz")` would generate the same
 * underscore-joined string as `(provider="apple", itemId="music_xyz")`.
 * Compose's slot identity uses `equals`/`hashCode`, both of which Triple
 * implements structurally.
 *
 * Contract pinned by `LazyListKeysTest`.
 */
fun AppMediaItem.lazyListKey(): Triple<MediaType, String, String> =
    Triple(mediaType, provider, itemId)
