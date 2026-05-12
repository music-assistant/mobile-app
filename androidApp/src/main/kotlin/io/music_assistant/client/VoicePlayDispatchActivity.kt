package io.music_assistant.client

import android.app.Activity
import android.app.SearchManager
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.session.MediaControllerCompat
import co.touchlab.kermit.Logger
import io.music_assistant.client.services.AndroidAutoPlaybackService

private const val VOICE_PLAY_ACTION = "android.media.action.MEDIA_PLAY_FROM_SEARCH"

/**
 * Headless dispatcher for App Actions / Assistant voice intents.
 *
 * App Actions capabilities can only target Activities (not Services), so this
 * Activity acts as a thin shim that mirrors what the in-car Android Auto
 * framework does: it binds [AndroidAutoPlaybackService] via [MediaBrowserCompat]
 * and sends `transportControls.playFromSearch(query, extras)` on the resulting
 * [MediaControllerCompat]. The bound-service callback handles the rest — same
 * code path as the DHU / in-car AA voice flow, no foreground-service contract
 * to satisfy.
 *
 * Because the theme is `Theme.NoDisplay`, this Activity MUST call `finish()`
 * before `onResume()` returns — otherwise the OS throws `IllegalStateException`
 * (`Activity did not call finish() prior to onResume() completing`). The async
 * `MediaBrowser.connect()` would never satisfy that, so the bind work runs on
 * [VoiceDispatcher] (a process-scoped singleton) and the Activity finishes
 * synchronously.
 */
class VoicePlayDispatchActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val incoming = intent
        // The Activity is exported; reject anything that didn't enter through the
        // documented intent-filter to keep the entry point well-defined.
        if (incoming?.action != VOICE_PLAY_ACTION) {
            Logger.withTag("VoiceDispatch").w {
                "Unexpected intent action=${incoming?.action} — ignoring."
            }
            finish()
            return
        }
        val query = incoming.getStringExtra(SearchManager.QUERY)
            ?: incoming.getStringExtra("query")
        val extras = incoming.extras
        Logger.withTag("VoiceDispatch").i {
            @Suppress("DEPRECATION") // Bundle.get(key) is the only untyped accessor we have for log dumps.
            val extrasDump = extras?.keySet()?.joinToString { k -> "$k=${extras.get(k)}" }
            "Received voice intent action=${incoming.action} query=\"$query\" extras={$extrasDump}"
        }
        VoiceDispatcher.dispatch(applicationContext, query.orEmpty(), extras)
        finish()
    }
}

/**
 * Process-scoped singleton that owns the [MediaBrowserCompat] lifecycle for a
 * voice-dispatch round-trip. Lives in application scope so it survives the
 * dispatch Activity being torn down immediately after [VoicePlayDispatchActivity.onCreate].
 *
 * A new voice command supersedes any in-flight bind — the previous attempt is
 * disconnected before the new one starts. Each in-flight bind also carries an
 * internal timeout so a service that never replies doesn't leak the binding.
 *
 * The connection callbacks capture their own [MediaBrowserCompat] reference
 * via closure (rather than reading the singleton's mutable field), so a rapid
 * second dispatch arriving mid-connection cannot misroute the first attempt's
 * `playFromSearch` onto the second attempt's session.
 */
private object VoiceDispatcher {
    private const val CONNECT_TIMEOUT_MS = 10_000L

    private val mainHandler = Handler(Looper.getMainLooper())
    private var current: InFlight? = null

    private data class InFlight(val browser: MediaBrowserCompat, val timeoutRunnable: Runnable)

    @Synchronized
    fun dispatch(appContext: Context, query: String, extras: Bundle?) {
        // Supersede any in-flight bind.
        cancelCurrent()

        // Forward declare for closures (callback + timeout need a stable handle).
        lateinit var instance: InFlight
        val callback = object : MediaBrowserCompat.ConnectionCallback() {
            override fun onConnected() {
                Logger.withTag("VoiceDispatch").i {
                    "Bound session token acquired — sending playFromSearch(\"$query\")"
                }
                runCatching {
                    MediaControllerCompat(appContext, instance.browser.sessionToken)
                        .transportControls
                        .playFromSearch(query, extras)
                }.onFailure { t ->
                    Logger.withTag("VoiceDispatch").w(t) { "playFromSearch failed" }
                }
                clearIfCurrent(instance)
            }

            override fun onConnectionSuspended() {
                Logger.withTag("VoiceDispatch").w { "Session connection suspended" }
                clearIfCurrent(instance)
            }

            override fun onConnectionFailed() {
                Logger.withTag("VoiceDispatch").w { "Failed to bind AndroidAutoPlaybackService" }
                clearIfCurrent(instance)
            }
        }
        val browser = MediaBrowserCompat(
            appContext,
            ComponentName(appContext, AndroidAutoPlaybackService::class.java),
            callback,
            null,
        )
        val timeout = Runnable {
            Logger.withTag("VoiceDispatch").w {
                "MediaBrowser did not connect within ${CONNECT_TIMEOUT_MS}ms — aborting bind"
            }
            clearIfCurrent(instance)
        }
        instance = InFlight(browser, timeout)
        current = instance
        mainHandler.postDelayed(timeout, CONNECT_TIMEOUT_MS)
        browser.connect()
    }

    @Synchronized
    private fun cancelCurrent() {
        current?.let { c ->
            mainHandler.removeCallbacks(c.timeoutRunnable)
            runCatching { c.browser.disconnect() }
        }
        current = null
    }

    @Synchronized
    private fun clearIfCurrent(instance: InFlight) {
        if (current === instance) {
            mainHandler.removeCallbacks(instance.timeoutRunnable)
            runCatching { instance.browser.disconnect() }
            current = null
        } else {
            // A newer dispatch already superseded this attempt — nothing to do.
            runCatching { instance.browser.disconnect() }
        }
    }
}
