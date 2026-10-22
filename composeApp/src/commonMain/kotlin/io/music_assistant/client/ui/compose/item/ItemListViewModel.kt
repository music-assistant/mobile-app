package io.music_assistant.client.ui.compose.item

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.music_assistant.client.data.model.client.SortOption
import io.music_assistant.client.data.model.client.clientFiltered
import io.music_assistant.client.data.model.client.clientSorted
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.client.items.itemList
import io.music_assistant.client.data.repository.MediaItemRepository
import io.music_assistant.client.settings.SettingsRepository
import io.music_assistant.client.ui.compose.common.DataState
import io.music_assistant.client.ui.compose.common.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ItemListViewModel(
    private val itemList: ItemList,
    mediaItemRepository: MediaItemRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    private val items = itemList(mediaItemRepository)
    private val sortOption = MutableStateFlow(settingsRepository.getSortOption(itemList.sortContext))

    /**
     * In-list text filter; null while the search field is closed, "" once opened. Exposed directly,
     * not through [state]: the text field must read back its own value synchronously, or a stale
     * value from the off-Main combine resets the cursor.
     */
    private val _query = MutableStateFlow<String?>(null)
    val query: StateFlow<String?> = _query.asStateFlow()

    val state = combine(items.asFlow(), sortOption, _query) { items, sortOption, query ->
        State(
            items = items.map { it.clientFiltered(query.orEmpty()).clientSorted(sortOption, itemList.sortContext) },
            sortOption = sortOption,
        )
    }.flowOn(Dispatchers.Default) // filter + sort per keystroke and per list change
        .stateIn(
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
        settingsRepository.setSortOption(itemList.sortContext, sortOption)
        this.sortOption.value = sortOption
    }

    /** null closes the filter and shows the full list again; any string filters live. */
    fun filter(query: String?) {
        _query.value = query
    }

    data class State(
        val items: DataState<List<AppMediaItem>>,
        val sortOption: SortOption,
    )
}
