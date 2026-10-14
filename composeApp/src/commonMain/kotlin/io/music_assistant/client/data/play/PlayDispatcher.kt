package io.music_assistant.client.data.play

import co.touchlab.kermit.Logger
import io.music_assistant.client.api.Request
import io.music_assistant.client.api.ServiceClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The single sender of `player_queues/play_media`, for every surface (screens, Android Auto,
 * CarPlay).
 *
 * Requests run in the app [scope], not the caller's: the server answers only after it has
 * resolved the media and started playback, which can take tens of seconds, and leaving the
 * screen must not abandon the request.
 */
class PlayDispatcher(
    private val apiClient: ServiceClient,
    private val scope: CoroutineScope,
) {
    private val log = Logger.withTag("PlayDispatch")

    fun play(request: PlayRequest) {
        scope.launch {
            apiClient.sendRequest(
                Request.Library.play(
                    media = request.media,
                    queueOrPlayerId = request.queueOrPlayerId,
                    option = request.option,
                    endlessMixMode = request.endlessMixMode,
                    startItem = request.startItem,
                ),
            ).onFailure { log.w(it) { "play_media failed for $request: ${it.message}" } }
        }
    }
}
