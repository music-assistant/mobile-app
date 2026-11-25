package io.music_assistant.client.wear.data

import android.content.Context
import androidx.core.content.edit

/** The one thing the watch remembers between launches: which player it controls. */
class WearPreferences(context: Context) {
    private val prefs = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    var selectedPlayerId: String?
        get() = prefs.getString(KEY_SELECTED_PLAYER, null)
        set(value) = prefs.edit { putString(KEY_SELECTED_PLAYER, value) }

    private companion object {
        const val NAME = "wear_prefs"
        const val KEY_SELECTED_PLAYER = "selected_player_id"
    }
}
