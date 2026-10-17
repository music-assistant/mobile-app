package io.music_assistant.client.ui.compose.common.items

import androidx.compose.runtime.Composable
import io.music_assistant.client.data.model.client.SortConfig
import io.music_assistant.client.data.model.client.SortOffer
import io.music_assistant.client.data.model.client.SortOption
import io.music_assistant.client.data.model.client.SubItemContext
import io.music_assistant.client.ui.compose.common.SortChip

@Composable
fun ItemSortChip(sortOption: SortOption, sortContext: SubItemContext, onSortChanged: (SortOption) -> Unit) {
    if (!SortConfig.isUserSortable(sortContext)) return
    ItemSortChip(sortOption, SortConfig.fieldsFor(sortContext).map(::SortOffer), onSortChanged)
}

@Composable
fun ItemSortChip(sortOption: SortOption, offers: List<SortOffer>, onSortChanged: (SortOption) -> Unit) {
    if (offers.size > 1) {
        SortChip(
            currentSort = sortOption,
            offers = offers,
            onSortChanged = { onSortChanged(it) },
        )
    }
}
