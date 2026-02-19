package io.music_assistant.client.data.model.server

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ServerMediaItem(
    @SerialName("item_id") val itemId: String,
    @SerialName("provider") val provider: String,
    @SerialName("name") val name: String,
    @SerialName("provider_mappings") val providerMappings: List<ProviderMapping>? = null,
    @SerialName("metadata") val metadata: Metadata? = null,
    @SerialName("favorite") val favorite: Boolean? = null,
    @SerialName("media_type") val mediaType: MediaType,
    //@SerialName("sort_name") val sortName: String? = null,
    @SerialName("uri") val uri: String? = null,
    @SerialName("image") val image: MediaItemImage? = null,
    //@SerialName("is_playable") val isPlayable: Boolean? = null,
    //@SerialName("timestamp_added") val timestampAdded: Long? = null,
    //@SerialName("timestamp_modified") val timestampModified: Long? = null,
    // various subtypes
    //@SerialName("musicbrainz_id") val musicbrainzId: String? = null,
    // Album
    //@SerialName("version") val version: String? = null,
    //@SerialName("external_ids") val externalIds: List<List<String>>? = null,
    //@SerialName("position") val position: Int? = null,
    //@SerialName("year") val year: Int? = null,
    @SerialName("artists") val artists: List<ServerMediaItem>? = null,
    //@SerialName("album_type") val albumType: AlbumType? = null,
    // Playlist only
    //@SerialName("owner") val owner: String? = null,
    @SerialName("is_editable") val isEditable: Boolean? = null,
    // Track only
    @SerialName("duration") val duration: Double? = null,
    //@SerialName("isrc") val isrc: String? = null,
    // album track only
    @SerialName("album") val album: ServerMediaItem? = null,
    //@SerialName("disc_number") val discNumber: Int? = null,
    //@SerialName("track_number") val trackNumber: Int? = null,
    // podcast episode only
    @SerialName("podcast") val podcast: ServerMediaItem? = null,
    // Audiobook only
    @SerialName("authors") val authors: List<String>? = null,
    @SerialName("narrators") val narrators: List<String>? = null,
    @SerialName("publisher") val publisher: String? = null,
    // Progress tracking (audiobooks, podcast episodes)
    @SerialName("fully_played") val fullyPlayed: Boolean? = null,
    @SerialName("resume_position_ms") val resumePositionMs: Long? = null,
    // Folder only
    @SerialName("items") val items: List<ServerMediaItem>? = null,
)

@Serializable
data class Metadata(
    @SerialName("description") val description: String? = null,
    @SerialName("review") val review: String? = null,
    @SerialName("explicit") val explicit: Boolean? = null,
    @SerialName("images") val images: List<MediaItemImage>? = null,
    @SerialName("genres") val genres: List<String>? = null,
    @SerialName("mood") val mood: String? = null,
    @SerialName("style") val style: String? = null,
    @SerialName("copyright") val copyright: String? = null,
    @SerialName("lyrics") val lyrics: String? = null,
    @SerialName("label") val label: String? = null,
    //@SerialName("links") val links: List<String>? = null,
    //@SerialName("performers") val performers: List<String>? = null,
    @SerialName("preview") val preview: String? = null,
    @SerialName("popularity") val popularity: Int? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    //@SerialName("languages") val languages: List<String>? = null,
    @SerialName("chapters") val chapters: List<MediaItemChapter>? = null,
    @SerialName("last_refresh") val lastRefresh: Long?
)

@Serializable
data class MediaItemImage(
    //@SerialName("type") val type: String,
    @SerialName("path") val path: String,
    @SerialName("provider") val provider: String,
    @SerialName("remotely_accessible") val remotelyAccessible: Boolean
)

@Serializable
data class ProviderMapping(
    @SerialName("item_id") val itemId: String,
//    @SerialName("provider_domain") val providerDomain: String,
    @SerialName("provider_instance") val providerInstance: String,
//    @SerialName("available") val available: Boolean,
//    @SerialName("audio_format") val audioFormat: AudioFormat? = null,
//    @SerialName("url") val url: String? = null,
//    @SerialName("details") val details: String?
)

@Serializable
data class MediaItemChapter(
    @SerialName("position") val position: Int,
    @SerialName("name") val name: String,
    @SerialName("start") val start: Double,
    @SerialName("end") val end: Double? = null,
) {
    val duration: Double get() = if (end != null) end - start else 0.0
}

@Serializable
data class AudioFormat(
    @SerialName("content_type") val contentType: String? = null,
    @SerialName("codec_type") val codecType: String? = null,
    @SerialName("sample_rate") val sampleRate: Int? = null,
    @SerialName("bit_depth") val bitDepth: Int? = null,
    @SerialName("channels") val channels: Int? = null,
    @SerialName("output_format_str") val outputFormatStr: String? = null,
    @SerialName("bit_rate") val bitRate: Int? = null
)
