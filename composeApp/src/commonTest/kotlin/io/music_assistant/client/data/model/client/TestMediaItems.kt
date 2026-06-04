package io.music_assistant.client.data.model.client

import io.music_assistant.client.data.model.client.items.Album
import io.music_assistant.client.data.model.client.items.Artist
import io.music_assistant.client.data.model.client.items.Audiobook
import io.music_assistant.client.data.model.client.items.Genre
import io.music_assistant.client.data.model.client.items.Playlist
import io.music_assistant.client.data.model.client.items.Podcast
import io.music_assistant.client.data.model.client.items.PodcastEpisode
import io.music_assistant.client.data.model.client.items.RadioStation
import io.music_assistant.client.data.model.client.items.Track

/** Minimal media-item builders for unit tests — only the fields tests care about are set. */
internal fun testTrack(isPlayable: Boolean = true) = Track(
    itemId = "id", provider = "test", name = "name", providerMappings = null, metadata = null,
    favorite = null, uri = null, images = emptyMap(), duration = null, isPlayable = isPlayable,
    artists = emptyList(), album = null, discNumber = null, trackNumber = null, position = null,
    version = null,
)

internal fun testAlbum() = Album(
    itemId = "id", provider = "test", name = "name", providerMappings = null, metadata = null,
    favorite = null, uri = null, images = emptyMap(), version = null, year = null, artists = emptyList(),
)

internal fun testArtist() = Artist(
    itemId = "id",
    provider = "test",
    name = "name",
    providerMappings = null,
    metadata = null,
    favorite = null,
    uri = null,
    images = emptyMap(),
)

internal fun testPlaylist(isDynamic: Boolean = false) = Playlist(
    itemId = "id", provider = "test", name = "name", providerMappings = null, metadata = null,
    favorite = null, uri = null, images = emptyMap(), isEditable = true, isDynamic = isDynamic,
)

internal fun testRadio(isPlayable: Boolean = true) = RadioStation(
    itemId = "id", provider = "test", name = "name", providerMappings = null, metadata = null,
    favorite = null, uri = null, images = emptyMap(), version = null, isPlayable = isPlayable,
)

internal fun testPodcastEpisode(isPlayable: Boolean = true) = PodcastEpisode(
    itemId = "id", provider = "test", name = "name", providerMappings = null, metadata = null,
    favorite = null, uri = null, images = emptyMap(), duration = null, isPlayable = isPlayable,
    podcast = null, fullyPlayed = null, resumePositionMs = null, version = null,
)

internal fun testPodcast() = Podcast(
    itemId = "id",
    provider = "test",
    name = "name",
    providerMappings = null,
    metadata = null,
    favorite = null,
    uri = null,
    images = emptyMap(),
)

internal fun testAudiobook(isPlayable: Boolean = true) = Audiobook(
    itemId = "id", provider = "test", name = "name", providerMappings = null, metadata = null,
    favorite = null, uri = null, images = emptyMap(), duration = null, isPlayable = isPlayable,
    authors = null, narrators = null, chapters = null, fullyPlayed = null, resumePositionMs = null,
    version = null,
)

internal fun testGenre() = Genre(
    itemId = "id",
    provider = "test",
    name = "name",
    providerMappings = null,
    metadata = null,
    favorite = null,
    uri = null,
    images = emptyMap(),
)
