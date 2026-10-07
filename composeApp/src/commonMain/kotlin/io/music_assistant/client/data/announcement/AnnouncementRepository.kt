package io.music_assistant.client.data.announcement

import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.music_assistant.client.api.ErrorMessageBus
import io.music_assistant.client.api.Request
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.utils.DataConnectionState
import io.music_assistant.client.utils.SessionState
import io.music_assistant.client.utils.authenticatedToken
import io.music_assistant.client.utils.resultAs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.announcement_failed
import org.jetbrains.compose.resources.getString

/** What the signed-in server can announce. Voice is further hidden for the phone's own player. */
data class AnnouncementAvailability(val text: Boolean = false, val voice: Boolean = false)

/** One hold-to-talk recording. [finish] ends it and sends the clip. */
class LiveRecording internal constructor() {
    internal val released = CompletableDeferred<Unit>()
    internal val capturingState = MutableStateFlow(true)

    /** False once the microphone stops for any reason: [finish], the length cap, or a failure. */
    val capturing: StateFlow<Boolean> = capturingState.asStateFlow()

    fun finish() {
        released.complete(Unit)
    }
}

/**
 * Typed and spoken announcements. Both run in the app [scope], not the caller's: the server
 * answers only after the announcement has played, and a spoken one would be cut short if its
 * link closed early. A failure reaches the user through [ErrorMessageBus]; the server already
 * puts its own errors for the typed command there.
 */
class AnnouncementRepository(
    private val apiClient: ServiceClient,
    private val httpClient: HttpClient,
    private val microphone: MicrophoneCapture,
    private val errorBus: ErrorMessageBus,
    private val scope: CoroutineScope,
) {
    private val log = Logger.withTag("Announcement")

    @OptIn(ExperimentalCoroutinesApi::class)
    val availability: StateFlow<AnnouncementAvailability> = apiClient.sessionState
        .map { state -> state.authenticatedSchema() }
        .distinctUntilChanged()
        .mapLatest { schema ->
            schema?.let {
                AnnouncementAvailability(
                    // Older servers reject the engine query, and the error would reach the user.
                    text = it >= TEXT_SCHEMA && hasTtsEngine(),
                    voice = it >= VOICE_SCHEMA,
                )
            } ?: AnnouncementAvailability()
        }
        .stateIn(scope, SharingStarted.Eagerly, AnnouncementAvailability())

    /** The player's chime setting; null when the server does not say. */
    suspend fun chimeSetting(playerId: String): Boolean? =
        apiClient.sendRequest(Request.Player.announcementChime(playerId)).resultAs<Boolean>()

    fun type(playerId: String, message: String, options: AnnouncementOptions) {
        scope.launch {
            apiClient.sendRequest(
                Request.Player.playAnnouncement(playerId, message, options.preAnnounce, options.volumeLevel),
            )
        }
    }

    /**
     * Starts recording at once. The link opens once the first frame gives the sample rate, and
     * catches up from the buffer. A recording released before any audio sends nothing.
     */
    fun speak(playerId: String, options: AnnouncementOptions): LiveRecording {
        val recording = LiveRecording()
        scope.launch {
            val outcome = try {
                coroutineScope {
                    val frames = Channel<ByteArray>(Channel.UNLIMITED)
                    val sampleRate = CompletableDeferred<Int?>()
                    val capture = launch { record(recording, frames, sampleRate) }
                    try {
                        sampleRate.await()?.let { stream(playerId, options, it, frames) }
                    } finally {
                        capture.cancel()
                        recording.capturingState.value = false
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.e(e) { "Live announcement failed" }
                LiveAnnouncementOutcome.Failed(null)
            }
            (outcome as? LiveAnnouncementOutcome.Failed)?.let {
                errorBus.emit(it.reason ?: getString(Res.string.announcement_failed))
            }
        }
        return recording
    }

    /**
     * Closes [frames] when the recording ends, or with the cause when the microphone fails.
     * [sampleRate] gets the first frame's rate, null when there was no audio, or the failure.
     */
    private suspend fun record(
        recording: LiveRecording,
        frames: Channel<ByteArray>,
        sampleRate: CompletableDeferred<Int?>,
    ) {
        var capturedMillis = 0L
        try {
            microphone.frames()
                .takeWhile { !recording.released.isCompleted && capturedMillis < MAX_MILLIS }
                .collect {
                    capturedMillis += it.durationMillis
                    sampleRate.complete(it.sampleRate)
                    frames.send(it.bytes)
                }
            frames.close()
            sampleRate.complete(null)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.e(e) { "Microphone failed" }
            frames.close(e)
            sampleRate.completeExceptionally(e)
        } finally {
            recording.capturingState.value = false
        }
    }

    private suspend fun stream(
        playerId: String,
        options: AnnouncementOptions,
        sampleRate: Int,
        frames: Channel<ByteArray>,
    ): LiveAnnouncementOutcome {
        val state = apiClient.sessionState.value
        val token = state.authenticatedToken() ?: return LiveAnnouncementOutcome.Failed(null)
        val link = when (state) {
            is SessionState.Connected.Direct ->
                WebSocketLink.connect(httpClient, state.connectionInfo.liveAnnouncementUrl)

            is SessionState.Connected.WebRTC ->
                apiClient.openWebRTCDataChannel(LIVE_ANNOUNCEMENT_CHANNEL)?.let(::DataChannelLink)

            else -> null
        } ?: return LiveAnnouncementOutcome.Failed(null)
        return runLiveAnnouncement(link, token, playerId, sampleRate, options, frames)
    }

    private suspend fun hasTtsEngine(): Boolean =
        apiClient.sendRequest(Request.Player.ttsEngines()).resultAs<List<JsonObject>>()?.isNotEmpty() == true

    private fun SessionState.authenticatedSchema(): Int? =
        (this as? SessionState.Connected)
            ?.takeIf { it.dataConnectionState is DataConnectionState.Authenticated }
            ?.serverInfo?.schemaVersion

    private companion object {
        const val TEXT_SCHEMA = 46
        const val VOICE_SCHEMA = 48
        const val LIVE_ANNOUNCEMENT_CHANNEL = "live_announcement"

        /** The server stops a clip at 300 s; stay under it so ours ends cleanly. */
        const val MAX_MILLIS = 295_000L
    }
}
