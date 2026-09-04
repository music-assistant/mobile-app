package io.music_assistant.client.data.model.client.items

import io.music_assistant.client.api.Request
import io.music_assistant.client.data.repository.MediaItemChange
import io.music_assistant.client.data.repository.MediaItemRepository
import io.music_assistant.client.ui.compose.common.DataState
import io.music_assistant.client.ui.compose.common.getOrEmptyList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
    private var request: Request? = null

    /**
     * Retrieve and store items using [request]. [request] will also be used to update the items
     * if/when needed.
     */
    suspend fun set(request: Request) {
        this.request = request
        reload()
    }

    /**
     * Store [items]. [request] will be used to update the items if/when needed.
     */
    fun set(items: List<AppMediaItem>, request: Request) {
        this.request = request
        stateFlow.value = DataState.Data(items)
    }

    fun setError() {
        stateFlow.value = DataState.Error()
    }

    fun setEmpty() {
        stateFlow.value = DataState.Data(emptyList())
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
        request?.let {
            stateFlow.value = DataState.Loading()
            try {
                stateFlow.value =
                    DataState.Data(mediaItemRepository.fetchMediaItems(it).getOrEmptyList())
            } catch (_: Exception) {
                stateFlow.value = DataState.Error()
            }
        }
    }
}

private fun <T : AppMediaItem> List<T>.replacing(changed: T): List<T> =
    map { if (it.hasAnyMappingFrom(changed)) changed else it }
