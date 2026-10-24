package io.music_assistant.sendspin.android

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetwork
import org.robolectric.shadows.ShadowNetworkCapabilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ConnectivityOnlineTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val manager = context.getSystemService(ConnectivityManager::class.java)
    private val shadow = shadowOf(manager)

    /** Cancelled on its own or with the test; collects without a scheduler step. */
    private fun TestScope.ownScope() =
        CoroutineScope(Job(backgroundScope.coroutineContext.job) + UnconfinedTestDispatcher(testScheduler))

    private fun activeNetworkCapabilities(vararg capabilities: Int) {
        val network = manager.activeNetwork ?: error("Robolectric has no default active network")
        val networkCapabilities = ShadowNetworkCapabilities.newInstance()
        capabilities.forEach { shadowOf(networkCapabilities).addCapability(it) }
        shadow.setNetworkCapabilities(network, networkCapabilities)
    }

    @Test
    fun anActiveNetworkWithInternetStartsOnline() = runTest {
        activeNetworkCapabilities(NetworkCapabilities.NET_CAPABILITY_INTERNET)

        assertTrue(connectivityOnline(context, ownScope()).value)
    }

    @Test
    fun anActiveNetworkWithoutInternetStartsOffline() = runTest {
        activeNetworkCapabilities(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)

        assertFalse(connectivityOnline(context, ownScope()).value)
    }

    @Test
    fun noActiveNetworkStartsOffline() = runTest {
        shadow.setActiveNetworkInfo(null)

        assertFalse(connectivityOnline(context, ownScope()).value)
    }

    @Test
    fun theDefaultNetworkCallbackDrivesTheValue() = runTest {
        shadow.setActiveNetworkInfo(null)
        val online = connectivityOnline(context, ownScope())
        val callback = shadow.networkCallbacks.single()
        val network: Network = ShadowNetwork.newInstance(1)

        callback.onAvailable(network)
        assertTrue(online.value, "after onAvailable")

        callback.onLost(network)
        assertFalse(online.value, "after onLost")
    }

    @Test
    fun theCallbackIsUnregisteredWhenTheScopeEnds() = runTest {
        val scope = ownScope()
        connectivityOnline(context, scope)
        assertEquals(1, shadow.networkCallbacks.size, "registered while the scope is active")

        scope.cancel()

        assertEquals(emptySet(), shadow.networkCallbacks, "registered after the scope ended")
    }
}
