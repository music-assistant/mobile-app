package io.music_assistant.client.wear.data

import android.content.Context
import android.net.Uri
import co.touchlab.kermit.Logger
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import io.music_assistant.client.watchlink.WatchLinkContract
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/** What the phone app said about the server, through the Wearable Data Layer. */
sealed interface PhoneLink {
    /** Nothing read yet, or the phone app has never sent anything. */
    data object Unknown : PhoneLink

    data class Configured(val config: WearServerConfig) : PhoneLink

    /** The phone reaches the server through WebRTC only, which the watch cannot use. */
    data object RemoteOnly : PhoneLink

    /** The phone signed out, or the server rejected its token. */
    data object SignedOut : PhoneLink
}

/**
 * The server and the token that the phone app put in the Data Layer
 * (`io.music_assistant.client.watchlink.WearDataLayerLink`).
 *
 * The Data Layer keeps the item on the watch, so a watch that starts without its phone still
 * gets the last state. The item is the only copy of the token on the watch.
 */
class PhoneLinkStore(context: Context, scope: CoroutineScope) {
    private val logger = Logger.withTag("WearPhoneLink")
    private val dataClient = Wearable.getDataClient(context.applicationContext)

    private val _link = MutableStateFlow<PhoneLink>(PhoneLink.Unknown)
    val link: StateFlow<PhoneLink> = _link.asStateFlow()

    private val listener = DataClient.OnDataChangedListener { events ->
        events.forEach { event ->
            if (event.dataItem.uri.path != WatchLinkContract.DATA_PATH) return@forEach
            _link.value = when (event.type) {
                DataEvent.TYPE_DELETED -> PhoneLink.SignedOut
                else -> phoneLinkOf(DataMapItem.fromDataItem(event.dataItem).dataMap.toMap())
            }
        }
        events.release()
    }

    init {
        // Lives as long as the process: the application builds the store once.
        dataClient.addListener(listener)
        scope.launch { readStoredItem() }
    }

    private suspend fun readStoredItem() {
        val uri = Uri.Builder().scheme("wear").path(WatchLinkContract.DATA_PATH).build()
        try {
            val items = dataClient.getDataItems(uri).await()
            val stored = items.firstOrNull()?.let { phoneLinkOf(DataMapItem.fromDataItem(it).dataMap.toMap()) }
            items.release()
            // A change event that came in during the read is newer than the read.
            stored?.let { _link.compareAndSet(PhoneLink.Unknown, it) }
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            logger.w(e) { "Could not read the server config from the phone" }
        }
    }

    private fun DataMap.toMap(): Map<String, Any?> = keySet().associateWith { get<Any?>(it) }
}

/** Reads the map that the phone sends (`WatchLinkContract.toMap`). */
internal fun phoneLinkOf(map: Map<String, Any?>): PhoneLink =
    when (map[WatchLinkContract.KEY_STATUS]) {
        WatchLinkContract.STATUS_CONFIGURED -> {
            val host = map[WatchLinkContract.KEY_HOST] as? String
            val port = map[WatchLinkContract.KEY_PORT] as? Int
            val token = map[WatchLinkContract.KEY_TOKEN] as? String
            if (host.isNullOrBlank() || port == null || token.isNullOrBlank()) {
                PhoneLink.Unknown
            } else {
                PhoneLink.Configured(
                    WearServerConfig(
                        host = host,
                        port = port,
                        isTls = map[WatchLinkContract.KEY_TLS] as? Boolean ?: false,
                        basePath = map[WatchLinkContract.KEY_BASE_PATH] as? String ?: "",
                        token = token,
                    ),
                )
            }
        }

        WatchLinkContract.STATUS_REMOTE_ONLY -> PhoneLink.RemoteOnly
        WatchLinkContract.STATUS_SIGNED_OUT -> PhoneLink.SignedOut
        else -> PhoneLink.Unknown
    }
