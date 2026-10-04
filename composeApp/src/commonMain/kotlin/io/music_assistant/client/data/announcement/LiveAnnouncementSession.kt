package io.music_assistant.client.data.announcement

import co.touchlab.kermit.Logger
import io.music_assistant.client.utils.myJson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Per-announcement settings; null leaves the player's own setting in charge. */
data class AnnouncementOptions(
    val preAnnounce: Boolean? = null,
    val volumeLevel: Int? = null,
)

sealed interface LiveAnnouncementOutcome {
    data object Finished : LiveAnnouncementOutcome

    /** [reason] is the server's own wording when it gave one. */
    data class Failed(val reason: String?) : LiveAnnouncementOutcome
}

/**
 * Runs one spoken announcement over [link], following the server's `live_announcement`
 * protocol: auth, start, raw s16le mono [frames] once the server has `started`, then stop when
 * [frames] closes. [frames] buffers what was captured before `started`, so nothing said while
 * connecting is lost. A [frames] closed with a cause is a capture failure: no stop is sent.
 *
 * The server buffers the clip and plays it only after stop, and answers `finished` once it has
 * played. So this returns only after playback, and the caller must not cancel it early: a closed
 * link also ends the clip and would announce a partial one. [link] is closed on return.
 */
internal suspend fun runLiveAnnouncement(
    link: AnnouncementLink,
    token: String,
    playerId: String,
    sampleRate: Int,
    options: AnnouncementOptions,
    frames: ReceiveChannel<ByteArray>,
    handshakeTimeout: Duration = HANDSHAKE_TIMEOUT,
): LiveAnnouncementOutcome = coroutineScope {
    val started = CompletableDeferred<Unit>()
    val outcome = CompletableDeferred<LiveAnnouncementOutcome>()

    launch {
        link.inbound.collect { message ->
            when (message) {
                is LinkInbound.Text -> when (message.type()) {
                    "started" -> started.complete(Unit)
                    "finished" -> outcome.complete(LiveAnnouncementOutcome.Finished)
                    "error" -> outcome.complete(LiveAnnouncementOutcome.Failed(message.field("message")))
                }

                is LinkInbound.Closed -> outcome.complete(
                    LiveAnnouncementOutcome.Failed(message.reason?.takeIf { it.isNotBlank() }),
                )
            }
        }
        outcome.complete(LiveAnnouncementOutcome.Failed(null))
    }
    launch {
        try {
            link.sendText(authMessage(token))
            link.sendText(startMessage(playerId, sampleRate, options))
            withTimeoutOrNull(handshakeTimeout) { started.await() } ?: run {
                outcome.complete(LiveAnnouncementOutcome.Failed(null))
                return@launch
            }
            for (frame in frames) link.sendBinary(frame)
            link.sendText(STOP_MESSAGE)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A link that dies mid-send, or the microphone's failure carried in by [frames].
            Logger.withTag("LiveAnnouncement").w(e) { "Announcement stream ended early" }
            outcome.complete(LiveAnnouncementOutcome.Failed(null))
        }
    }

    try {
        outcome.await()
    } finally {
        coroutineContext.cancelChildren()
        withContext(NonCancellable) { runCatching { link.close() } }
    }
}

private fun LinkInbound.Text.field(name: String): String? = runCatching {
    myJson.parseToJsonElement(text).jsonObject[name]?.jsonPrimitive?.contentOrNull
}.getOrNull()

private fun LinkInbound.Text.type(): String? = field("type")

private fun authMessage(token: String) = buildJsonObject {
    put("type", JsonPrimitive("auth"))
    put("token", JsonPrimitive(token))
}.toString()

private fun startMessage(playerId: String, sampleRate: Int, options: AnnouncementOptions) = buildJsonObject {
    put("type", JsonPrimitive("start"))
    put("player_id", JsonPrimitive(playerId))
    put("sample_rate", JsonPrimitive(sampleRate))
    put("channels", JsonPrimitive(1))
    options.preAnnounce?.let { put("pre_announce", JsonPrimitive(it)) }
    options.volumeLevel?.let { put("volume_level", JsonPrimitive(it)) }
}.toString()

private val STOP_MESSAGE = buildJsonObject { put("type", JsonPrimitive("stop")) }.toString()

/** The server waits 10 s for auth and start; allow a little more for it to answer. */
private val HANDSHAKE_TIMEOUT = 15.seconds
