@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package io.music_assistant.client.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import io.music_assistant.client.player.PlatformContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

actual class NetworkMonitor actual constructor(platformContext: PlatformContext) {
    private val _isAvailable = MutableStateFlow(true)
    actual val isAvailable: StateFlow<Boolean> = _isAvailable.asStateFlow()

    init {
        val cm = platformContext.applicationContext
            .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        // Sync initial state
        val activeNetwork = cm.activeNetwork
        _isAvailable.value = activeNetwork != null &&
            cm.getNetworkCapabilities(activeNetwork)
                ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

        cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                _isAvailable.value = true
            }

            override fun onLost(network: Network) {
                _isAvailable.value = false
            }
        })
    }
}
