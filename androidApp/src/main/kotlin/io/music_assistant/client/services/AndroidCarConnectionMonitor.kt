package io.music_assistant.client.services

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.car.app.connection.CarConnection
import androidx.lifecycle.Observer
import io.music_assistant.client.data.CarConnectionMonitor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * [CarConnectionMonitor] backed by androidx [CarConnection]. It queries the car host's content
 * provider, so it reports Android Auto projection connect/disconnect independently of our
 * MediaBrowserService's bind/promote/destroy timing (which was too coarse to be a reliable edge).
 *
 * Only `CONNECTION_TYPE_PROJECTION` (phone→car) counts as connected — this is a phone app, so
 * `CONNECTION_TYPE_NATIVE` (Android Automotive OS) must not be mistaken for connecting to a car.
 *
 * App-lifetime singleton: the forever-observer is intentionally never removed.
 */
class AndroidCarConnectionMonitor(context: Context) : CarConnectionMonitor {
    private val _connected = MutableStateFlow(false)
    override val connected = _connected.asStateFlow()

    private val type = CarConnection(context.applicationContext).type
    private val observer = Observer<Int> { connectionType ->
        _connected.value = connectionType == CarConnection.CONNECTION_TYPE_PROJECTION
    }

    init {
        // observeForever must be called on the main thread.
        Handler(Looper.getMainLooper()).post { type.observeForever(observer) }
    }
}
