package io.music_assistant.client.watchlink

import android.content.Context
import co.touchlab.kermit.Logger
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable

/**
 * Puts the [WatchLinkState] in a Wearable Data Layer item that the Wear OS app reads.
 *
 * The Data Layer keeps the item and syncs it when the watch connects, so a watch that is off
 * or out of range gets the last state later. It delivers the item only to an app with the same
 * application id and the same signing key, and it encrypts the link between phone and watch.
 * A phone without Google Play services or without a paired watch fails the task, and nothing
 * else happens.
 */
class WearDataLayerLink(context: Context) : WatchLink {
    private val dataClient = Wearable.getDataClient(context.applicationContext)

    override fun publish(state: WatchLinkState) {
        val request = PutDataMapRequest.create(WatchLinkContract.DATA_PATH).apply {
            WatchLinkContract.toMap(state).forEach { (key, value) ->
                when (value) {
                    is String -> dataMap.putString(key, value)
                    is Int -> dataMap.putInt(key, value)
                    is Boolean -> dataMap.putBoolean(key, value)
                }
            }
        }.asPutDataRequest().setUrgent()
        dataClient.putDataItem(request)
            .addOnFailureListener { log.w(it) { "Could not put the watch config" } }
    }

    private companion object {
        val log = Logger.withTag("WatchLink")
    }
}
