package io.music_assistant.client.api

import io.ktor.http.Url
import io.music_assistant.client.data.model.client.MediaType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A page reachable via a `musicassistant://app/<page>` deep link. */
sealed interface DeepLinkDestination {
    data object Home : DeepLinkDestination

    /** Library tab. [mediaType] null = tab root; set = a category list (`/library/<category>`). */
    data class Library(val mediaType: MediaType? = null) : DeepLinkDestination

    // Future: data class Search(query: String?) — wire query through the
    // existing pendingSearch hoist in MainNavRoot when needed.
    data object Search : DeepLinkDestination

    /** Expand the players (now-playing) layout over the current tab. */
    data object Players : DeepLinkDestination
}

/**
 * App-wide carrier for in-app navigation deep links arriving from outside the
 * Compose tree (Android Intents, iOS `onOpenURL` / Universal Links).
 *
 * Holds the *latest pending* destination in a retained [StateFlow] rather than a
 * one-shot channel. This is deliberate: the consumer
 * ([io.music_assistant.client.ui.compose.home.MainNavigationRoot]) can be
 * composed, torn down, and re-composed during the cold-launch auth churn
 * (Main → Settings → Main as `sessionState` resolves). A one-shot channel gets
 * drained by the first, doomed instance and the rebuilt instance sees nothing.
 * A retained value survives that churn: the consumer applies it only once it is
 * authenticated and then calls [consume] to clear it, so it is honored exactly
 * once by the instance that actually stays on screen.
 */
class DeepLinkBus {
    private val _pending = MutableStateFlow<DeepLinkDestination?>(null)
    val pending: StateFlow<DeepLinkDestination?> = _pending.asStateFlow()

    /**
     * Parses a page deep link and stores it as the pending destination. Accepts
     * both forms:
     *  - custom scheme:    `musicassistant://app/<page>`         (host == "app")
     *  - App/Universal Link: `https://<domain>/app/<page>`       (first path segment == "app")
     *
     * The owning domain is enforced by the OS (Android intent-filter / iOS
     * associated-domains), so we key only on the `app` marker and the page
     * segment — keeping this layer domain-agnostic. Silently ignores anything
     * that isn't a recognized page link (OAuth callbacks, unknown paths,
     * unrelated URLs). Non-suspending; safe to call from any context.
     */
    fun handle(urlString: String) {
        val url = runCatching { Url(urlString) }.getOrNull() ?: return
        val segments = url.encodedPath.split('/').filter { it.isNotEmpty() }
        val route = when {
            url.host == "app" -> segments              // musicassistant://app/<route...>
            segments.firstOrNull() == "app" -> segments.drop(1) // https://<domain>/app/<route...>
            else -> return
        }
        val dest = when (route.firstOrNull() ?: "home") {  // bare ".../app" → home
            "home" -> DeepLinkDestination.Home
            "library" -> {
                // /library → tab root; /library/<category> → that category list.
                // A present-but-unknown category is rejected (whole link ignored)
                // rather than silently landing on the tab root.
                val sub = route.getOrNull(1)
                DeepLinkDestination.Library(
                    mediaType = if (sub == null) null else LIBRARY_ROUTES[sub] ?: return,
                )
            }
            "search" -> DeepLinkDestination.Search
            "players" -> DeepLinkDestination.Players
            else -> return
        }
        _pending.value = dest
    }

    /** Clear [dest] once applied. No-op if a newer link superseded it meanwhile. */
    fun consume(dest: DeepLinkDestination) {
        _pending.compareAndSet(dest, null)
    }

    companion object {
        /**
         * Deep-link contract for `/library/<category>` segments. Intentionally
         * an explicit table (not derived from a UI enum) so this layer stays
         * UI-independent and the public URL names are stable. Plural by
         * convention, matching the in-app category labels.
         */
        private val LIBRARY_ROUTES = mapOf(
            "artists" to MediaType.ARTIST,
            "albums" to MediaType.ALBUM,
            "tracks" to MediaType.TRACK,
            "playlists" to MediaType.PLAYLIST,
            "audiobooks" to MediaType.AUDIOBOOK,
            "podcasts" to MediaType.PODCAST,
            "radios" to MediaType.RADIO,
            "genres" to MediaType.GENRE,
        )
    }
}
