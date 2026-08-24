package io.music_assistant.client.auth

/**
 * Presents the provider's OAuth page.
 *
 * An interface, not an `expect class`, because the iOS implementation is written in
 * Swift: Kotlin exports an interface as an Obj-C protocol, which a Swift class can
 * conform to, while an `expect class` cannot be implemented outside Kotlin.
 */
interface OAuthHandler {
    /**
     * True when this handler reports cancellation itself.
     *
     * An in-app session (iOS `ASWebAuthenticationSession`) knows when the user
     * dismissed it and calls [AuthenticationManager.cancelOAuthFlow]. A handler that
     * hands the URL to a separate browser task (Android Custom Tabs) does not, so the
     * app has to infer abandonment from the next foreground event instead.
     */
    val reportsCancellation: Boolean

    fun openOAuthUrl(url: String)
}
