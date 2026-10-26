package io.music_assistant.client.data

import co.touchlab.kermit.Logger
import io.music_assistant.client.api.Answer
import io.music_assistant.client.api.Request
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.data.model.server.ServerUserPreferences
import io.music_assistant.client.utils.mapState
import io.music_assistant.client.utils.myJson
import io.music_assistant.client.utils.resultAs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * Server-synced user preferences from `auth/me`, shared by every surface and by the web
 * frontend. An absent field keeps the documented server default.
 *
 * `auth/user/update` replaces the whole object and the server sends no change event, so the
 * raw object is kept and every write is a read-modify-write of one key. One mutex orders the
 * reads and the writes, so a refresh cannot overtake a write. A change from another client
 * between the read and the write is lost; the server offers no way to prevent that.
 */
@OptIn(ExperimentalAtomicApi::class)
class UserPreferences(private val apiClient: ServiceClient) {
    private val mutex = Mutex()

    // Bumped by [clear]: a fetch or a write that started for the previous server is dropped.
    private val generation = AtomicInt(0)

    private val raw = MutableStateFlow(JsonObject(emptyMap()))
    private val _preferences = MutableStateFlow(ServerUserPreferences())

    /** Reactive view of the whole preference set. */
    val preferences: StateFlow<ServerUserPreferences> = _preferences

    /** Chapter-based progress and navigation; the server default is on. */
    val chapterProgressEnabled: Flow<Boolean> =
        _preferences.map { it.chapterProgressEnabled }.distinctUntilChanged()

    /** Synchronous read for command-path call sites. */
    val isChapterProgressEnabled: Boolean get() = _preferences.value.chapterProgressEnabled

    /** Recent search terms, newest first. The web frontend shares the list. */
    val recentSearches: StateFlow<List<String>> = raw.mapState { it[RECENT_SEARCHES].strings() }

    /** The `providers` of a search: "library" and provider ids. Empty searches all. The web shares it. */
    val searchSources: StateFlow<List<String>> = raw.mapState { it[SEARCH_SOURCES].strings() }

    /** Fetches `auth/me`; a failed fetch keeps the current values. */
    suspend fun refresh() {
        val started = generation.load()
        mutex.withLock { fetch()?.let { cache(it, started) } }
    }

    /**
     * Sets [key] to what [change] makes of its current server value. A null result, or the
     * same value, writes nothing. Says whether the server holds the result. A failed fetch
     * writes nothing, because the write would replace the object with what this client has.
     */
    suspend fun update(key: String, change: (JsonElement?) -> JsonElement?): Boolean {
        val started = generation.load()
        return mutex.withLock {
            val current = fetch() ?: return@withLock false.also {
                log.w { "Not saving $key: preferences could not be read" }
            }
            cache(current, started)
            val value = change(current[key])
            if (value == null || value == current[key]) return@withLock true
            val next = JsonObject(current + (key to value))
            // Shown while the write is on its way; a failed write puts the server state back.
            cache(next, started)
            val saved = apiClient.sendRequest(Request.Auth.updatePreferences(next)).preferences()
            cache(saved ?: current, started)
            (saved != null).also { if (!it) log.w { "Server did not save $key" } }
        }
    }

    /** Puts [term] first in [recentSearches], with the rules of the web frontend. */
    suspend fun addRecentSearch(term: String): Boolean =
        update(RECENT_SEARCHES) { current ->
            withRecentSearch(current.strings(), term)
                ?.let { recents -> JsonArray(recents.map(::JsonPrimitive)) }
        }

    suspend fun setSearchSources(sources: List<String>): Boolean =
        update(SEARCH_SOURCES) { JsonArray(sources.map(::JsonPrimitive)) }

    /** Drops cached values so the next server does not inherit them. */
    fun clear() {
        generation.addAndFetch(1)
        raw.value = JsonObject(emptyMap())
        _preferences.value = ServerUserPreferences()
    }

    private suspend fun fetch(): JsonObject? = apiClient.sendRequest(Request.Auth.me()).preferences()

    private fun cache(preferences: JsonObject, started: Int) {
        if (generation.load() != started) return
        raw.value = preferences
        try {
            _preferences.value = myJson.decodeFromJsonElement<ServerUserPreferences>(preferences)
        } catch (e: SerializationException) {
            log.w(e) { "Keeping the typed preferences: the server set does not decode" }
        }
    }

    companion object {
        const val RECENT_SEARCHES = "search.recent"
        const val SEARCH_SOURCES = "search.sources"
        const val MAX_RECENT_SEARCHES = 5
        const val MIN_RECENT_SEARCH_LENGTH = 2

        private val log = Logger.withTag("UserPreferences")

        /**
         * [term], trimmed, first in [recents], without its other spellings in a different case,
         * cut to [MAX_RECENT_SEARCHES]. Null for a term shorter than [MIN_RECENT_SEARCH_LENGTH].
         */
        fun withRecentSearch(recents: List<String>, term: String): List<String>? =
            term.trim().takeIf { it.length >= MIN_RECENT_SEARCH_LENGTH }?.let { trimmed ->
                (listOf(trimmed) + recents.filterNot { it.equals(trimmed, ignoreCase = true) })
                    .take(MAX_RECENT_SEARCHES)
            }

        // A user answer always holds a preference object; a user without one has an empty
        // object. No user (an error answer) is null, so nothing gets written over it.
        private fun Result<Answer>.preferences(): JsonObject? =
            resultAs<JsonObject>()?.let { it["preferences"] as? JsonObject ?: JsonObject(emptyMap()) }

        private fun JsonElement?.strings(): List<String> =
            (this as? JsonArray).orEmpty()
                .mapNotNull { (it as? JsonPrimitive)?.takeIf(JsonPrimitive::isString)?.content }
    }
}
