package io.music_assistant.client.data

import io.music_assistant.client.api.Request
import io.music_assistant.client.data.model.client.Lyrics
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.client.items.PodcastEpisode
import io.music_assistant.client.data.model.client.items.RadioStation
import io.music_assistant.client.data.model.client.items.Track
import io.music_assistant.client.data.repository.MediaItemChange
import io.music_assistant.client.data.repository.MediaItemRepository
import io.music_assistant.client.data.repository.SearchResultData
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class RecordingItemDetailsMediaItemRepository(
    private val radioStation: RadioStation,
) : MediaItemRepository {

    val requests = mutableListOf<Request>()

    private val _itemChanges = MutableSharedFlow<MediaItemChange>()
    override val itemChanges = _itemChanges.asSharedFlow()

    override suspend fun fetchMediaItems(
        request: Request,
    ): Result<List<AppMediaItem>> {
        requests += request
        return Result.success(emptyList())
    }

    override suspend fun fetchAllMediaItems(
        request: Request,
    ): List<AppMediaItem>? {
        requests += request
        return emptyList()
    }

    override suspend fun search(
        request: Request,
    ): Result<SearchResultData> {
        requests += request
        return Result.success(SearchResultData.EMPTY)
    }

    override suspend fun fetchMediaItem(
        request: Request,
    ): Result<AppMediaItem?> {
        requests += request
        return Result.success(radioStation)
    }

    override suspend fun fetchTrackLyrics(track: Track): Result<Lyrics?> =
        Result.success(null)

    override suspend fun fetchEpisodeTranscript(
        episode: PodcastEpisode,
    ): Result<Lyrics?> = Result.success(null)

    override fun publishLocalChange(change: MediaItemChange) {
        _itemChanges.tryEmit(change)
    }
}
