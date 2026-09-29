package io.music_assistant.client.services

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import io.music_assistant.client.MainActivity

/**
 * The single PendingIntent that returns the user to the app's players screen.
 *
 * Shared by the foreground notification's content intent and the MediaSession's
 * `sessionActivity`. On Android TV there is no notification shade: the home-screen
 * "now playing" card is the only way back to a still-playing app, and it launches
 * the app via `sessionActivity` — so both affordances must resolve to the same
 * deep link, or an exited app becomes unreachable while audio keeps playing.
 */
internal fun openAppPendingIntent(context: Context): PendingIntent {
    val intent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        data = Uri.parse("musicassistant://app/players")
    }
    return PendingIntent.getActivity(
        context,
        0,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
