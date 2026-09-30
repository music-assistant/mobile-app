package io.music_assistant.client.data.model.client.items

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.music_assistant.client.api.Request
import io.music_assistant.client.data.repository.MediaItemChange
import io.music_assistant.client.data.repository.MediaItemRepository
import io.music_assistant.client.data.repository.fetchMediaItems
import io.music_assistant.client.ui.compose.common.DataState
import io.music_assistant.client.ui.compose.common.StaleReason
import io.music_assistant.client.ui.compose.common.getOrEmptyList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

fun ViewModel.itemList(mediaItemRepository: MediaItemRepository): MediaItemDataMediator {
    return MediaItemDataMediator(DataState.Loading(), mediaItemRepository).updateOn(viewModelScope)
}

/**
 * Mediator for storing and managing a [DataState] of a [AppMediaItem] list retrieved from a
 * [MediaItemRepository]. The items can be kept up to date with the [MediaItemRepository] by
 * calling [updateOn].
 */
class MediaItemDataMediator(
    initial: DataState<List<AppMediaItem>>,
    private val mediaItemRepository: MediaItemRepository,
) {
    private val stateFlow = MutableStateFlow(initial)
    private var requests: List<Request>? = null

    /**
     * Retrieve and store items using [requests]. [requests] will also be used to update the items
     * if/when needed.
     */
    suspend fun set(requests: List<Request>) {
        this.requests = requests
        reload()
    }

    /**
     * Store [items]. [requests] will be used to update the items if/when needed.
     */
    fun set(items: List<AppMediaItem>, requests: List<Request>) {
        this.requests = requests
        stateFlow.value = DataState.Data(items)
    }

    fun setError() {
        stateFlow.value = DataState.Error()
    }

    fun setEmpty() {
        stateFlow.value = DataState.Data(emptyList())
    }

    fun setStale(disconnectedAt: Long, reason: StaleReason) {
        stateFlow.update {
            when (it) {
                is DataState.Data -> DataState.Stale(it.data, disconnectedAt, reason)
                else -> it
            }
        }
    }

    fun updateOn(coroutineScope: CoroutineScope): MediaItemDataMediator {
        coroutineScope.launch {
            mediaItemRepository.itemChanges.collect { change ->
                when (change) {
                    is MediaItemChange.Added -> reload()
                    is MediaItemChange.Deleted -> reload()
                    is MediaItemChange.Updated -> stateFlow.update { dataState ->
                        when (dataState) {
                            is DataState.Data -> dataState.copy(
                                data = dataState.data.replacing(change.item),
                            )

                            is DataState.Stale -> dataState.copy(
                                data = dataState.data.replacing(change.item),
                            )

                            else -> dataState
                        }
                    }
                }
            }
        }

        return this
    }

    fun asFlow(): StateFlow<DataState<List<AppMediaItem>>> {
        return stateFlow
    }

    private suspend fun reload() {
        requests?.let {
            stateFlow.value = DataState.Loading()
            try {
                val items = mediaItemRepository.fetchMediaItems(it).getOrEmptyList()
                stateFlow.value = DataState.Data(items)
            } catch (_: Exception) {
                stateFlow.value = DataState.Error()
            }
        }
    }
}

suspend fun MediaItemDataMediator.set(request: Request) {
    this.set(listOf(request))
}

fun MediaItemDataMediator.set(items: List<AppMediaItem>, request: Request) {
    this.set(items, listOf(request))
}

private fun <T : AppMediaItem> List<T>.replacing(changed: T): List<T> =
    map { if (it.hasAnyMappingFrom(changed)) changed else it }
