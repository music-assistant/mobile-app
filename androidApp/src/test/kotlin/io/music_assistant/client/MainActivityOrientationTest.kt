package io.music_assistant.client

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(AndroidJUnit4::class)
class MainActivityOrientationTest {
    @Test
    fun `locks landscape on television regardless of reported width`() {
        val configuration = Configuration().apply {
            uiMode = Configuration.UI_MODE_TYPE_TELEVISION
            smallestScreenWidthDp = MainActivity.COMPACT_DEVICE_WIDTH
        }

        assertEquals(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, resolveOrientationLock(configuration))
    }

    @Test
    fun `locks portrait on compact non-television devices`() {
        val configuration = Configuration().apply {
            uiMode = Configuration.UI_MODE_TYPE_NORMAL
            smallestScreenWidthDp = MainActivity.COMPACT_DEVICE_WIDTH
        }

        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, resolveOrientationLock(configuration))
    }

    @Test
    fun `leaves orientation unlocked on larger non-television devices`() {
        val configuration = Configuration().apply {
            uiMode = Configuration.UI_MODE_TYPE_NORMAL
            smallestScreenWidthDp = MainActivity.COMPACT_DEVICE_WIDTH + 1
        }

        assertNull(resolveOrientationLock(configuration))
    }
}
