package io.music_assistant.client.data.repository

import co.touchlab.kermit.Logger
import io.music_assistant.client.api.Request
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.data.factory.MediaItemFactory
import io.music_assistant.client.data.model.client.items.SoundEffect
import io.music_assistant.client.data.model.server.ServerMediaItem
import io.music_assistant.client.data.model.server.ServerProviderInstance
import io.music_assistant.client.utils.DataConnectionState
import io.music_assistant.client.utils.SessionState
import io.music_assistant.client.utils.resultAs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The ambient sound overlay of a queue: a looping sound effect that the server mixes into the
 * queue stream, so every player on that stream hears it, the local Sendspin player included.
 *
 * Server-only on purpose, like the sleep timer: it is never a `PlayerAction`, so the local
 * player's command path cannot intercept it. There is no optimistic state either, because the
 * server answers with a `queue_updated` event that carries the new overlay.
 *
 * [apply] runs in the app [scope]: the dialog closes on Apply and must not cancel the command.
 */
class QueueOverlayRepository(
    private val apiClient: ServiceClient,
    private val mediaItemFactory: MediaItemFactory,
    private val scope: CoroutineScope,
) {
    private val log = Logger.withTag("QueueOverlay")

    /**
     * True while an available provider offers sound effects. The server auto-creates the
     * `ambient_sounds` provider, so this is false mostly when a user removed or disabled it.
     * Fails closed: a failed provider fetch hides the feature.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val available: StateFlow<Boolean> = apiClient.sessionState
        .map { it.isAuthenticated() }
        .distinctUntilChanged()
        .mapLatest { authenticated -> authenticated && hasSoundEffectProvider() }
        .stateIn(scope, SharingStarted.Eagerly, false)

    /** Every sound effect the user may pick, from all providers that offer them. */
    suspend fun soundEffects(): Result<List<SoundEffect>> =
        apiClient.sendRequest(Request.SoundEffects.all()).mapCatching { answer ->
            val items = answer.resultAs<List<ServerMediaItem>>()
                ?: error("Missing or undecodable sound effect list payload")
            mediaItemFactory.createList(items).filterIsInstance<SoundEffect>()
        }

    /**
     * Turns the overlay of [queueId] off when [sourceUri] is null. The server keeps the last
     * sound and volume then. Otherwise selects [sourceUri] at [volume] and turns it on, in
     * one command, so the server restarts playback once.
     */
    fun apply(queueId: String, sourceUri: String?, volume: Int) {
        val request = sourceUri
            ?.let { Request.Queue.setOverlay(queueId, enabled = true, source = it, volume = volume) }
            ?: Request.Queue.setOverlay(queueId, enabled = false)
        scope.launch {
            apiClient.sendRequest(request)
                .onFailure { log.e(it) { "Failed to apply ambient sound on $queueId" } }
        }
    }

    private suspend fun hasSoundEffectProvider(): Boolean =
        apiClient.sendRequest(Request.Provider.all())
            .resultAs<List<ServerProviderInstance>>()
            ?.any { it.available && SOUND_EFFECTS_FEATURE in it.supportedFeatures } == true

    private fun SessionState.isAuthenticated(): Boolean =
        (this as? SessionState.Connected)?.dataConnectionState is DataConnectionState.Authenticated

    private companion object {
        const val SOUND_EFFECTS_FEATURE = "sound_effects"
    }
}
