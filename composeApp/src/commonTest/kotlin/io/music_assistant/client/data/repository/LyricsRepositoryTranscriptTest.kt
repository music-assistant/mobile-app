package io.music_assistant.client.data.repository

import io.music_assistant.client.api.ConnectionInfo
import io.music_assistant.client.api.Request
import io.music_assistant.client.data.model.client.AppMediaItemFixtures
import io.music_assistant.client.data.model.client.Lyrics
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.client.items.PodcastEpisode
import io.music_assistant.client.data.model.client.items.Track
import io.music_assistant.client.data.model.server.EPISODE_TRANSCRIPT_MIN_SCHEMA
import io.music_assistant.client.data.model.server.ServerInfo
import io.music_assistant.client.data.model.server.StubServiceClient
import io.music_assistant.client.data.model.server.events.Event
import io.music_assistant.client.ui.compose.common.DataState
import io.music_assistant.client.utils.ConnectionData
import io.music_assistant.client.utils.SessionState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class LyricsRepositoryTranscriptTest {
    private val transcript = Lyrics.Plain("Hello.")

    @Test
    fun aServerWithoutTheCommandIsNotAsked() = runTest {
        val repository = GatedRepository()
        val client = FakeClient(EPISODE_TRANSCRIPT_MIN_SCHEMA - 1)
        val transcripts = LyricsRepository(repository, client, backgroundScope)
        val episode = AppMediaItemFixtures.episode()

        transcripts.load(episode)
        runCurrent()

        assertTrue(repository.asked.isEmpty())
        assertIs<DataState.NoData<Lyrics>>(transcripts.transcript(episode).value)
    }

    @Test
    fun anEpisodeWhoseProviderReportsNoTranscriptIsNotAsked() = runTest {
        val repository = GatedRepository()
        val transcripts = LyricsRepository(repository, FakeClient(), backgroundScope)
        val episode = AppMediaItemFixtures.episode(metadata = AppMediaItemFixtures.metadata(hasTranscript = false))

        transcripts.load(episode)
        runCurrent()

        assertTrue(repository.asked.isEmpty())
    }

    @Test
    fun aTranscriptLoadsOnceAndThenStays() = runTest {
        val repository = GatedRepository()
        val transcripts = LyricsRepository(repository, FakeClient(), backgroundScope)
        val episode = AppMediaItemFixtures.episode()

        transcripts.load(episode)
        runCurrent()
        assertIs<DataState.Loading<Lyrics>>(transcripts.transcript(episode).value)
        transcripts.load(episode)
        runCurrent()
        repository.answer(Result.success(transcript))
        runCurrent()
        transcripts.load(episode)
        runCurrent()

        assertEquals(listOf(episode.itemId), repository.asked)
        assertEquals(DataState.Data<Lyrics>(transcript), transcripts.transcript(episode).value)
    }

    @Test
    fun aServerWithoutATranscriptGivesNoData() = runTest {
        val repository = GatedRepository()
        val transcripts = LyricsRepository(repository, FakeClient(), backgroundScope)
        val episode = AppMediaItemFixtures.episode()

        transcripts.load(episode)
        runCurrent()
        repository.answer(Result.success(null))
        runCurrent()

        assertIs<DataState.NoData<Lyrics>>(transcripts.transcript(episode).value)
        transcripts.load(episode)
        runCurrent()
        assertEquals(1, repository.asked.size)
    }

    @Test
    fun aFailedLoadIsAskedAgainOnTheNextLoad() = runTest {
        val repository = GatedRepository()
        val transcripts = LyricsRepository(repository, FakeClient(), backgroundScope)
        val episode = AppMediaItemFixtures.episode()

        transcripts.load(episode)
        runCurrent()
        repository.answer(Result.failure(IllegalStateException("connection lost")))
        runCurrent()
        assertIs<DataState.Error<Lyrics>>(transcripts.transcript(episode).value)

        transcripts.load(episode)
        runCurrent()
        repository.answer(Result.success(transcript))
        runCurrent()

        assertEquals(2, repository.asked.size)
        assertEquals(DataState.Data<Lyrics>(transcript), transcripts.transcript(episode).value)
    }

    @Test
    fun theOldestSettledTranscriptGoesWhenTheCacheIsFull() = runTest {
        val repository = GatedRepository()
        val transcripts = LyricsRepository(repository, FakeClient(), backgroundScope)
        val episodes = List(9) { AppMediaItemFixtures.episode(itemId = "ep-$it") }

        episodes.forEach { episode ->
            transcripts.load(episode)
            runCurrent()
            repository.answer(Result.success(transcript))
            runCurrent()
        }

        assertIs<DataState.NoData<Lyrics>>(transcripts.transcript(episodes[0]).value)
        assertEquals(DataState.Data<Lyrics>(transcript), transcripts.transcript(episodes[1]).value)
        assertEquals(DataState.Data<Lyrics>(transcript), transcripts.transcript(episodes[8]).value)
    }

    @Test
    fun trackAndEpisodeWithSameIdentityHaveSeparateSharedRequests() = runTest {
        val repository = GatedRepository()
        val lyrics = LyricsRepository(repository, FakeClient(), backgroundScope)
        val episode = AppMediaItemFixtures.episode(itemId = "same")
        val track = AppMediaItemFixtures.track(itemId = "same").copy(provider = episode.provider)
        lyrics.load(track)
        lyrics.load(episode)
        lyrics.load(track)
        lyrics.load(episode)
        runCurrent()
        assertEquals(2, repository.asked.size)
        repository.answer(Result.success(Lyrics.Plain("Track")))
        repository.answer(Result.success(transcript))
        runCurrent()
        assertEquals(Lyrics.Plain("Track"), lyrics.lyrics(track).value.dataOrNull)
        assertEquals(transcript, lyrics.transcript(episode).value.dataOrNull)
    }

    @Test
    fun episodeCancellationClearsLoadingAndAllowsRetry() = runTest {
        val repository = GatedRepository()
        val lyrics = LyricsRepository(repository, FakeClient(), backgroundScope)
        val episode = AppMediaItemFixtures.episode()
        lyrics.load(episode)
        runCurrent()
        repository.answer(Result.failure(CancellationException("cancelled")))
        runCurrent()
        assertIs<DataState.NoData<Lyrics>>(lyrics.transcript(episode).value)
        lyrics.load(episode)
        runCurrent()
        repository.answer(Result.success(transcript))
        runCurrent()
        assertEquals(transcript, lyrics.transcript(episode).value.dataOrNull)
    }

    @Test
    fun cancellingOwnerCancelsEpisodeRequest() = runTest {
        val repository = GatedRepository()
        val owner = Job()
        val lyrics = LyricsRepository(
            repository,
            FakeClient(),
            CoroutineScope(backgroundScope.coroutineContext + owner),
        )
        val episode = AppMediaItemFixtures.episode()
        lyrics.load(episode)
        runCurrent()
        owner.cancel()
        runCurrent()
        assertTrue(owner.isCompleted)
        assertIs<DataState.NoData<Lyrics>>(lyrics.transcript(episode).value)
    }

    private class FakeClient(schemaVersion: Int = EPISODE_TRANSCRIPT_MIN_SCHEMA) : StubServiceClient() {
        override val events: Flow<Event<out Any>> = emptyFlow()

        override val sessionState: StateFlow<SessionState> = MutableStateFlow(
            SessionState.Connected.Direct(
                connectionInfo = ConnectionInfo(host = "test", port = 8095, isTls = false),
                connectionData = ConnectionData(
                    serverInfo = ServerInfo(serverId = "test", schemaVersion = schemaVersion),
                ),
            ),
        )
    }

    /** Answers each transcript request when the test says so, in request order. */
    private class GatedRepository : MediaItemRepository {
        val asked = mutableListOf<String>()
        private val pending = ArrayDeque<CompletableDeferred<Result<Lyrics?>>>()

        override suspend fun fetchEpisodeTranscript(episode: PodcastEpisode): Result<Lyrics?> {
            asked += episode.itemId
            return CompletableDeferred<Result<Lyrics?>>().also { pending.addLast(it) }.await()
        }

        fun answer(result: Result<Lyrics?>) {
            pending.removeFirst().complete(result)
        }

        override val itemChanges: SharedFlow<MediaItemChange> = MutableSharedFlow()
        override suspend fun fetchMediaItems(request: Request): Result<List<AppMediaItem>> = unused()
        override suspend fun fetchAllMediaItems(request: Request): List<AppMediaItem>? = unused()
        override suspend fun search(request: Request): Result<SearchResultData> = unused()
        override suspend fun fetchMediaItem(request: Request): Result<AppMediaItem?> = unused()
        override suspend fun fetchTrackLyrics(track: Track): Result<Lyrics?> {
            asked += track.itemId
            return CompletableDeferred<Result<Lyrics?>>().also { pending.addLast(it) }.await()
        }
        override fun publishLocalChange(change: MediaItemChange) = unused()

        private fun unused(): Nothing = error("Not used by LyricsRepository")
    }
}
