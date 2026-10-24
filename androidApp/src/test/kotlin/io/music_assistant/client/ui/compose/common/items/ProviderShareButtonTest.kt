package io.music_assistant.client.ui.compose.common.items

import android.content.ContextWrapper
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.core.content.IntentCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.music_assistant.client.data.model.server.ServerShareItem
import io.music_assistant.client.data.model.server.ServerShareLink
import io.music_assistant.client.ui.compose.common.OverflowMenuButton
import io.music_assistant.client.ui.compose.common.OverflowMenuOption
import kotlinx.coroutines.CompletableDeferred
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class ProviderShareButtonTest {
    @get:Rule
    val composeTestRule = createComposeRule()
    private val intents = mutableListOf<Intent>()
    private var requests = 0
    private val uri = mutableStateOf("library://track/42")
    private var hold: CompletableDeferred<Unit>? = null
    private var cancelled = 0

    @Test
    fun `item change cancels loading and discards the old response`() {
        hold = CompletableDeferred()
        menu(ServerShareItem("Song", "Song by Artist", emptyList()))
        composeTestRule.onNodeWithText("More").performClick()
        composeTestRule.onNodeWithText("Loading sharing options…").assertIsNotEnabled()
        composeTestRule.runOnIdle { uri.value = "library://track/43" }
        composeTestRule.onNodeWithText("Share").assertIsDisplayed()
        composeTestRule.runOnIdle {
            assertEquals(1, cancelled)
            assertEquals(2, requests)
        }
        composeTestRule.onNodeWithText("Share").performClick()
        assertPayload("Song by Artist")
    }

    @Test
    fun `share is inside overflow and loads once per opening`() {
        menu(ServerShareItem("Song", null, listOf(link("spotify", "https://example.com/song"))))
        composeTestRule.onNodeWithText("Share").assertDoesNotExist()
        composeTestRule.runOnIdle { assertEquals(0, requests) }
        composeTestRule.onNodeWithText("More").performClick()
        composeTestRule.onNodeWithText("Share").assertIsDisplayed().performClick()
        assertPayload("https://example.com/song")
        composeTestRule.runOnIdle { assertEquals(1, requests) }
        composeTestRule.onNodeWithText("More").performClick()
        composeTestRule.onNodeWithText("Share").assertIsDisplayed()
        composeTestRule.runOnIdle { assertEquals(2, requests) }
    }

    @Test
    fun `provider picker uses friendly names and shares only chosen URL`() {
        menu(
            ServerShareItem(
                "Song",
                null,
                listOf(
                    link("spotify", "https://example.com/spotify"),
                    link("ytmusic", "https://example.com/youtube"),
                ),
            ),
        )
        composeTestRule.onNodeWithText("More").performClick()
        composeTestRule.onNodeWithText("Share").performClick()
        composeTestRule.onNodeWithText("Share Spotify link").assertIsDisplayed()
        composeTestRule.onNodeWithText("Share YouTube Music link").performClick()
        assertPayload("https://example.com/youtube")
    }

    @Test
    @Config(qualifiers = "w640dp-h360dp-land")
    fun `long provider picker can cancel and share last link in landscape with large text`() {
        RuntimeEnvironment.setFontScale(2f)
        val providerNames = (1..20).associate { "provider$it" to "Music service $it" }
        menu(
            ServerShareItem(
                "Song",
                null,
                providerNames.keys.map { link(it, "https://example.com/$it") },
            ),
            providerNames,
        )

        composeTestRule.onNodeWithText("More").performClick()
        composeTestRule.onNodeWithText("Share").performClick()
        val lastProvider = composeTestRule.onNodeWithText("Share Music service 20 link")
        lastProvider.assertIsNotDisplayed()
        composeTestRule.onNodeWithText("Cancel").assertIsDisplayed()
        lastProvider.performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Share").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cancel").assertIsDisplayed().performTouchInput { click() }
        lastProvider.assertDoesNotExist()
        composeTestRule.runOnIdle { assertTrue(intents.isEmpty()) }

        composeTestRule.onNodeWithText("More").performClick()
        composeTestRule.onNodeWithText("Share").performClick()
        lastProvider.performScrollTo().assertIsDisplayed().performTouchInput { click() }
        assertPayload("https://example.com/provider20")
        composeTestRule.onNodeWithText("Cancel").assertDoesNotExist()
    }

    @Test
    fun `local fallback shares only server text`() {
        menu(ServerShareItem("Song", "Song by Artist", emptyList()))
        composeTestRule.onNodeWithText("More").performClick()
        composeTestRule.onNodeWithText("Share").performClick()
        assertPayload("Song by Artist")
    }

    @Test
    fun `failed response does not expose raw item URI`() {
        menu(null)
        composeTestRule.onNodeWithText("More").performClick()
        composeTestRule.onNodeWithText("Sharing unavailable").assertIsDisplayed()
        composeTestRule.onNodeWithText("Share").assertDoesNotExist()
        composeTestRule.runOnIdle { assertTrue(intents.isEmpty()) }
    }

    @Test
    fun `dismiss while loading cancels request without sharing`() {
        hold = CompletableDeferred()
        menu(ServerShareItem("Song", "Song by Artist", emptyList()))
        composeTestRule.onNodeWithText("More").performClick()
        composeTestRule.onNodeWithText("Loading sharing options…").assertIsNotEnabled()
        composeTestRule.onNodeWithText("Other action").performClick()
        composeTestRule.runOnIdle {
            assertEquals(1, cancelled)
            assertTrue(intents.isEmpty())
        }
        composeTestRule.onNodeWithText("Share").assertDoesNotExist()
    }

    @Test
    fun `cancel provider picker does not share`() {
        menu(
            ServerShareItem(
                "Song",
                null,
                listOf(
                    link("spotify", "https://example.com/a"),
                    link("ytmusic", "https://example.com/b"),
                ),
            ),
        )
        composeTestRule.onNodeWithText("More").performClick()
        composeTestRule.onNodeWithText("Share").performClick()
        composeTestRule.onNodeWithText("Cancel").performClick()
        composeTestRule.onNodeWithText("Share Spotify link").assertDoesNotExist()
        composeTestRule.runOnIdle { assertTrue(intents.isEmpty()) }
    }

    private fun menu(
        response: ServerShareItem?,
        providerNames: Map<String, String> = mapOf("spotify" to "Spotify", "ytmusic" to "YouTube Music"),
    ) {
        composeTestRule.setContent {
            val baseContext = LocalContext.current
            val context = remember(baseContext) {
                object : ContextWrapper(baseContext) {
                    override fun startActivity(intent: Intent) { intents.add(intent) }
                }
            }
            CompositionLocalProvider(LocalContext provides context) {
                var active by remember { mutableStateOf(false) }
                val options = shareMenuOptions(uri.value, active, load = { requestedUri ->
                    requests++
                    if (requestedUri == "library://track/42") {
                        try {
                            hold?.await()
                        } finally {
                            if (hold?.isCompleted == false) cancelled++
                        }
                    }
                    response
                }, providerName = { _, domain ->
                    providerNames.getValue(domain)
                })
                OverflowMenuButton(
                    options = options + OverflowMenuOption("Other action", onClick = {}),
                    onExpandedChange = { active = it },
                ) { onClick ->
                    BasicText("More", Modifier.clickable(onClick = onClick))
                }
            }
        }
    }

    private fun assertPayload(expected: String) {
        composeTestRule.runOnIdle {
            val chooser = intents.single()
            assertEquals(Intent.ACTION_CHOOSER, chooser.action)
            val send = assertNotNull(IntentCompat.getParcelableExtra(chooser, Intent.EXTRA_INTENT, Intent::class.java))
            assertEquals(Intent.ACTION_SEND, send.action)
            assertEquals("text/plain", send.type)
            assertEquals(expected, send.getStringExtra(Intent.EXTRA_TEXT))
            assertEquals("Song", send.getStringExtra(Intent.EXTRA_SUBJECT))
        }
    }

    private fun link(domain: String, url: String) = ServerShareLink(domain, "$domain--account", url)
}
