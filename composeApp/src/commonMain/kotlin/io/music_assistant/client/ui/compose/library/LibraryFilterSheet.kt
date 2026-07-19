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
import io.music_assistant.client.data.model.client.GenreEmptyFilter
import io.music_assistant.client.data.model.client.LibraryFilters
import io.music_assistant.client.data.model.client.MediaType
import io.music_assistant.client.data.model.client.stringResource
import io.music_assistant.client.ui.compose.common.DataState
import io.music_assistant.client.ui.compose.common.SettingsSheet
import io.music_assistant.client.ui.compose.common.SettingsSheet.MultiChoiceChipsRow
import io.music_assistant.client.ui.compose.common.SettingsSheet.SingleChoiceChipsRow
import io.music_assistant.client.ui.compose.common.SettingsSheet.SwitchRow
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.filter_album_artists_only
import musicassistantclient.composeapp.generated.resources.filter_album_types
import musicassistantclient.composeapp.generated.resources.filter_favorites
import musicassistantclient.composeapp.generated.resources.filter_genres
import musicassistantclient.composeapp.generated.resources.filter_providers
import musicassistantclient.composeapp.generated.resources.filter_sheet_title
import musicassistantclient.composeapp.generated.resources.genre_filter_empty_all
import musicassistantclient.composeapp.generated.resources.genre_filter_empty_default
import musicassistantclient.composeapp.generated.resources.genre_filter_empty_non_empty
import musicassistantclient.composeapp.generated.resources.genre_filter_media_type
import musicassistantclient.composeapp.generated.resources.genre_filter_media_type_all
import musicassistantclient.composeapp.generated.resources.genre_filter_show
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Per-[MediaType] filter bottom sheet. Non-swipeable (gestures disabled); the
 * scrim tap and system back both discard via [onDismiss]. Edits accumulate in a
 * working copy and commit atomically through [onApply] only when "Apply" is hit.
 */
private enum class FilterPicker { NONE, PROVIDERS, GENRES }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryFilterSheet(
    mediaType: MediaType,
    filters: LibraryFilters,
    providerOptions: DataState<List<SelectOption<String>>>,
    genreOptions: DataState<List<SelectOption<Int>>>,
    onLoadOptions: () -> Unit,
    onApply: (LibraryFilters) -> Unit,
    onDismiss: () -> Unit,
) {
    var working by remember { mutableStateOf(filters) }

    SettingsSheet(
        title = stringResource(Res.string.filter_sheet_title),
        onApply = { onApply(working) },
        onDismiss = onDismiss,
    ) {
        // Seeded once at open; never keyed on the live `filters` (a settings
        // fold-back re-emission would otherwise wipe in-progress edits).

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
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = 24.dp,
                vertical = 8.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item {
                SwitchRow(Res.string.filter_favorites, working.favorite) {
                    working = working.copy(favorite = it)
                }
            }
            if (showProviders) {
                item {
                    FilterPickerRow(Res.string.filter_providers, working.providers.size) {
                        openPicker = FilterPicker.PROVIDERS
                    }
                }
            }
            if (showGenres) {
                item {
                    FilterPickerRow(Res.string.filter_genres, working.genres.size) {
                        openPicker = FilterPicker.GENRES
                    }
                }
            }
            typeSpecific(mediaType, workingProvider = { working }, onChange = { working = it })
        }

        when (openPicker) {
            FilterPicker.PROVIDERS -> MultiSelectDialog(
                title = Res.string.filter_providers,
                optionsState = providerOptions,
                selected = working.providers.toSet(),
                onConfirm = {
                    working = working.copy(providers = it.toList())
                    openPicker = FilterPicker.NONE
                },
                onDismiss = { openPicker = FilterPicker.NONE },
            )

            FilterPicker.GENRES -> MultiSelectDialog(
                title = Res.string.filter_genres,
                optionsState = genreOptions,
                selected = working.genres.toSet(),
                onConfirm = {
                    working = working.copy(genres = it.toList())
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

        else -> Unit
    }
}

private fun GenreEmptyFilter.label(): StringResource = when (this) {
    GenreEmptyFilter.DEFAULT -> Res.string.genre_filter_empty_default
    GenreEmptyFilter.NON_EMPTY -> Res.string.genre_filter_empty_non_empty
    GenreEmptyFilter.ALL -> Res.string.genre_filter_empty_all
}
