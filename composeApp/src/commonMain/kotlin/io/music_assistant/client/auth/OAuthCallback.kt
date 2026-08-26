package io.music_assistant.client.auth

import io.ktor.http.URLParserException
import io.ktor.http.Url

/** Outcome of reading an incoming URL as an OAuth callback. */
sealed interface OAuthCallbackResult {
    /** The provider returned an authorization code. */
    data class Code(val token: String) : OAuthCallbackResult

    /** The callback shape matched but carried a failure, or no code at all. */
    data class Failed(val reason: String) : OAuthCallbackResult

    /** Not an OAuth callback — some other deep link, or not a URL at all. */
    data object NotOAuth : OAuthCallbackResult
}

/**
 * The one place that knows what an OAuth callback URL looks like.
 *
 * Three callers share it: the iOS in-app auth session, the iOS deep-link dispatcher and
 * Android's `MainActivity`. They used to hold three copies of the scheme, which could
 * drift apart silently — a callback scheme that does not match the one the session waits
 * for produces a sheet that hangs until the user gives up.
 */
object OAuthCallback {
    /**
     * Bare scheme, for `ASWebAuthenticationSession`. Note this is NOT interchangeable
     * with [RETURN_URL]: the session matches on the scheme alone and silently never
     * fires if it is given a full URL.
     */
    const val SCHEME = "musicassistant"

    /** Full redirect URL, for the server's `auth/authorization_url` call. */
    const val RETURN_URL = "$SCHEME://$HOST$PATH"

    /**
     * Read [urlString] as an OAuth callback.
     *
     * A URL that matches the shape but carries `?error=` returns [Failed] rather than
     * being dropped — the user is sitting in front of a spinner that only an outcome can
     * clear.
     */
    fun parse(urlString: String): OAuthCallbackResult {
        val url = try {
            Url(urlString)
        } catch (_: URLParserException) {
            return OAuthCallbackResult.NotOAuth
        }
        val isCallback = url.protocol.name == SCHEME &&
            url.host == HOST &&
            url.encodedPath.trimEnd('/') == PATH
        if (!isCallback) return OAuthCallbackResult.NotOAuth

        url.parameters["code"]?.takeIf { it.isNotEmpty() }
            ?.let { return OAuthCallbackResult.Code(it) }

        val error = url.parameters["error_description"]?.takeIf { it.isNotEmpty() }
            ?: url.parameters["error"]?.takeIf { it.isNotEmpty() }
        return OAuthCallbackResult.Failed(error ?: "No token in OAuth callback")
    }
}

private const val HOST = "auth"
private const val PATH = "/callback"
