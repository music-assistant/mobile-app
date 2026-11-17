package io.music_assistant.client.data.repository

import io.music_assistant.client.data.model.client.AppMediaItemFixtures
import io.music_assistant.client.data.model.client.Lyrics
import io.music_assistant.client.data.model.client.items.Track
import io.music_assistant.client.data.model.client.items.support.StubMediaItemRepository
import io.music_assistant.client.ui.compose.common.DataState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TrackLyricsRepositoryTest {
    private val serverLyrics = Lyrics.Plain("Found by the server")

    @Test
    fun missingMetadataLoadsLyricsAndUpdatesBothObservers() = runTest {
        val repository = GatedRepository()
        val lyrics = TrackLyricsRepository(repository, backgroundScope)
        val track = AppMediaItemFixtures.track().copy(provider = "glowmusic")
        val button = lyrics.lyrics(track)
        val panel = lyrics.lyrics(track)
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
        val lyrics = TrackLyricsRepository(repository, backgroundScope)
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
        val lyrics = TrackLyricsRepository(repository, backgroundScope)
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
        val lyrics = TrackLyricsRepository(repository, backgroundScope)
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
        val lyrics = TrackLyricsRepository(repository, backgroundScope)
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
        val lyrics = TrackLyricsRepository(repository, backgroundScope)
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
        val lyrics = TrackLyricsRepository(repository, backgroundScope)
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
        val lyrics = TrackLyricsRepository(repository, backgroundScope)
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
    fun settledCacheIsBoundedAndDoesNotEvictAnInFlightRequest() = runTest {
        val repository = GatedRepository()
        val lyrics = TrackLyricsRepository(repository, backgroundScope)
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
    fun returningToCachedTrackProtectsItFromOlderPendingResponses() = runTest {
        val repository = GatedRepository()
        val lyrics = TrackLyricsRepository(repository, backgroundScope)
        val current = AppMediaItemFixtures.track(itemId = "current")
        lyrics.load(current)
        runCurrent()
        repository.answer(current, Result.success(serverLyrics))
        runCurrent()
        val previous = List(9) { AppMediaItemFixtures.track(itemId = "previous-$it") }
        previous.forEach { lyrics.load(it) }
        runCurrent()
        lyrics.load(current)

        previous.forEach { track ->
            repository.answer(track, Result.success(Lyrics.Plain("Previous")))
            runCurrent()
            assertEquals(serverLyrics, lyrics.lyrics(current).value.dataOrNull)
        }
        assertEquals(1, repository.asked.count { it.itemId == current.itemId })
    }

    @Test
    fun ownerCancellationCancelsPendingRequest() = runTest {
        val repository = GatedRepository()
        val owner = Job()
        val lyrics = TrackLyricsRepository(repository, CoroutineScope(backgroundScope.coroutineContext + owner))
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
