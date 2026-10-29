// Compose layout values (sizes, line counts) are visual design tokens.
@file:Suppress("MagicNumber")

package io.music_assistant.client.ui.compose.item

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.music_assistant.client.data.model.client.ItemDescription
import io.music_assistant.client.data.model.client.ItemDescriptionKind
import io.music_assistant.client.data.model.client.ProviderDetails
import io.music_assistant.client.data.model.client.items.Album
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.client.items.Audiobook
import io.music_assistant.client.data.model.client.items.Genre
import io.music_assistant.client.data.model.client.items.PlaylistAccessPart
import io.music_assistant.client.data.model.client.items.PlaylistOwnerLine
import io.music_assistant.client.data.model.client.items.PodcastEpisode
import io.music_assistant.client.data.model.client.stringResource
import io.music_assistant.client.utils.TotalDuration
import io.music_assistant.client.utils.toTotalDuration
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.item_description_more
import musicassistantclient.composeapp.generated.resources.item_duration_hours
import musicassistantclient.composeapp.generated.resources.item_duration_hours_minutes
import musicassistantclient.composeapp.generated.resources.item_duration_minutes
import musicassistantclient.composeapp.generated.resources.item_publisher
import musicassistantclient.composeapp.generated.resources.item_section_about_episode
import musicassistantclient.composeapp.generated.resources.item_section_biography
import musicassistantclient.composeapp.generated.resources.item_section_description
import musicassistantclient.composeapp.generated.resources.item_section_review
import musicassistantclient.composeapp.generated.resources.item_track_count
import musicassistantclient.composeapp.generated.resources.playlist_access_collaborative
import musicassistantclient.composeapp.generated.resources.playlist_access_personal
import musicassistantclient.composeapp.generated.resources.playlist_access_shared
import musicassistantclient.composeapp.generated.resources.playlist_sharing_everyone
import musicassistantclient.composeapp.generated.resources.playlist_sharing_members
import musicassistantclient.composeapp.generated.resources.playlist_sharing_not_shared
import musicassistantclient.composeapp.generated.resources.playlist_sharing_private
import musicassistantclient.composeapp.generated.resources.playlist_sharing_selected
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** Between the parts of a facts line, like the web frontend. */
private const val PART_SEPARATOR = " · "
private const val MAX_GENRE_CHIPS = 15
private const val CLAMPED_LINES = 3

/**
 * The facts under the subtitle: the album type, the track count and the total playing time, the
 * playing time of a podcast episode, the publisher of an audiobook, the music services of a
 * library item, and the owner of a playlist.
 * Nothing renders when the item has none.
 */
@Composable
fun ItemFacts(
    item: AppMediaItem,
    playableSummary: PlayableSummary?,
    ownerLine: PlaylistOwnerLine?,
    providerDetails: (String) -> ProviderDetails?,
    textAlign: TextAlign,
    modifier: Modifier = Modifier,
) {
    val facts = listOfNotNull(
        (item as? Album)?.albumType?.let { stringResource(it.stringResource()) },
        (item as? Album)?.year?.toString(),
        playableSummary?.let { pluralStringResource(Res.plurals.item_track_count, it.count, it.count) },
        playableSummary?.totalSeconds?.toTotalDuration()?.localized(),
        (item as? PodcastEpisode)?.duration?.takeIf { it > 0 }?.toTotalDuration()?.localized(),
        (item as? Audiobook)?.publisher?.takeIf { it.isNotBlank() }
            ?.let { stringResource(Res.string.item_publisher, it) },
        item.sourceNames(providerDetails),
    )
    val owner = ownerLine?.localized()
    if (facts.isEmpty() && owner == null) return

    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        facts.takeIf { it.isNotEmpty() }?.let { FactText(it.joinToString(PART_SEPARATOR), textAlign) }
        owner?.let { FactText(it, textAlign) }
    }
}

@Composable
private fun FactText(text: String, textAlign: TextAlign) {
    Text(
        modifier = Modifier.fillMaxWidth(),
        text = text,
        textAlign = textAlign,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** The names of the music services that hold a library item, in the order of its mappings. */
private fun AppMediaItem.sourceNames(providerDetails: (String) -> ProviderDetails?): String? =
    takeIf { isInLibrary }?.providerMappings.orEmpty()
        .filter { it.available }
        .mapNotNull { (providerDetails(it.providerInstance) ?: providerDetails(it.providerDomain))?.name }
        .distinct()
        .takeIf { it.isNotEmpty() }
        ?.joinToString(", ")

@Composable
private fun TotalDuration.localized(): String = when {
    hours == 0L -> stringResource(Res.string.item_duration_minutes, minutes)
    minutes == 0L -> stringResource(Res.string.item_duration_hours, hours)
    else -> stringResource(Res.string.item_duration_hours_minutes, hours, minutes)
}

@Composable
private fun PlaylistOwnerLine.localized(): String = when (this) {
    is PlaylistOwnerLine.Owner -> name
    is PlaylistOwnerLine.Access -> parts.map { it.localized() }.joinToString(PART_SEPARATOR)
}

@Composable
private fun PlaylistAccessPart.localized(): String = when (this) {
    PlaylistAccessPart.Personal -> stringResource(Res.string.playlist_access_personal)
    PlaylistAccessPart.Shared -> stringResource(Res.string.playlist_access_shared)
    is PlaylistAccessPart.Owner -> name
    PlaylistAccessPart.OnlyMe -> stringResource(Res.string.playlist_sharing_private)
    PlaylistAccessPart.NotShared -> stringResource(Res.string.playlist_sharing_not_shared)
    is PlaylistAccessPart.SharedWith -> pluralStringResource(Res.plurals.playlist_sharing_selected, count, count)
    PlaylistAccessPart.Members -> stringResource(Res.string.playlist_sharing_members)
    PlaylistAccessPart.Everyone -> stringResource(Res.string.playlist_sharing_everyone)
    PlaylistAccessPart.Collaborative -> stringResource(Res.string.playlist_access_collaborative)
}

/** The genre chips of a library artist or album; a tap opens the genre page. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GenreChips(
    genres: List<Genre>,
    onGenreClick: (Genre) -> Unit,
    textAlign: TextAlign,
    modifier: Modifier = Modifier,
) {
    if (genres.isEmpty()) return
    val alignment = if (textAlign == TextAlign.Center) Alignment.CenterHorizontally else Alignment.Start
    FlowRow(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, alignment),
    ) {
        genres.take(MAX_GENRE_CHIPS).forEach { genre ->
            SuggestionChip(
                onClick = { onGenreClick(genre) },
                label = { Text(genre.name) },
            )
        }
    }
}

private fun ItemDescriptionKind.stringResource(): StringResource = when (this) {
    ItemDescriptionKind.BIOGRAPHY -> Res.string.item_section_biography
    ItemDescriptionKind.REVIEW -> Res.string.item_section_review
    ItemDescriptionKind.DESCRIPTION -> Res.string.item_section_description
    ItemDescriptionKind.ABOUT_EPISODE -> Res.string.item_section_about_episode
}

/**
 * The long text, clamped to a few lines. A text that does not fit shows a "More" label, and a tap
 * calls [onOpen] for the full text ([ItemDescriptionSheet]).
 */
@Composable
fun ItemDescriptionBlock(
    description: ItemDescription,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var clamped by remember(description) { mutableStateOf(false) }
    val more = stringResource(Res.string.item_description_more)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = clamped, onClickLabel = more, role = Role.Button, onClick = onOpen)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = stringResource(description.kind.stringResource()),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = description.text,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = CLAMPED_LINES,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { clamped = it.hasVisualOverflow },
        )
        if (clamped) {
            Text(
                text = more,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** The full text of [description], in a sheet the user can scroll and select from. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemDescriptionSheet(
    description: ItemDescription,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(description.kind.stringResource()),
                style = MaterialTheme.typography.titleLarge,
            )
            SelectionContainer {
                Text(text = description.text, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
