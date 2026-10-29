package io.music_assistant.client.ui.compose.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.music_assistant.client.data.model.client.AlbumType
import io.music_assistant.client.data.model.client.ArtistType
import io.music_assistant.client.data.model.client.GenreContentType
import io.music_assistant.client.data.model.client.GenreEmptyFilter
import io.music_assistant.client.data.model.client.LibraryFilters
import io.music_assistant.client.data.model.client.MediaType
import io.music_assistant.client.data.model.client.genreMediaTypeFilter
import io.music_assistant.client.data.model.client.hasActive
import io.music_assistant.client.data.model.client.stringResource
import io.music_assistant.client.ui.compose.common.DataState
import io.music_assistant.client.ui.compose.common.MultiSelectDialog
import io.music_assistant.client.ui.compose.common.SelectOption
import io.music_assistant.client.ui.compose.common.SettingsSheet.MultiChoiceChipsRow
import io.music_assistant.client.ui.compose.common.SettingsSheet.PickerRow
import io.music_assistant.client.ui.compose.common.SettingsSheet.SingleChoiceChipsRow
import io.music_assistant.client.ui.compose.common.SettingsSheet.SwitchRow
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.artist_type_authors
import musicassistantclient.composeapp.generated.resources.artist_type_narrators
import musicassistantclient.composeapp.generated.resources.filter_album_artists_only
import musicassistantclient.composeapp.generated.resources.filter_album_types
import musicassistantclient.composeapp.generated.resources.filter_audiobooks_show
import musicassistantclient.composeapp.generated.resources.filter_favorites
import musicassistantclient.composeapp.generated.resources.filter_genres
import musicassistantclient.composeapp.generated.resources.filter_sources
import musicassistantclient.composeapp.generated.resources.genre_content_type_music
import musicassistantclient.composeapp.generated.resources.genre_filter_content_type
import musicassistantclient.composeapp.generated.resources.genre_filter_content_type_all
import musicassistantclient.composeapp.generated.resources.genre_filter_empty_all
import musicassistantclient.composeapp.generated.resources.genre_filter_empty_default
import musicassistantclient.composeapp.generated.resources.genre_filter_empty_non_empty
import musicassistantclient.composeapp.generated.resources.genre_filter_media_type
import musicassistantclient.composeapp.generated.resources.genre_filter_media_type_all
import musicassistantclient.composeapp.generated.resources.genre_filter_show
import musicassistantclient.composeapp.generated.resources.media_type_artists
import musicassistantclient.composeapp.generated.resources.media_type_audiobooks
import musicassistantclient.composeapp.generated.resources.media_type_podcasts
import org.jetbrains.compose.resources.StringResource

/**
 * Per-[MediaType] filter bottom sheet. Non-swipeable (gestures disabled); the
 * scrim tap and system back both discard via [onDismiss]. Edits accumulate in a
 * working copy and commit atomically through [onApply] only when "Apply" is hit.
 */
private enum class FilterPicker { NONE, PROVIDERS, GENRES }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryFilterAction(
    mediaType: MediaType,
    filters: LibraryFilters,
    providerOptions: DataState<List<SelectOption<String>>>,
    genreOptions: DataState<List<SelectOption<Int>>>,
    audiobookArtistTypes: List<ArtistType>,
    onLoadOptions: () -> Unit,
    onApply: (LibraryFilters) -> Unit,
) {
    FilterAction(
        active = filters.hasActive,
        state = { mutableStateOf(filters) },
        onApply = { onApply(it.value) },
        canReset = { it.value.hasActive },
        onReset = { it.value = LibraryFilters.DEFAULT },
    ) { state ->
        var workingFilters by state

        var openPicker by remember { mutableStateOf(FilterPicker.NONE) }
        LaunchedEffect(Unit) { onLoadOptions() }

        // Providers only when >1 serves this type (hides single-provider cases like
        // podcasts/audiobooks); genres only when there are any for this type. Neither
        // on the genres list itself.
        val showProviders = mediaType != MediaType.GENRE &&
                (providerOptions as? DataState.Data)?.data.orEmpty().size > 1
        val showGenres = mediaType != MediaType.GENRE &&
                (genreOptions as? DataState.Data)?.data.orEmpty().isNotEmpty()

        LazyColumn(
            // fill = false keeps the reset button right below the rows instead of at the sheet bottom.
            modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                vertical = 8.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item {
                SwitchRow(Res.string.filter_favorites, workingFilters.favorite) {
                    workingFilters = workingFilters.copy(favorite = it)
                }
            }
            if (showProviders) {
                item {
                    PickerRow(Res.string.filter_sources, workingFilters.providers.size) {
                        openPicker = FilterPicker.PROVIDERS
                    }
                }
            }
            if (showGenres) {
                item {
                    PickerRow(Res.string.filter_genres, workingFilters.genres.size) {
                        openPicker = FilterPicker.GENRES
                    }
                }
            }
            typeSpecific(
                mediaType = mediaType,
                audiobookArtistTypes = audiobookArtistTypes,
                workingProvider = { workingFilters },
                onChange = { workingFilters = it },
            )
        }

        when (openPicker) {
            FilterPicker.PROVIDERS -> MultiSelectDialog(
                title = Res.string.filter_sources,
                optionsState = providerOptions,
                selected = workingFilters.providers.toSet(),
                onConfirm = {
                    workingFilters = workingFilters.copy(providers = it.toList())
                    openPicker = FilterPicker.NONE
                },
                onDismiss = { openPicker = FilterPicker.NONE },
            )

            FilterPicker.GENRES -> MultiSelectDialog(
                title = Res.string.filter_genres,
                optionsState = genreOptions,
                selected = workingFilters.genres.toSet(),
                onConfirm = {
                    workingFilters = workingFilters.copy(genres = it.toList())
                    openPicker = FilterPicker.NONE
                },
                onDismiss = { openPicker = FilterPicker.NONE },
            )

            FilterPicker.NONE -> Unit
        }
    }
}

/** Emits the filter rows unique to [mediaType]. */
private fun androidx.compose.foundation.lazy.LazyListScope.typeSpecific(
    mediaType: MediaType,
    audiobookArtistTypes: List<ArtistType>,
    workingProvider: () -> LibraryFilters,
    onChange: (LibraryFilters) -> Unit,
) {
    when (mediaType) {
        MediaType.ARTIST -> item {
            val w = workingProvider()
            SwitchRow(Res.string.filter_album_artists_only, w.albumArtistsOnly) {
                onChange(w.copy(albumArtistsOnly = it))
            }
        }

        MediaType.ALBUM -> item {
            val w = workingProvider()
            MultiChoiceChipsRow(
                label = Res.string.filter_album_types,
                options = AlbumType.entries,
                selected = w.albumTypes,
                optionLabel = { it.stringResource() },
                onToggle = { type ->
                    val next =
                        if (type in w.albumTypes) w.albumTypes - type else w.albumTypes + type
                    onChange(w.copy(albumTypes = next))
                },
            )
        }

        // The row stays while a type is chosen that the library no longer holds, so the
        // choice can always be undone from the sheet.
        MediaType.AUDIOBOOK -> {
            val chosen = workingProvider().audiobookArtistType
            if (audiobookArtistTypes.isNotEmpty() || chosen != null) {
                item {
                    val w = workingProvider()
                    SingleChoiceChipsRow(
                        label = Res.string.filter_audiobooks_show,
                        options = listOf<ArtistType?>(null) + (audiobookArtistTypes + chosen).distinct().filterNotNull(),
                        selected = w.audiobookArtistType,
                        optionLabel = { it?.label() ?: Res.string.media_type_audiobooks },
                        onSelect = { onChange(w.copy(audiobookArtistType = it)) },
                    )
                }
            }
        }

        MediaType.GENRE -> {
            item {
                val w = workingProvider()
                SingleChoiceChipsRow(
                    label = Res.string.genre_filter_show,
                    options = GenreEmptyFilter.entries,
                    selected = w.hideEmpty,
                    optionLabel = { it.label() },
                    onSelect = { onChange(w.copy(hideEmpty = it)) },
                )
            }
            item {
                val w = workingProvider()
                SingleChoiceChipsRow(
                    label = Res.string.genre_filter_content_type,
                    options = listOf<GenreContentType?>(null) + GenreContentType.entries,
                    selected = w.genreContentType,
                    optionLabel = { it?.label() ?: Res.string.genre_filter_content_type_all },
                    // The media type refines the music taxonomy only, so it goes with it.
                    onSelect = { type ->
                        onChange(w.copy(genreContentType = type, genreMediaType = w.genreMediaTypeFilter))
                    },
                )
            }
            if (workingProvider().genreContentType == GenreContentType.MUSIC) {
                item {
                    val w = workingProvider()
                    SingleChoiceChipsRow(
                        label = Res.string.genre_filter_media_type,
                        options = listOf<MediaType?>(null) + MediaType.genreMediaTypeOptions,
                        selected = w.genreMediaType,
                        optionLabel = {
                            it?.stringResource() ?: Res.string.genre_filter_media_type_all
                        },
                        onSelect = { onChange(w.copy(genreMediaType = it)) },
                    )
                }
            }
        }

        else -> Unit
    }
}

private fun ArtistType.label(): StringResource = when (this) {
    ArtistType.SINGER -> Res.string.media_type_artists
    ArtistType.AUTHOR -> Res.string.artist_type_authors
    ArtistType.NARRATOR -> Res.string.artist_type_narrators
}

private fun GenreContentType.label(): StringResource = when (this) {
    GenreContentType.MUSIC -> Res.string.genre_content_type_music
    GenreContentType.PODCAST -> Res.string.media_type_podcasts
    GenreContentType.AUDIOBOOK -> Res.string.media_type_audiobooks
}

private fun GenreEmptyFilter.label(): StringResource = when (this) {
    GenreEmptyFilter.DEFAULT -> Res.string.genre_filter_empty_default
    GenreEmptyFilter.NON_EMPTY -> Res.string.genre_filter_empty_non_empty
    GenreEmptyFilter.ALL -> Res.string.genre_filter_empty_all
}
