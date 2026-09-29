package io.music_assistant.client.ui.compose.item

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.music_assistant.client.data.model.client.SortConfig
import io.music_assistant.client.data.model.client.SortOption
import io.music_assistant.client.data.model.client.clientFiltered
import io.music_assistant.client.data.model.client.clientSorted
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.client.items.itemList
import io.music_assistant.client.data.repository.MediaItemRepository
import io.music_assistant.client.ui.compose.common.DataState
import io.music_assistant.client.ui.compose.common.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ItemListViewModel(
    private val itemList: ItemList,
    mediaItemRepository: MediaItemRepository,
) : ViewModel() {
    private val items = itemList(mediaItemRepository)
    private val sortOption = MutableStateFlow(SortConfig.defaultFor(itemList.mediaType))

    /** In-list text filter; null while the search field is closed, "" once opened. */
    private val query = MutableStateFlow<String?>(null)

    val state = combine(items.asFlow(), sortOption, query) { items, sortOption, query ->
        State(
            items = items.map { it.clientFiltered(query.orEmpty()).clientSorted(sortOption) },
            sortOption = sortOption,
            query = query,
        )
    }.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            State(items = DataState.Loading(), sortOption = sortOption.value),
        )

    init {
        viewModelScope.launch {
            items.set(itemList.toRequests())
        }
    }

    fun sort(sortOption: SortOption) {
        this.sortOption.value = sortOption
    }

    /** null closes the filter and shows the full list again; any string filters live. */
    fun filter(query: String?) {
        this.query.value = query
    }

    data class State(
        val items: DataState<List<AppMediaItem>>,
        val sortOption: SortOption,
        val query: String? = null,
    )
}
