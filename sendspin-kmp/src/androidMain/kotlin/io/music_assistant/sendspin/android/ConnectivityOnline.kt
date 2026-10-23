package io.music_assistant.sendspin.android

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.stateIn

/**
 * [io.music_assistant.sendspin.api.SendspinDeps.online] from the default network: true
 * while a default network is up. The network callback stays registered until [scope] ends.
 * Needs the `ACCESS_NETWORK_STATE` permission, which the module manifest declares.
 */
public fun connectivityOnline(context: Context, scope: CoroutineScope): StateFlow<Boolean> {
    val manager = context.applicationContext.getSystemService(ConnectivityManager::class.java)
    return callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(true)
            }

            override fun onLost(network: Network) {
                trySend(false)
            }
        }
        manager.registerDefaultNetworkCallback(callback)
        awaitClose { manager.unregisterNetworkCallback(callback) }
    }.stateIn(scope, SharingStarted.Eagerly, manager.hasInternet())
}

private fun ConnectivityManager.hasInternet(): Boolean =
    activeNetwork?.let(::getNetworkCapabilities)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
