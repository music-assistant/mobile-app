package io.music_assistant.client.ui.compose.common.items

import androidx.compose.runtime.Composable
import io.music_assistant.client.data.model.client.MediaType
import io.music_assistant.client.data.model.client.SortConfig
import io.music_assistant.client.data.model.client.SortField
import io.music_assistant.client.data.model.client.SortOption
import io.music_assistant.client.data.model.client.SubItemContext
import io.music_assistant.client.ui.compose.common.SortChip

@Composable
fun ItemSortChip(sortOption: SortOption, mediaType: MediaType, onSortChanged: (SortOption) -> Unit) {
    val availableFields = SortConfig.fieldsFor(mediaType)
    ItemSortChip(sortOption, availableFields, onSortChanged)
}

@Composable
fun ItemSortChip(sortOption: SortOption, sortContext: SubItemContext, onSortChanged: (SortOption) -> Unit) {
    if (!SortConfig.isUserSortable(sortContext)) return
    ItemSortChip(sortOption, SortConfig.fieldsFor(sortContext), onSortChanged)
}

@Composable
fun ItemSortChip(sortOption: SortOption, availableFields: List<SortField>, onSortChanged: (SortOption) -> Unit) {
    if (availableFields.size > 1) {
        SortChip(
            currentSort = sortOption,
            availableFields = availableFields,
            onSortChanged = { onSortChanged(it) },
        )
    }
}
