package io.music_assistant.client.feature

import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.music_assistant.client.data.CarConnectionMonitor
import io.music_assistant.client.data.MainDataSource
import io.music_assistant.client.services.SESSION_BLOCK_DEBOUNCE_MS
import io.music_assistant.client.services.SharedMediaSessionManager
import io.music_assistant.client.support.rules.createTestRuleChain
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent.inject

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class AndroidAutoSessionIsolationTest {
    @get:Rule
    val testRuleChain = createTestRuleChain()

    private val dataSource: MainDataSource by inject(MainDataSource::class.java)
    private val managerDispatcher = StandardTestDispatcher()
    private val carConnection = object : CarConnectionMonitor {
        override val connected = MutableStateFlow(false)
    }
    private val managerScope = TestScope(managerDispatcher)
    private lateinit var sharedSession: SharedMediaSessionManager

    private val noOpHandler = object : SharedMediaSessionManager.AutoPlayHandler {
        override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) = Unit
        override fun onPlayFromSearch(query: String?, extras: Bundle?) = Unit
    }

    @Before
    fun setUp() {
        sharedSession = SharedMediaSessionManager(
            ApplicationProvider.getApplicationContext(),
            dataSource,
            carConnection,
            managerScope,
        )
    }

    @After
    fun tearDown() {
        if (::sharedSession.isInitialized) {
            sharedSession.unbindAutoHost()
        }
        managerScope.cancel()
    }

    @Test
    fun `no car host means no isolation and no block`() = runTest(managerDispatcher) {
        runCurrent()
        assertFalse("car host active without a host", sharedSession.autoHostActive.value)
        assertFalse("session blocked without a host", sharedSession.sessionBlocked.value)
    }

    @Test
    fun `binding a car host with no local player blocks the session`() =
        runTest(managerDispatcher) {
            runCurrent()
            sharedSession.bindAutoHost(noOpHandler, isProjectionHost = true)
            assertTrue("projection host not active after bind", sharedSession.autoHostActive.value)

            awaitBlocked(expected = true)

            sharedSession.unbindAutoHost()
            assertFalse("car host active after unbind", sharedSession.autoHostActive.value)
            awaitBlocked(expected = false)
        }

    @Test
    fun `binding a non-projection host never isolates or blocks the session`() =
        runTest(managerDispatcher) {
            runCurrent()
            sharedSession.bindAutoHost(noOpHandler, isProjectionHost = false)
            assertFalse("non-projection host became active", sharedSession.autoHostActive.value)

            awaitBlocked(expected = false)

            sharedSession.unbindAutoHost()
            assertFalse("car host active after unbind", sharedSession.autoHostActive.value)
        }

    private fun awaitBlocked(expected: Boolean) {
        managerScope.testScheduler.advanceTimeBy(SESSION_BLOCK_DEBOUNCE_MS)
        managerScope.testScheduler.runCurrent()
        if (expected) {
            assertTrue("session not blocked", sharedSession.sessionBlocked.value)
        } else {
            assertFalse("session still blocked", sharedSession.sessionBlocked.value)
        }
    }
}
