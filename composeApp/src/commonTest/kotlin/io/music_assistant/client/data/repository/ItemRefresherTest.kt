package io.music_assistant.client.data.repository

import io.music_assistant.client.api.APICommands
import io.music_assistant.client.api.Request
import io.music_assistant.client.data.model.client.AppMediaItemFixtures
import io.music_assistant.client.data.model.client.Lyrics
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.client.items.PodcastEpisode
import io.music_assistant.client.data.model.client.items.Track
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ItemRefresherTest {
    private val artist = AppMediaItemFixtures.artist(itemId = "8")

    @Test
    fun `a second refresh of a running item is not sent`() = runTest {
        val repository = GatedRepository()
        val refresher = ItemRefresher(repository, backgroundScope)

        refresher.refresh(URI)
        runCurrent()
        refresher.refresh(URI)
        runCurrent()

        assertEquals(listOf(URI), repository.sentUris)
        assertEquals(setOf(URI), refresher.refreshingUris.value)
    }

    @Test
    fun `an answer publishes the item without its favorite and then gives the result`() = runTest {
        val repository = GatedRepository()
        val refresher = ItemRefresher(repository, backgroundScope)
        val results = mutableListOf<ItemRefresher.Refreshed>()
        backgroundScope.launch { refresher.refreshed.collect { results += it } }
        runCurrent()

        refresher.refresh(URI)
        runCurrent()
        repository.answer(Result.success(artist))
        runCurrent()

        val change = assertIs<MediaItemChange.Updated>(repository.published.single())
        assertFalse(change.carriesFavorite)
        assertEquals(listOf(ItemRefresher.Refreshed(URI, artist)), results)
        assertEquals(emptySet(), refresher.refreshingUris.value)
    }

    @Test
    fun `a failed refresh publishes nothing and ends the refresh`() = runTest {
        val repository = GatedRepository()
        val refresher = ItemRefresher(repository, backgroundScope)

        refresher.refresh(URI)
        runCurrent()
        // RpcEngine.failAll answers every open request this way when the connection drops.
        repository.answer(Result.failure(IllegalStateException("connection lost")))
        runCurrent()

        assertTrue(repository.published.isEmpty())
        assertEquals(emptySet(), refresher.refreshingUris.value)
    }

    @Test
    fun `a refresh after the last one ended is sent`() = runTest {
        val repository = GatedRepository()
        val refresher = ItemRefresher(repository, backgroundScope)

        refresher.refresh(URI)
        runCurrent()
        repository.answer(Result.success(artist))
        runCurrent()
        refresher.refresh(URI)
        runCurrent()

        assertEquals(listOf(URI, URI), repository.sentUris)
    }

    /** Holds every refresh until the test answers it, oldest first. */
    private class GatedRepository : MediaItemRepository {
        private val sent = mutableListOf<Request>()
        private val pending = ArrayDeque<CompletableDeferred<Result<AppMediaItem?>>>()
        val published = mutableListOf<MediaItemChange>()

        val sentUris: List<String>
            get() = sent.map { request ->
                assertEquals(APICommands.MUSIC_REFRESH_ITEM, request.command)
                request.args?.get("media_item")?.jsonPrimitive?.content.orEmpty()
            }

        override suspend fun fetchMediaItem(request: Request): Result<AppMediaItem?> {
            sent += request
            return CompletableDeferred<Result<AppMediaItem?>>().also { pending.addLast(it) }.await()
        }

        fun answer(result: Result<AppMediaItem?>) {
            pending.removeFirst().complete(result)
        }

        override fun publishLocalChange(change: MediaItemChange) {
            published += change
        }

        override val itemChanges: SharedFlow<MediaItemChange> = MutableSharedFlow()
        override suspend fun fetchMediaItems(request: Request): Result<List<AppMediaItem>> = unused()
        override suspend fun fetchAllMediaItems(request: Request): List<AppMediaItem>? = unused()
        override suspend fun search(request: Request): Result<SearchResultData> = unused()
        override suspend fun fetchTrackLyrics(track: Track): Result<Lyrics?> = unused()
        override fun supportsRadioTracks(): Boolean = false
        override suspend fun fetchEpisodeTranscript(episode: PodcastEpisode): Result<Lyrics?> = unused()

        private fun unused(): Nothing = error("Not used by ItemRefresher")
    }

    private companion object {
        const val URI = "library://artist/8"
    }
}
