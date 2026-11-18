package io.music_assistant.client.data.repository

import io.music_assistant.client.data.FakeItemDetailsServiceClient
import io.music_assistant.client.data.model.client.AppMediaItemFixtures
import io.music_assistant.client.data.model.client.Lyrics
import io.music_assistant.client.data.model.client.items.Track
import io.music_assistant.client.data.model.client.items.support.StubMediaItemRepository
import io.music_assistant.client.ui.compose.common.DataState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class LyricsRepositoryTrackTest {
    private val serverLyrics = Lyrics.Plain("Found by the server")

    @Test
    fun missingMetadataLoadsLyricsAndUpdatesBothObservers() = runTest {
        val repository = GatedRepository()
        val lyrics = LyricsRepository(repository, FakeItemDetailsServiceClient(), backgroundScope)
        val track = AppMediaItemFixtures.track().copy(provider = "glowmusic")
        val button = lyrics.lyrics(track)
        val panel = lyrics.lyrics(track)
        lyrics.load(track) // Item Details and Now Playing request the same singleton concurrently.
        val observed = mutableListOf<DataState<Lyrics>>()
        backgroundScope.launch { button.collect { observed += it } }

        assertNull(track.lyrics)
        lyrics.load(track)
        runCurrent()
        assertIs<DataState.Loading<Lyrics>>(button.value)
        repository.answer(track, Result.success(serverLyrics))
        runCurrent()

        assertEquals(serverLyrics, button.value.dataOrNull)
        assertEquals(serverLyrics, panel.value.dataOrNull)
        assertEquals(serverLyrics, observed.last().dataOrNull)
        assertEquals(listOf(track), repository.asked)
    }

    @Test
    fun embeddedLyricsAreAvailableImmediatelyWithoutARequest() = runTest {
        val repository = GatedRepository()
        val lyrics = LyricsRepository(repository, FakeItemDetailsServiceClient(), backgroundScope)
        val track = AppMediaItemFixtures.track().copy(
            metadata = AppMediaItemFixtures.metadata().copy(lyrics = "Embedded"),
        )

        assertEquals(Lyrics.Plain("Embedded"), lyrics.lyrics(track).value.dataOrNull)
        lyrics.load(track)
        runCurrent()
        assertTrue(repository.asked.isEmpty())
    }

    @Test
    fun metadataArrivingDuringARequestWinsOverTheServerResult() = runTest {
        val repository = GatedRepository()
        val lyrics = LyricsRepository(repository, FakeItemDetailsServiceClient(), backgroundScope)
        val track = AppMediaItemFixtures.track()
        lyrics.load(track)
        runCurrent()
        val updated = track.copy(metadata = AppMediaItemFixtures.metadata().copy(lyrics = "Embedded"))
        lyrics.load(updated)
        repository.answer(track, Result.success(serverLyrics))
        runCurrent()

        assertEquals(Lyrics.Plain("Embedded"), lyrics.lyrics(updated).value.dataOrNull)
        assertEquals(1, repository.asked.size)
    }

    @Test
    fun absentLyricsAreCachedWithoutRepeatedRequests() = runTest {
        val repository = GatedRepository()
        val lyrics = LyricsRepository(repository, FakeItemDetailsServiceClient(), backgroundScope)
        val track = AppMediaItemFixtures.track()
        lyrics.load(track)
        runCurrent()
        repository.answer(track, Result.success(null))
        runCurrent()
        lyrics.load(track)
        runCurrent()

        assertIs<DataState.NoData<Lyrics>>(lyrics.lyrics(track).value)
        assertEquals(1, repository.asked.size)
    }

    @Test
    fun repeatedLoadsAndTrackCopiesShareOneInFlightAndCachedRequest() = runTest {
        val repository = GatedRepository()
        val lyrics = LyricsRepository(repository, FakeItemDetailsServiceClient(), backgroundScope)
        val track = AppMediaItemFixtures.track()
        repeat(3) { lyrics.load(track.copy(favorite = it == 0)) }
        runCurrent()
        lyrics.load(track)
        runCurrent()
        assertEquals(1, repository.asked.size)

        repository.answer(track, Result.success(serverLyrics))
        runCurrent()
        lyrics.load(track.copy())
        runCurrent()
        assertEquals(1, repository.asked.size)
        assertEquals(serverLyrics, lyrics.lyrics(track).value.dataOrNull)
    }

    @Test
    fun lateResponseForPreviousTrackCannotReplaceCurrentLyrics() = runTest {
        val repository = GatedRepository()
        val lyrics = LyricsRepository(repository, FakeItemDetailsServiceClient(), backgroundScope)
        val previous = AppMediaItemFixtures.track(itemId = "previous")
        val current = AppMediaItemFixtures.track(itemId = "current")
        lyrics.load(previous)
        runCurrent()
        lyrics.load(current)
        runCurrent()
        assertNull(lyrics.lyrics(current).value.dataOrNull)

        repository.answer(current, Result.success(serverLyrics))
        runCurrent()
        repository.answer(previous, Result.success(Lyrics.Plain("Previous")))
        runCurrent()

        assertEquals(serverLyrics, lyrics.lyrics(current).value.dataOrNull)
        lyrics.load(previous) // Swiping back to a player reuses its own answer.
        runCurrent()
        assertEquals(Lyrics.Plain("Previous"), lyrics.lyrics(previous).value.dataOrNull)
        assertEquals(2, repository.asked.size)
    }

    @Test
    fun identicalItemIdsFromDifferentProvidersHaveSeparateLyrics() = runTest {
        val repository = GatedRepository()
        val lyrics = LyricsRepository(repository, FakeItemDetailsServiceClient(), backgroundScope)
        val first = AppMediaItemFixtures.track(itemId = "same").copy(provider = "first")
        val second = first.copy(provider = "second")
        lyrics.load(first)
        lyrics.load(second)
        runCurrent()
        repository.answer(first, Result.success(serverLyrics))
        repository.answer(second, Result.success(null))
        runCurrent()

        assertEquals(serverLyrics, lyrics.lyrics(first).value.dataOrNull)
        assertNull(lyrics.lyrics(second).value.dataOrNull)
    }

    @Test
    fun failedRequestIsHiddenAndCanRetryOnNextLoad() = runTest {
        val repository = GatedRepository()
        val lyrics = LyricsRepository(repository, FakeItemDetailsServiceClient(), backgroundScope)
        val track = AppMediaItemFixtures.track()
        lyrics.load(track)
        runCurrent()
        repository.answer(track, Result.failure(IllegalStateException("offline")))
        runCurrent()
        assertIs<DataState.Error<Lyrics>>(lyrics.lyrics(track).value)
        assertNull(lyrics.lyrics(track).value.dataOrNull)

        lyrics.load(track)
        runCurrent()
        repository.answer(track, Result.success(serverLyrics))
        runCurrent()
        assertEquals(serverLyrics, lyrics.lyrics(track).value.dataOrNull)
        assertEquals(2, repository.asked.size)
    }

    @Test
    fun settledEntriesAreEvictedInCompletionOrder() = runTest {
        val repository = GatedRepository()
        val lyrics = LyricsRepository(repository, FakeItemDetailsServiceClient(), backgroundScope)
        val first = AppMediaItemFixtures.track(itemId = "A")
        val second = AppMediaItemFixtures.track(itemId = "B")
        lyrics.load(first)
        runCurrent()
        lyrics.load(second)
        runCurrent()
        assertEquals(listOf(first, second), repository.asked)

        repository.answer(second, Result.success(serverLyrics))
        runCurrent()
        assertIs<DataState.Loading<Lyrics>>(lyrics.lyrics(first).value)
        repository.answer(first, Result.success(serverLyrics))
        runCurrent()

        // Seven more answers exceed the eight-entry cache: B completed first, so it goes first.
        repeat(7) { index ->
            val track = AppMediaItemFixtures.track(itemId = "later-$index")
            lyrics.load(track)
            runCurrent()
            repository.answer(track, Result.success(serverLyrics))
            runCurrent()
        }

        assertIs<DataState.NoData<Lyrics>>(lyrics.lyrics(second).value)
        assertEquals(serverLyrics, lyrics.lyrics(first).value.dataOrNull)
    }

    @Test
    fun settledCacheIsBoundedAndDoesNotEvictAnInFlightRequest() = runTest {
        val repository = GatedRepository()
        val lyrics = LyricsRepository(repository, FakeItemDetailsServiceClient(), backgroundScope)
        val pending = AppMediaItemFixtures.track(itemId = "pending")
        val tracks = List(9) { AppMediaItemFixtures.track(itemId = "track-$it") }
        lyrics.load(pending)
        tracks.forEach { track ->
            lyrics.load(track)
            runCurrent()
            repository.answer(track, Result.success(serverLyrics))
            runCurrent()
        }

        assertIs<DataState.Loading<Lyrics>>(lyrics.lyrics(pending).value)
        assertIs<DataState.NoData<Lyrics>>(lyrics.lyrics(tracks.first()).value)
        assertEquals(serverLyrics, lyrics.lyrics(tracks.last()).value.dataOrNull)
        lyrics.load(pending)
        runCurrent()
        assertEquals(1, repository.asked.count { it.itemId == pending.itemId })
    }

    @Test
    fun ownerCancellationCancelsPendingRequest() = runTest {
        val repository = GatedRepository()
        val owner = Job()
        val lyrics = LyricsRepository(
            repository,
            FakeItemDetailsServiceClient(),
            CoroutineScope(backgroundScope.coroutineContext + owner),
        )
        val track = AppMediaItemFixtures.track()
        lyrics.load(track)
        runCurrent()

        owner.cancel()
        runCurrent()
        assertTrue(owner.isCompleted)
        repository.answer(track, Result.success(serverLyrics))
        runCurrent()
        assertIs<DataState.NoData<Lyrics>>(lyrics.lyrics(track).value)
    }

    @Test
    fun simultaneousCallersReserveOnlyOneRequest() = runTest {
        val repository = GatedRepository()
        val lyrics = LyricsRepository(repository, FakeItemDetailsServiceClient(), backgroundScope)
        val track = AppMediaItemFixtures.track()
        val start = CompletableDeferred<Unit>()
        val callers = List(32) {
            launch(Dispatchers.Default) {
                start.await()
                lyrics.load(track)
            }
        }

        start.complete(Unit)
        callers.joinAll()
        assertIs<DataState.Loading<Lyrics>>(lyrics.lyrics(track).value)
        runCurrent()
        assertEquals(listOf(track), repository.asked)

        repository.answer(track, Result.success(serverLyrics))
        runCurrent()
        assertEquals(serverLyrics, lyrics.lyrics(track).value.dataOrNull)
    }

    @Test
    fun cancellingOneScreenCollectorDoesNotCancelSharedLookup() = runTest {
        val repository = GatedRepository()
        val lyrics = LyricsRepository(repository, FakeItemDetailsServiceClient(), backgroundScope)
        val track = AppMediaItemFixtures.track()
        val itemDetails = backgroundScope.launch { lyrics.lyrics(track).collect() }
        val nowPlaying = lyrics.lyrics(track)
        val callers = List(2) { backgroundScope.launch { lyrics.load(track) } }
        runCurrent()
        callers.forEach { it.join() }
        itemDetails.cancel()
        repository.answer(track, Result.success(serverLyrics))
        runCurrent()
        assertEquals(1, repository.asked.size)
        assertEquals(serverLyrics, nowPlaying.value.dataOrNull)
    }

    private class GatedRepository : MediaItemRepository by StubMediaItemRepository() {
        val asked = mutableListOf<Track>()
        private val pending = mutableMapOf<Pair<String, String>, CompletableDeferred<Result<Lyrics?>>>()

        override suspend fun fetchTrackLyrics(track: Track): Result<Lyrics?> {
            asked += track
            return CompletableDeferred<Result<Lyrics?>>()
                .also { pending[track.provider to track.itemId] = it }.await()
        }

        fun answer(track: Track, result: Result<Lyrics?>) {
            pending.remove(track.provider to track.itemId)!!.complete(result)
        }
    }
}
