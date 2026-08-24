package io.music_assistant.client.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The single definition of what an OAuth callback URL looks like, shared by the iOS
 * auth session, the iOS deep-link handler and Android's launch intent. A disagreement
 * between those three used to be possible, and shows up as a login that hangs.
 */
class OAuthCallbackTest {
    @Test
    fun `the return url is built from the scheme`() {
        assertTrue(
            OAuthCallback.RETURN_URL.startsWith("${OAuthCallback.SCHEME}://"),
            "The session matches on SCHEME alone; it must be the prefix of the URL the " +
                "server redirects to, or the session waits forever",
        )
        assertEquals("musicassistant://auth/callback", OAuthCallback.RETURN_URL)
    }

    @Test
    fun `a callback carrying a code yields the code`() {
        val result = OAuthCallback.parse("musicassistant://auth/callback?code=abc123")

        assertTrue(result is OAuthCallbackResult.Code)
        assertEquals("abc123", result.token)
    }

    @Test
    fun `extra query parameters do not hide the code`() {
        val result = OAuthCallback.parse("musicassistant://auth/callback?state=xyz&code=abc123")

        assertTrue(result is OAuthCallbackResult.Code)
        assertEquals("abc123", result.token)
    }

    @Test
    fun `a provider error is reported instead of dropped`() {
        val result = OAuthCallback.parse(
            "musicassistant://auth/callback?error=access_denied",
        )

        assertTrue(
            result is OAuthCallbackResult.Failed,
            "Silently ignoring an error callback leaves the user on a spinner nothing can clear",
        )
        assertEquals("access_denied", result.reason)
    }

    @Test
    fun `error_description is preferred over the error code`() {
        val result = OAuthCallback.parse(
            "musicassistant://auth/callback?error=access_denied&error_description=User+said+no",
        )

        assertTrue(result is OAuthCallbackResult.Failed)
        assertEquals("User said no", result.reason)
    }

    @Test
    fun `a callback with neither code nor error is a failure rather than a code`() {
        val result = OAuthCallback.parse("musicassistant://auth/callback")

        assertTrue(result is OAuthCallbackResult.Failed)
    }

    @Test
    fun `an empty code is not a code`() {
        val result = OAuthCallback.parse("musicassistant://auth/callback?code=")

        assertTrue(result is OAuthCallbackResult.Failed)
    }

    @Test
    fun `a page deep link is not an oauth callback`() {
        assertEquals(
            OAuthCallbackResult.NotOAuth,
            OAuthCallback.parse("musicassistant://app/library"),
            "A non-OAuth deep link must fall through to the DeepLinkBus untouched",
        )
    }

    @Test
    fun `a universal link is not an oauth callback`() {
        assertEquals(
            OAuthCallbackResult.NotOAuth,
            OAuthCallback.parse("https://music-assistant.io/app/library"),
        )
    }

    @Test
    fun `a foreign scheme reaching our callback path is not ours`() {
        assertEquals(
            OAuthCallbackResult.NotOAuth,
            OAuthCallback.parse("otherapp://auth/callback?code=abc123"),
        )
    }

    @Test
    fun `garbage does not throw`() {
        assertEquals(OAuthCallbackResult.NotOAuth, OAuthCallback.parse("not a url at all"))
        assertEquals(OAuthCallbackResult.NotOAuth, OAuthCallback.parse(""))
    }
}
