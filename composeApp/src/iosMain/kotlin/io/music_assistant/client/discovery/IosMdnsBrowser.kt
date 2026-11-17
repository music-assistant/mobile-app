package io.music_assistant.client.discovery

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** One browse session, implemented in Swift with `NWBrowser` (`MassServiceBrowser.swift`). */
interface NativeServiceBrowser {
    /** [onChange] gets the TXT record of each service that is currently found. */
    fun start(serviceType: String, onChange: (List<Map<String, String>>) -> Unit)
    fun cancel()
}

/**
 * [MdnsBrowser] over the Swift browser that the app sets at bootstrap. Without one, the flow
 * ends at once and discovery shows nothing.
 */
class IosMdnsBrowser : MdnsBrowser {
    private var factory: (() -> NativeServiceBrowser)? = null

    fun setFactory(factory: () -> NativeServiceBrowser) {
        this.factory = factory
    }

    override fun browse(serviceType: String): Flow<List<MdnsService>> = callbackFlow {
        val browser = factory?.invoke() ?: run {
            close()
            return@callbackFlow
        }
        browser.start(serviceType) { records -> trySend(records.map { MdnsService(txt = it) }) }
        awaitClose { browser.cancel() }
    }
}
