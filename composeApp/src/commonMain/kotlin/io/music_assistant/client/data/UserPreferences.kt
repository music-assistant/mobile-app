package io.music_assistant.client.data

import io.music_assistant.client.data.model.server.ServerUserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Server-synced user preferences from `auth/me`, shared by every surface.
 * The web frontend owns the toggles; this client refreshes them on connect.
 * An absent field keeps the documented server default.
 */
class UserPreferences {
    private val _preferences = MutableStateFlow(ServerUserPreferences())

    /** Reactive view of the whole preference set. */
    val preferences: StateFlow<ServerUserPreferences> = _preferences

    /** Chapter-based progress and navigation; the server default is on. */
    val chapterProgressEnabled: Flow<Boolean> =
        _preferences.map { it.chapterProgressEnabled }.distinctUntilChanged()

    /** Synchronous read for command-path call sites. */
    val isChapterProgressEnabled: Boolean get() = _preferences.value.chapterProgressEnabled

    /** Caches a fetched set; a failed fetch keeps the current values. */
    fun update(preferences: ServerUserPreferences?) {
        preferences?.let { _preferences.value = it }
    }

    /** Drops cached values so the next server does not inherit them. */
    fun clear() {
        _preferences.value = ServerUserPreferences()
    }
}
