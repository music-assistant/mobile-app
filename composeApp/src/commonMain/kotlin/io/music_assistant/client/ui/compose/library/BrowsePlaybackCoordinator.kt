package io.music_assistant.client.ui.compose.library

import co.touchlab.kermit.Logger
import io.music_assistant.client.api.Request
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.data.model.client.QueueOption
import io.music_assistant.client.data.model.client.RepeatMode
import io.music_assistant.client.data.model.client.items.Track
import io.music_assistant.client.data.model.client.items.RecommendationFolder
import io.music_assistant.client.data.model.client.items.browsePlaybackUri
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
        queueId: String?,
        option: QueueOption,
        radioMode: Boolean,
        continueIntoFollowingFolders: Boolean,
    ) {
        appendJob?.cancelAndJoin()
        appendJob = null

        queueId?.let { id ->
            apiClient.sendRequest(
                Request.Queue.setRepeatMode(id, RepeatMode.OFF),
            )
            apiClient.sendRequest(
                Request.Queue.setShuffle(id, enabled = false),
            )
            apiClient.sendRequest(
                Request.Queue.setDontStopTheMusic(id, enabled = false),
            )
        }

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
            val continuationUris =
                followingFolders.flatMap { folder ->
                    val tracks = collectFolderTrackUris(folder.path)

                Logger.withTag("BrowsePlayback").i {
                    "COLLECT folder=${folder.name} tracks=${tracks.size}"
                }

                    tracks
                }

            if (continuationUris.isEmpty()) return@launch

            apiClient.sendRequest(
                Request.Library.play(
                    media = continuationUris,
                    queueOrPlayerId = queueOrPlayerId,
                    option = QueueOption.ADD,
                    radioMode = false,
                ),
            ).onFailure { error ->
                Logger.withTag("BrowsePlayback").e(error) {
                    "Failed to append Browse continuation"
                }
            }

            Logger.withTag("BrowsePlayback").i {
                "APPEND continuation tracks=${continuationUris.size}"
            }
        }
    }

    private suspend fun collectFolderTrackUris(path: String, depth: Int = 0): List<String> {
        if (depth > 12) return emptyList()
        val items =
            mediaItemRepository
                .fetchMediaItems(Request.Browse.atPath(path))
                .getOrNull()
                .orEmpty()

        val directTracks =
            items
                .filterIsInstance<Track>()
                .filterNot { track ->
                    val name = track.displayName.trim()
                    name.equals("(Empty)", ignoreCase = true) ||
                        name.equals("Empty", ignoreCase = true)
                }
                .sortedBy { it.displayName.lowercase() }
                .mapNotNull { it.browsePlaybackUri }

        val nestedTracks =
            items
                .filterIsInstance<RecommendationFolder>()
                .filterNot { it.isParentLink }
                .mapNotNull { it.path }
                .distinct()
                .sortedWith(String.CASE_INSENSITIVE_ORDER)
                .flatMap { collectFolderTrackUris(it, depth + 1) }

        return (directTracks + nestedTracks).distinct()
    }
}

internal fun orderedBrowseContinuationFolders(
    currentPath: String,
    folders: List<SettingsRepository.CachedBrowseFolder>,
): List<SettingsRepository.CachedBrowseFolder> {
    val currentRoot = currentPath.topLevelBrowsePath() ?: return emptyList()
    val currentProvider = currentPath.browseProviderRoot()
    val grouped =
        folders
            .filter { it.path.browseProviderRoot() == currentProvider }
            .distinctBy { it.path }
            .mapNotNull { folder -> folder.path.topLevelBrowsePath()?.let { it to folder } }
            .groupBy({ it.first }, { it.second })
            .toSortedMap(String.CASE_INSENSITIVE_ORDER)
    val orderedRoots = grouped.keys.toList()
    val currentIndex = orderedRoots.indexOf(currentRoot)
    if (currentIndex < 0 || orderedRoots.size < 2) return emptyList()

    val followingRoots = orderedRoots.drop(currentIndex + 1) + orderedRoots.take(currentIndex)
    return followingRoots.flatMap { root ->
        grouped.getValue(root).sortedWith(
            compareBy(String.CASE_INSENSITIVE_ORDER) { it.path },
        )
    }
}

private fun String.browseProviderRoot(): String? {
    val schemeIndex = indexOf("://")
    return if (schemeIndex < 0) null else take(schemeIndex + 3)
}

private fun String.topLevelBrowsePath(): String? {
    val normalized = trimEnd('/')
    val schemeIndex = normalized.indexOf("://")
    if (schemeIndex < 0) return normalized.substringBefore('/').takeIf { it.isNotEmpty() }
    val providerRoot = normalized.take(schemeIndex + 3)
    val firstSegment = normalized.drop(schemeIndex + 3).substringBefore('/')
    return firstSegment.takeIf { it.isNotEmpty() }?.let { providerRoot + it }
}

private fun String.parentBrowsePath(): String? {
    val normalized = trimEnd('/')
    val schemeIndex = normalized.indexOf("://")
    if (schemeIndex >= 0) {
        val providerRoot = normalized.take(schemeIndex + 3)
        val relativePath = normalized.drop(schemeIndex + 3)
        val lastSeparator = relativePath.lastIndexOf('/')
        return if (lastSeparator < 0) {
            providerRoot
        } else {
            providerRoot + relativePath.take(lastSeparator)
        }
    }

    val lastSeparator = normalized.lastIndexOf('/')
    return if (lastSeparator < 0) null else normalized.take(lastSeparator)
}
