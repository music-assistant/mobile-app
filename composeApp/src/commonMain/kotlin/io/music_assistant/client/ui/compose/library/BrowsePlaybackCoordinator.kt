package io.music_assistant.client.ui.compose.library

import co.touchlab.kermit.Logger
import io.music_assistant.client.api.Request
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.data.model.client.QueueOption
import io.music_assistant.client.data.model.client.items.Track
import io.music_assistant.client.data.repository.MediaItemRepository
import io.music_assistant.client.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch

/**
 * Extends normal Browse playback beyond the folder that supplied the tapped track.
 *
 * The current folder is submitted first so playback starts without waiting for a library-wide
 * crawl. Remaining playable folders are then appended in alphabetical order. A newer Browse
 * playback request always cancels the older append job before changing the queue.
 */
class BrowsePlaybackCoordinator(
    private val apiClient: ServiceClient,
    private val mediaItemRepository: MediaItemRepository,
    private val settings: SettingsRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var appendJob: Job? = null

    suspend fun play(
        currentPath: String?,
        initialUris: List<String>,
        queueOrPlayerId: String,
        option: QueueOption,
        radioMode: Boolean,
        continueIntoFollowingFolders: Boolean,
    ) {
        appendJob?.cancelAndJoin()
        appendJob = null

        apiClient.sendRequest(
            Request.Library.play(
                media = initialUris,
                queueOrPlayerId = queueOrPlayerId,
                option = option,
                radioMode = radioMode,
            ),
        )

        if (!continueIntoFollowingFolders || currentPath == null) return

        val followingFolders =
            orderedBrowseContinuationFolders(
                currentPath = currentPath,
                folders = settings.cachedBrowseFolders.value,
            )

        if (followingFolders.isEmpty()) return

        appendJob = scope.launch {
            followingFolders.forEach { folder ->
                val tracks =
                    mediaItemRepository
                        .fetchMediaItems(Request.Browse.atPath(folder.path))
                        .getOrNull()
                        .orEmpty()
                        .filterIsInstance<Track>()
                        .filterNot { track ->
                            val name = track.displayName.trim()
                            name.equals("(Empty)", ignoreCase = true) ||
                                name.equals("Empty", ignoreCase = true)
                        }
                        .sortedBy { it.displayName.lowercase() }
                        .mapNotNull { it.mediaUri }
                        .distinct()

                if (tracks.isEmpty()) return@forEach

                apiClient.sendRequest(
                    Request.Library.play(
                        media = tracks,
                        queueOrPlayerId = queueOrPlayerId,
                        option = QueueOption.ADD,
                        radioMode = false,
                    ),
                ).onFailure { error ->
                    Logger.withTag("BrowsePlayback").e(error) {
                        "Failed to append folder=${folder.name} path=${folder.path}"
                    }
                }

                Logger.withTag("BrowsePlayback").i {
                    "APPEND folder=${folder.name} tracks=${tracks.size}"
                }
            }
        }
    }
}

internal fun orderedBrowseContinuationFolders(
    currentPath: String,
    folders: List<SettingsRepository.CachedBrowseFolder>,
): List<SettingsRepository.CachedBrowseFolder> {
    val ordered =
        folders
            .distinctBy { it.path }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    val currentIndex = ordered.indexOfFirst { it.path == currentPath }
    if (currentIndex < 0 || ordered.size < 2) return emptyList()

    return ordered.drop(currentIndex + 1) + ordered.take(currentIndex)
}
