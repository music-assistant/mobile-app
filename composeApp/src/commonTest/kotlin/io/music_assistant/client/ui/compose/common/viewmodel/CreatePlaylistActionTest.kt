package io.music_assistant.client.ui.compose.common.viewmodel

import io.music_assistant.client.data.model.client.items.Playlist
import io.music_assistant.client.data.repository.MediaItemChange
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Pins the orchestration contract of [createPlaylistAwaitingConfirmation] — the
 * one piece of non-trivial logic behind the "New playlist" button.
 *
 * The function must:
 *  - subscribe to the change stream BEFORE issuing the create request, so a
 *    server echo that fires during the request round-trip is still observed;
 *  - resolve to the matching server-confirmed [Playlist] (by display name);
 *  - ignore irrelevant changes (wrong name, non-Added);
 *  - report failure via [onError] and resolve to null without waiting;
 *  - resolve to null (silently) when no confirmation arrives within the timeout.
 *
 * Dispatcher strategy mirrors [AuthenticationViewModelTest]:
 * [UnconfinedTestDispatcher] makes the internal awaiter subscribe eagerly, so
 * the test can emit confirming events deterministically after launching the call.
 * The timeout test relies on `runTest`'s virtual clock auto-advancing while idle.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CreatePlaylistActionTest {
    @Test
    fun `confirming Added event resolves to the created playlist`() =
        runTest(UnconfinedTestDispatcher()) {
            val changes = MutableSharedFlow<MediaItemChange>()
            var errorCalls = 0

            val result = async {
                createPlaylistAwaitingConfirmation(
                    name = "Road trip",
                    itemChanges = changes,
                    timeoutMs = TIMEOUT_MS,
                    sendCreate = { Result.success(Unit) },
                    onError = { errorCalls++ },
                )
            }

            // Awaiter is already subscribed (unconfined, eager). Confirm it.
            changes.emit(MediaItemChange.Added(playlist("Road trip")))

            assertEquals("Road trip", result.await()?.displayName)
            assertEquals(0, errorCalls)
        }

    @Test
    fun `event emitted during the create request is not missed`() =
        runTest(UnconfinedTestDispatcher()) {
            val changes = MutableSharedFlow<MediaItemChange>()

            // The server "echoes" the new playlist as part of the request turn.
            // Because we subscribe before sending, this is still caught.
            val result = createPlaylistAwaitingConfirmation(
                name = "Mixtape",
                itemChanges = changes,
                timeoutMs = TIMEOUT_MS,
                sendCreate = {
                    changes.emit(MediaItemChange.Added(playlist("Mixtape")))
                    Result.success(Unit)
                },
                onError = { },
            )

            assertEquals("Mixtape", result?.displayName)
        }

    @Test
    fun `non-matching changes are ignored until the matching one arrives`() =
        runTest(UnconfinedTestDispatcher()) {
            val changes = MutableSharedFlow<MediaItemChange>()

            val result = async {
                createPlaylistAwaitingConfirmation(
                    name = "Focus",
                    itemChanges = changes,
                    timeoutMs = TIMEOUT_MS,
                    sendCreate = { Result.success(Unit) },
                    onError = { },
                )
            }

            // Wrong name, and a non-Added change carrying the right name: both ignored.
            changes.emit(MediaItemChange.Added(playlist("Other")))
            changes.emit(MediaItemChange.Updated(playlist("Focus")))
            changes.emit(MediaItemChange.Added(playlist("Focus")))

            assertEquals("Focus", result.await()?.displayName)
        }

    @Test
    fun `request failure invokes onError and resolves to null without waiting`() =
        runTest(UnconfinedTestDispatcher()) {
            val changes = MutableSharedFlow<MediaItemChange>()
            var errorCalls = 0

            val result = createPlaylistAwaitingConfirmation(
                name = "Nope",
                itemChanges = changes,
                timeoutMs = TIMEOUT_MS,
                sendCreate = { Result.failure<Unit>(RuntimeException("boom")) },
                onError = { errorCalls++ },
            )

            assertNull(result)
            assertEquals(1, errorCalls)
        }

    @Test
    fun `missing confirmation within the timeout resolves to null without error`() =
        runTest(UnconfinedTestDispatcher()) {
            val changes = MutableSharedFlow<MediaItemChange>()
            var errorCalls = 0

            // Nothing is ever emitted; the awaiter times out on virtual time.
            val result = createPlaylistAwaitingConfirmation(
                name = "Ghost",
                itemChanges = changes,
                timeoutMs = TIMEOUT_MS,
                sendCreate = { Result.success(Unit) },
                onError = { errorCalls++ },
            )

            assertNull(result)
            assertEquals(0, errorCalls, "timeout is not a request failure")
        }

    private fun playlist(name: String): Playlist = Playlist(
        itemId = name,
        provider = "library",
        name = name,
        providerMappings = null,
        metadata = null,
        favorite = null,
        uri = null,
        images = emptyMap(),
        isEditable = true,
        isDynamic = false,
    )

    private companion object {
        private const val TIMEOUT_MS = 5_000L
    }
}
