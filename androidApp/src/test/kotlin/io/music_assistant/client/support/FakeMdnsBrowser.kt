package io.music_assistant.client.support

import io.music_assistant.client.discovery.MdnsBrowser
import io.music_assistant.client.discovery.MdnsService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Stands in for the system mDNS responder; a test sets [services] to what the network advertises. */
class FakeMdnsBrowser : MdnsBrowser {
    val services = MutableStateFlow<List<MdnsService>>(emptyList())

    override fun browse(serviceType: String): Flow<List<MdnsService>> = services
}
