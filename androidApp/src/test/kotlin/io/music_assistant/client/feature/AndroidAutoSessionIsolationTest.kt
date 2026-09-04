package io.music_assistant.client.feature

import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.music_assistant.client.services.SharedMediaSessionManager
import io.music_assistant.client.support.rules.createTestRuleChain
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent.inject

/**
 * Regression cover for the Android Auto isolation rule: while a car host owns the session
 * and there is no local player, the session must present nothing. Before this, the session
 * kept publishing the canonical all-players now-playing, so the car showed and controlled a
 * remote player.
 */
@RunWith(AndroidJUnit4::class)
class AndroidAutoSessionIsolationTest {
    @get:Rule
    val testRuleChain = createTestRuleChain()

    private val sharedSession: SharedMediaSessionManager by inject(
        SharedMediaSessionManager::class.java,
    )

    private val noOpHandler = object : SharedMediaSessionManager.AutoPlayHandler {
        override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) = Unit
        override fun onPlayFromSearch(query: String?, extras: Bundle?) = Unit
    }

    @Test
    fun `no car host means no isolation and no block`() {
        assertFalse(sharedSession.autoHostActive.value)
        assertFalse(sharedSession.sessionBlocked.value)
    }

    @Test
    fun `binding a car host with no local player blocks the session`() {
        sharedSession.bindAutoHost(noOpHandler, isProjectionHost = true)
        assertTrue(sharedSession.autoHostActive.value)

        awaitBlocked(expected = true)

        sharedSession.unbindAutoHost()
        assertFalse(sharedSession.autoHostActive.value)
        awaitBlocked(expected = false)
    }

    /**
     * A generic media binder — Assistant, Gemini, Wear, or the app's own
     * VoicePlayDispatchActivity — registers a play handler but must never isolate the session.
     * It used to, which deactivated the session and blanked the phone notification for a remote
     * player on every voice attempt.
     */
    @Test
    fun `binding a non-projection host never isolates or blocks the session`() {
        sharedSession.bindAutoHost(noOpHandler, isProjectionHost = false)
        assertFalse(sharedSession.autoHostActive.value)

        awaitBlocked(expected = false)

        sharedSession.unbindAutoHost()
        assertFalse(sharedSession.autoHostActive.value)
    }

    // The blocked flow is debounced to ride out Sendspin bootstrap, so poll rather than
    // read once. The wait is generous enough to stay stable on a loaded CI machine.
    private fun awaitBlocked(expected: Boolean) = runBlocking {
        withTimeout(AWAIT_TIMEOUT_MS) {
            sharedSession.sessionBlocked.first { it == expected }
        }
    }

    private companion object {
        const val AWAIT_TIMEOUT_MS = 10_000L
    }
}
