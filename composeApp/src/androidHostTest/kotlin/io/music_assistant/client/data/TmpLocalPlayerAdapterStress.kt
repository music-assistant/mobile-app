package io.music_assistant.client.data

import com.russhwolf.settings.MapSettings
import io.music_assistant.client.api.Answer
import io.music_assistant.client.api.ErrorMessageBus
import io.music_assistant.client.api.Request
import io.music_assistant.client.data.model.server.StubServiceClient
import io.music_assistant.client.settings.SettingsRepository
import io.music_assistant.client.utils.SessionState
import io.music_assistant.sendspin.api.PlayerEvent
import io.music_assistant.sendspin.api.PlayerState
import io.music_assistant.sendspin.api.SendspinPlayer
import io.music_assistant.sendspin.api.StopCause
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.buildJsonObject
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.test.Test

// TEMPORARY stress harness, deleted after the investigation.
class TmpLocalPlayerAdapterStress {
    private class Player : SendspinPlayer {
        override val state = MutableStateFlow<PlayerState>(PlayerState.Disabled)
        private val events0 = MutableSharedFlow<PlayerEvent>(extraBufferCapacity = 16)
        override val events: Flow<PlayerEvent> = events0
        val held = MutableStateFlow(false)
        val log = ConcurrentLinkedQueue<String>()
        var adapter: LocalPlayerAdapter? = null
        override fun hold(held: Boolean) {
            log += "hold($held) on ${Thread.currentThread().name} isPlaying=${adapter?.localPlayerData?.value?.player?.isPlaying}"
            this.held.value = held
        }
        suspend fun emit(event: PlayerEvent) {
            events0.subscriptionCount.first { it > 0 }
            events0.emit(event)
        }
    }

    private class Client(val log: ConcurrentLinkedQueue<String>) : StubServiceClient() {
        override val sessionState = MutableStateFlow<SessionState>(SessionState.Disconnected.Initial)
        override val isReadyForCommands = MutableStateFlow(true)
        val sent = Channel<Request>(Channel.UNLIMITED)
        var adapter: LocalPlayerAdapter? = null
        override suspend fun sendRequest(request: Request): Result<Answer> {
            log += "send ${request.command} on ${Thread.currentThread().name} " +
                "isPlaying=${adapter?.localPlayerData?.value?.player?.isPlaying}"
            sent.send(request)
            return Result.success(Answer(buildJsonObject {}))
        }
    }

    private suspend fun <T : Any> await(read: () -> T?): T = withTimeout(fiveSeconds) {
        var v = read()
        while (v == null) {
            delay(tenMillis)
        v = read()
        }
        v
    }

    private val fiveSeconds = 5_000L
    private val tenMillis = 10L

    private suspend fun once(): String? {
        val player = Player()
        val client = Client(player.log)
        val settings = SettingsRepository(MapSettings(), MapSettings()).apply { setSendspinEnabled(true) }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val tracker = PlayerPositionTracker()
        val prefs = UserPreferences()
        val adapter = LocalPlayerAdapter(
            settings = settings,
            apiClient = client,
            playerRequestFactory = PlayerRequestFactory(tracker, prefs),
            positionTracker = tracker,
            userPreferences = prefs,
            errorBus = ErrorMessageBus(),
            endpoints = LocalPlayerEndpoints(client, settings, scope),
            playerFactory = { _, _, _ -> player },
        )
        player.adapter = adapter
        client.adapter = adapter
        try {
            adapter.onInitialPlayersReceived(hasLocalPlayer = false)
            val synthetic = await { adapter.localPlayerData.value?.player }
            adapter.onServerPlayerUpdate(synthetic.copy(isPlaying = true))
            await { adapter.wantsSession.value.takeIf { it } }
            player.log += "test: playing() done isPlaying=${adapter.localPlayerData.value?.player?.isPlaying}"
            player.emit(PlayerEvent.PlaybackStopped(StopCause.Starved))
            val cmd = client.sent.receive().command
            await { player.held.value.takeIf { it } }
            val playing = adapter.localPlayerData.value?.player?.isPlaying
            val wants = adapter.wantsSession.value
            return if (playing == true || !wants) {
                "cmd=$cmd playing=$playing wants=$wants :: ${player.log.joinToString(
                    " | ",
                )}"
            } else {
                null
            }
        } finally {
            adapter.cancel()
            scope.cancel()
        }
    }

    @Test
    fun stress() = runBlocking {
        val failures = ConcurrentLinkedQueue<String>()
        (1..16).map { async(Dispatchers.IO) { repeat(60) { once()?.let { failures += it } } } }.awaitAll()
        println("STRESS failures=${failures.size}")
        failures.take(six).forEach { println("STRESS $it") }
    }

    private val six = 6
}
