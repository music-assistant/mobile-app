package io.music_assistant.client.data.model.client

/**
 * Client-side metadata view of [io.music_assistant.client.data.model.server.ServerMetadata].
 *
 * Only carries fields the app actually reads; everything else the server sends
 * is dropped at the mapping boundary in `MediaItemFactory`.
 */
data class Metadata(
    val explicit: Boolean,
    val images: List<ImageInfo>,
    val releaseDate: String?,
    val chapters: List<Chapter>,
)
