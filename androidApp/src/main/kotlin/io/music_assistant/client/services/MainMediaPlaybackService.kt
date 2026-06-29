package io.music_assistant.client.services

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.widget.Toast
import androidx.media.MediaBrowserServiceCompat
import co.touchlab.kermit.Logger
import io.music_assistant.client.data.MainDataSource
import io.music_assistant.client.ui.compose.common.action.PlayerAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.media_toast_playing_players
import org.jetbrains.compose.resources.getString
import org.koin.android.ext.android.inject

class MainMediaPlaybackService : MediaBrowserServiceCompat() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val logger = Logger.withTag("MainMediaPlaybackService")

    private lateinit var mediaNotificationManager: MediaNotificationManager
    private lateinit var audioManager: AudioManager
    private var wifiLock: WifiManager.WifiLock? = null
    private var fullyInitialized = false

    private val dataSource: MainDataSource by inject()
    private val sharedSession: SharedMediaSessionManager by inject()

    // Debounce job to prevent double-pause when multiple BT devices are removed simultaneously
    // (e.g., wireless Android Auto disconnects both TYPE_BLUETOOTH_A2DP and TYPE_BLUETOOTH_SCO)
    private var audioRoutingPauseJob: Job? = null

    // Audio routing callback - pauses playback when external audio output disconnects
    // (Android Auto, Bluetooth headphones, wired headphones, USB audio, etc.)
    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            val hasExternalOutputRemoved = removedDevices?.any { device ->
                device.isSink && when (device.type) {
                    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,      // Bluetooth audio (includes Android Auto)
                    AudioDeviceInfo.TYPE_BLUETOOTH_SCO,       // Bluetooth phone audio
                    AudioDeviceInfo.TYPE_WIRED_HEADPHONES,    // 3.5mm headphones
                    AudioDeviceInfo.TYPE_WIRED_HEADSET,       // 3.5mm headset
                    AudioDeviceInfo.TYPE_USB_DEVICE,          // USB audio
                    AudioDeviceInfo.TYPE_USB_HEADSET,         // USB headset
                    AudioDeviceInfo.TYPE_USB_ACCESSORY,
                    -> true

                    else -> false
                }
            } == true

            if (hasExternalOutputRemoved) {
                val names = removedDevices.filter { it.isSink }.map { it.productName }
                logger.w { "External audio output(s) disconnected: $names" }

                // Debounce: cancel pending job and reschedule, so multiple simultaneous
                // device removals (e.g., BT A2DP + SCO at once) only trigger ONE pause
                audioRoutingPauseJob?.cancel()
                audioRoutingPauseJob = scope.launch {
                    delay(300)
                    dataSource.localPlayer.value
                        ?.takeIf { it.player.isPlaying }
                        ?.let { localPlayer ->
                            dataSource.playerAction(localPlayer, PlayerAction.Pause)
                        }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        val token = sharedSession.acquire()
        sessionToken = token
        mediaNotificationManager = MediaNotificationManager(this, token)
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        try {
            startForeground(
                MediaNotificationManager.NOTIFICATION_ID,
                mediaNotificationManager.createNotification(null),
            )
        } catch (e: IllegalStateException) {
            logger.w(e) { "Cannot start foreground service from background, stopping" }
            stopSelf()
            return
        }

        audioManager.registerAudioDeviceCallback(audioDeviceCallback, null)
        logger.i { "Registered audio device callback for routing change detection" }

        // Repost the notification on every now-playing change. A repost is what refreshes
        // the visible title/artist (MediaStyle does not re-read them live on all surfaces),
        // and it also swaps the largeIcon. The flow is keyed by track + bitmap, so it fires
        // on track changes even when consecutive tracks share artwork. The first (null)
        // emission re-posts the initial icon.
        scope.launch {
            sharedSession.notificationArt.collect { postNotification(it?.bitmap) }
        }
        scope.launch {
            // Block until everything is stopped, then bail
            dataSource.doesAnythingHavePlayableItem.filter { !it }.first()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        // Hold the Wi-Fi radio only while audio is actually playing, not for the whole service
        // lifetime — a paused-in-background player keeps the service (for the notification) but
        // doesn't need the radio awake.
        scope.launch {
            dataSource.isAnythingPlaying.collect { playing ->
                if (playing) acquireWifiLock() else releaseWifiLock()
            }
        }
        dataSource.apiClient.onPlaybackActive()
        registerNotificationDismissReceiver()
        fullyInitialized = true
    }

    @Suppress("DEPRECATION")
    private fun acquireWifiLock() {
        if (wifiLock?.isHeld == true) return
        val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
        wifiLock = wifiManager.createWifiLock(
            WifiManager.WIFI_MODE_FULL_HIGH_PERF,
            "MusicAssistant:Playback",
        ).apply { acquire() }
        logger.i { "Wi-Fi lock acquired for Sendspin streaming" }
    }

    private fun releaseWifiLock() {
        wifiLock?.takeIf { it.isHeld }?.let {
            it.release()
            logger.i { "Wi-Fi lock released" }
        }
        wifiLock = null
    }

    private val notificationDismissReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (dataSource.focusPlayingSessionPlayer()) {
                scope.launch {
                    val msg = getString(Res.string.media_toast_playing_players)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@MainMediaPlaybackService,
                            msg,
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
            } else {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun registerNotificationDismissReceiver() {
        val filter = IntentFilter(ACTION_NOTIFICATION_DISMISSED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(notificationDismissReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(notificationDismissReceiver, filter)
        }
    }

    override fun onGetRoot(p0: String, p1: Int, p2: Bundle?): BrowserRoot? = null

    override fun onLoadChildren(
        parentId: String,
        result: Result<MutableList<MediaBrowserCompat.MediaItem>>,
    ) = Unit

    private fun postNotification(bitmap: Bitmap?) {
        val notification = mediaNotificationManager.createNotification(bitmap)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                MediaNotificationManager.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
        } else {
            startForeground(MediaNotificationManager.NOTIFICATION_ID, notification)
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // If nothing is actively playing, stop this service when the user closes the app from
        // recents. Playing state is left running so background audio continues uninterrupted.
        // onDestroy releases the local audio stack.
        if (!dataSource.isAnythingPlaying.value) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onDestroy() {
        if (fullyInitialized) {
            releaseWifiLock()
            unregisterReceiver(notificationDismissReceiver)
            audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
            logger.i { "Unregistered audio device callback" }
            dataSource.apiClient.onPlaybackInactive()
            // The control surface is gone (no notification): the user is done. Release the local
            // audio stack (AudioTrack/decoder/consumer + Sendspin client) — unless Android Auto is
            // still hosting the local player. onAppClosed() re-checks "nothing playing" and no-ops
            // otherwise; it launches in the app-scoped MainDataSource, surviving this service.
            if (!sharedSession.autoHostActive.value) {
                dataSource.onAppClosed()
            }
        }
        sharedSession.release()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_NOTIFICATION_DISMISSED =
            "io.music_assistant.client.ACTION_NOTIFICATION_DISMISSED"
    }
}
