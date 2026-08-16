package io.music_assistant.client.data

import io.music_assistant.client.api.APICommands
import io.music_assistant.client.api.Answer
import io.music_assistant.client.api.ConnectionInfo
import io.music_assistant.client.api.Request
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.data.model.client.QueueOption
import io.music_assistant.client.data.model.server.events.Event
import io.music_assistant.client.utils.SessionState
import io.music_assistant.client.webrtc.DataChannelWrapper
import io.music_assistant.client.webrtc.model.RemoteId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

class LocalPlayerDispatchTest {
    // --- planLocalPlayerDispatch ---

    @Test
    fun planReturnsNullWhenNoLocalPlayer() {
        val plan = planLocalPlayerDispatch(
            localPlayerId = null,
            localPlayerSyncedTo = null,
            mediaUris = listOf("library://album/42"),
            option = QueueOption.PLAY,
        )
        assertNull(plan)
    }

    @Test
    fun planReturnsNullWhenMediaUrisEmpty() {
        // Container items occasionally surface without a URI (mostly Genre,
        // pre-synthesis); voice-search paths can also reduce to nothing playable.
        // The planner refuses rather than dispatch an empty media list and let
        // MA error out remotely.
        val plan = planLocalPlayerDispatch(
            localPlayerId = "sendspin-local",
            localPlayerSyncedTo = null,
            mediaUris = emptyList(),
            option = QueueOption.PLAY,
        )
        assertNull(plan)
    }

    @Test
    fun planCarriesAllFieldsThroughWhenNotSynced() {
        val plan = planLocalPlayerDispatch(
            localPlayerId = "sendspin-local",
            localPlayerSyncedTo = null,
            mediaUris = listOf("library://album/42"),
            option = QueueOption.REPLACE,
        )
        assertNotNull(plan)
        assertEquals("sendspin-local", plan.playerId)
        assertEquals(listOf("library://album/42"), plan.mediaUris)
        assertEquals(QueueOption.REPLACE, plan.option)
        assertNull(plan.detachFrom)
    }

    @Test
    fun planSetsDetachFromWhenSyncedToIsNonNull() {
        val plan = planLocalPlayerDispatch(
            localPlayerId = "sendspin-local",
            localPlayerSyncedTo = "kitchen-group",
            mediaUris = listOf("library://playlist/1"),
            option = QueueOption.ADD,
        )
        assertNotNull(plan)
        assertEquals("kitchen-group", plan.detachFrom)
        // The plan targets the LOCAL player, never the group it was synced
        // to. Catches the easy bug of accidentally routing the play request
        // at the group ID.
        assertEquals("sendspin-local", plan.playerId)
    }

    @Test
    fun planCarriesStartItemThrough() {
        val plan = planLocalPlayerDispatch(
            localPlayerId = "sendspin-local",
            localPlayerSyncedTo = null,
            mediaUris = listOf("library://album/42"),
            option = QueueOption.REPLACE,
            startItem = "track-7",
        )
        assertEquals("track-7", plan?.startItem)
    }

    @Test
    fun planDefaultsStartItemToNull() {
        val plan = planLocalPlayerDispatch(
            localPlayerId = "sendspin-local",
            localPlayerSyncedTo = null,
            mediaUris = listOf("library://album/42"),
            option = QueueOption.REPLACE,
        )
        assertNull(plan?.startItem)
    }

    @Test
    fun planPreservesEveryQueueOption() {
        // Pins the option through verbatim — guards against a future
        // refactor introducing per-option dispatch branches in the planner.
        QueueOption.entries.forEach { option ->
            val plan = planLocalPlayerDispatch(
                localPlayerId = "p",
                localPlayerSyncedTo = null,
                mediaUris = listOf("uri"),
                option = option,
            )
            assertEquals(option, plan?.option, "option=$option round-trip mismatch")
        }
    }

    // --- executeLocalPlayerDispatch ---

    @Test
    fun executeSendsOnlyPlayWhenNotDetaching() = runBlocking {
        val client = RecordingClient()
        executeLocalPlayerDispatch(
            client,
            LocalPlayerDispatchPlan(
                playerId = "sendspin-local",
                mediaUris = listOf("library://album/42"),
                detachFrom = null,
                option = QueueOption.PLAY,
            ),
        )
        assertEquals(1, client.sent.size)
        assertEquals(PLAY_MEDIA, client.sent[0].command)
    }

    @Test
    fun executeSendsDetachBeforePlayWhenSyncedTo() = runBlocking {
        // Ordering is load-bearing: MA promotes `play(queueOrPlayerId=childId)`
        // to the group queue if the detach hasn't landed first, which would
        // re-introduce the exact bug the unconditional detach exists to
        // prevent.
        val client = RecordingClient()
        executeLocalPlayerDispatch(
            client,
            LocalPlayerDispatchPlan(
                playerId = "sendspin-local",
                mediaUris = listOf("library://album/42"),
                detachFrom = "kitchen-group",
                option = QueueOption.REPLACE,
            ),
        )
        assertEquals(2, client.sent.size)
        assertEquals(SET_MEMBERS, client.sent[0].command)
        assertEquals(PLAY_MEDIA, client.sent[1].command)
    }

    @Test
    fun executeDetachRequestTargetsTheGroupAndRemovesTheLocalPlayer() = runBlocking {
        val client = RecordingClient()
        executeLocalPlayerDispatch(
            client,
            LocalPlayerDispatchPlan(
                playerId = "sendspin-local",
                mediaUris = listOf("uri"),
                detachFrom = "kitchen-group",
                option = QueueOption.PLAY,
            ),
        )
        val detachArgs = client.sent.first { it.command == SET_MEMBERS }.args
        assertNotNull(detachArgs)
        assertEquals("kitchen-group", detachArgs["target_player"]?.jsonPrimitive?.content)
        val removed = detachArgs["player_ids_to_remove"]?.jsonArray
            ?.map { it.jsonPrimitive.content }
        assertContentEquals(listOf("sendspin-local"), removed)
    }

    @Test
    fun executePlayRequestCarriesMediaUrisPlayerIdAndServerEncodedOption() = runBlocking {
        QueueOption.entries.forEach { option ->
            val client = RecordingClient()
            executeLocalPlayerDispatch(
                client,
                LocalPlayerDispatchPlan(
                    playerId = "sendspin-local",
                    mediaUris = listOf("library://album/42"),
                    detachFrom = null,
                    option = option,
                ),
            )
            val playArgs = client.sent.single().args
            assertNotNull(playArgs)
            assertEquals(
                option.serverValue,
                playArgs["option"]?.jsonPrimitive?.content,
                "option=$option not propagated to server payload",
            )
            assertEquals(
                "sendspin-local",
                playArgs["queue_id"]?.jsonPrimitive?.content,
                "queue_id should be the local player id, not the group",
            )
            val media = playArgs["media"]?.jsonArray?.map { it.jsonPrimitive.content }
            assertContentEquals(listOf("library://album/42"), media)
        }
    }

    @Test
    fun executePutsStartItemInPlayPayloadWhenSet() = runBlocking {
        val client = RecordingClient()
        executeLocalPlayerDispatch(
            client,
            LocalPlayerDispatchPlan(
                playerId = "sendspin-local",
                mediaUris = listOf("library://album/42"),
                detachFrom = null,
                option = QueueOption.REPLACE,
                startItem = "track-7",
            ),
        )
        val playArgs = client.sent.single().args
        assertNotNull(playArgs)
        assertEquals("track-7", playArgs["start_item"]?.jsonPrimitive?.content)
    }

    @Test
    fun executeOmitsStartItemFromPlayPayloadWhenNull() = runBlocking {
        val client = RecordingClient()
        executeLocalPlayerDispatch(
            client,
            LocalPlayerDispatchPlan(
                playerId = "sendspin-local",
                mediaUris = listOf("library://album/42"),
                detachFrom = null,
                option = QueueOption.REPLACE,
            ),
        )
        val playArgs = client.sent.single().args
        assertNotNull(playArgs)
        assertNull(playArgs["start_item"])
    }

    @Test
    fun executePassesMultiUriBatchVerbatimAsServerMediaArray() = runBlocking {
        // AA's pre-ordered batches must reach the server unchanged.
        val uris = listOf(
            "library://track/1",
            "library://track/2",
            "library://track/3",
        )
        val client = RecordingClient()
        executeLocalPlayerDispatch(
            client,
            LocalPlayerDispatchPlan(
                playerId = "sendspin-local",
                mediaUris = uris,
                detachFrom = null,
                option = QueueOption.REPLACE,
            ),
        )
        val media = client.sent.single().args
            ?.get("media")?.jsonArray?.map { it.jsonPrimitive.content }
        assertContentEquals(uris, media)
    }

    @Test
    fun executeStillFiresPlayWhenDetachRpcFails() = runBlocking {
        // sendRequest is contractually no-throw; failures land in Result.failure.
        // The play RPC must not be skipped just because detach failed — at worst
        // we land in MA's group-queue branch, which is no worse than not detaching
        // at all, and silently dropping the play would leave the user staring at
        // CarPlay wondering why nothing happened.
        val client = RecordingClient { request ->
            if (request.command == SET_MEMBERS) {
                Result.failure(IllegalStateException("simulated detach failure"))
            } else {
                Result.success(emptyAnswer())
            }
        }
        executeLocalPlayerDispatch(
            client,
            LocalPlayerDispatchPlan(
                playerId = "sendspin-local",
                mediaUris = listOf("uri"),
                detachFrom = "kitchen-group",
                option = QueueOption.PLAY,
            ),
        )
        assertEquals(2, client.sent.size, "play must still fire after a failed detach")
        assertEquals(PLAY_MEDIA, client.sent[1].command)
    }

    @Test
    fun executeReportsRpcFailuresViaCallbackWithLabel() = runBlocking {
        val failures = mutableListOf<Pair<String, Throwable>>()
        val detachError = IllegalStateException("boom-detach")
        val playError = IllegalStateException("boom-play")
        val client = RecordingClient { request ->
            when (request.command) {
                SET_MEMBERS -> Result.failure(detachError)
                PLAY_MEDIA -> Result.failure(playError)
                else -> fail("unexpected command ${request.command}")
            }
        }
        executeLocalPlayerDispatch(
            client,
            LocalPlayerDispatchPlan(
                playerId = "p",
                mediaUris = listOf("u"),
                detachFrom = "g",
                option = QueueOption.REPLACE,
            ),
            onRpcFailure = { label, error -> failures.add(label to error) },
        )
        assertEquals(2, failures.size)
        // Labels must distinguish which RPC failed so logs aren't ambiguous.
        assertEquals("detach", failures[0].first)
        assertEquals(detachError, failures[0].second)
        assertTrue(
            failures[1].first.startsWith("play("),
            "play failure label should include the option for log triage; got '${failures[1].first}'",
        )
        assertEquals(playError, failures[1].second)
    }

    @Test
    fun executeDoesNotInvokeFailureCallbackOnSuccess() = runBlocking {
        var calls = 0
        val client = RecordingClient { Result.success(emptyAnswer()) }
        executeLocalPlayerDispatch(
            client,
            LocalPlayerDispatchPlan(
                playerId = "p",
                mediaUris = listOf("u"),
                detachFrom = "g",
                option = QueueOption.PLAY,
            ),
            onRpcFailure = { _, _ -> calls++ },
        )
        assertEquals(0, calls)
    }

    private companion object {
        const val PLAY_MEDIA = APICommands.PLAYER_QUEUES_PLAY_MEDIA
        const val SET_MEMBERS = APICommands.PLAYERS_CMD_SET_MEMBERS

        fun emptyAnswer(): Answer = Answer(
            json = buildJsonObject {
                put("message_id", JsonPrimitive("fake"))
            },
        )
    }
}

/**
 * Stub [ServiceClient] that records every request it sees and returns a
 * configurable response per call. Only [sendRequest] is implemented;
 * everything else throws because the unit under test never touches it.
 */
private class RecordingClient(
    private val respondWith: (Request) -> Result<Answer> = {
        Result.success(
            Answer(
                json = buildJsonObject {
                    put("message_id", JsonPrimitive("fake"))
                },
            ),
        )
    },
) : ServiceClient {
    val sent: MutableList<Request> = mutableListOf()

    override suspend fun sendRequest(request: Request): Result<Answer> {
        sent.add(request)
        return respondWith(request)
    }

    override val sessionState: StateFlow<SessionState>
        get() = error("not used")
    override val isReadyForCommands: StateFlow<Boolean>
        get() = error("not used")
    override val externalConsumerActive: StateFlow<Boolean>
        get() = error("not used")
    override val events: Flow<Event<out Any>>
        get() = error("not used")
    override val webrtcSendspinChannel: DataChannelWrapper? = null
    override val webRTCHttpProxy: io.music_assistant.client.webrtc.WebRTCHttpProxy? = null
    override val foregroundEvents: Flow<Unit>
        get() = error("not used")

    override suspend fun login(username: String, password: String) = Unit
    override suspend fun authorize(token: String, isAutoLogin: Boolean) = Unit
    override fun logout() = Unit
    override fun resolveImageUrl(
        path: String,
        provider: String,
        isRemotelyAccessible: Boolean,
        proxyId: String?,
    ): String? = null
    override fun rebaseServerImageUrl(rawUrl: String): String? = null
    override fun forceWebRTCReconnect() = Unit
    override fun onAppForeground() = Unit
    override fun onAppBackground() = Unit
    override fun disconnectByUser() = Unit
    override fun connect(connection: ConnectionInfo) = Unit
    override fun connectWebRTC(remoteId: RemoteId) = Unit
    override fun onExternalConsumerActive() = Unit
    override fun onPlaybackActive() = Unit
    override fun onExternalConsumerInactive() = Unit
    override fun onPlaybackInactive() = Unit
    override fun forceDisconnect(reason: Exception) = Unit
    override fun noServer() = Unit
}
