@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.music_assistant.client.utils

import io.music_assistant.client.player.PlatformContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.Network.nw_path_get_status
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_queue
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.Network.nw_path_status_satisfiable
import platform.Network.nw_path_status_satisfied
import platform.darwin.dispatch_queue_create

actual class NetworkMonitor actual constructor(@Suppress("UNUSED_PARAMETER") platformContext: PlatformContext) {
    private val _isAvailable = MutableStateFlow(true)
    actual val isAvailable: StateFlow<Boolean> = _isAvailable.asStateFlow()

    // A property: a monitor that only a local holds can be released, and then the value freezes.
    private val monitor = nw_path_monitor_create()

    init {
        val queue = dispatch_queue_create("io.music_assistant.NetworkMonitor", null)
        nw_path_monitor_set_update_handler(monitor) { path ->
            // Satisfiable: a connection can bring the path up, for example a VPN on demand.
            val status = nw_path_get_status(path)
            _isAvailable.value = status == nw_path_status_satisfied || status == nw_path_status_satisfiable
        }
        nw_path_monitor_set_queue(monitor, queue)
        nw_path_monitor_start(monitor)
    }
}
