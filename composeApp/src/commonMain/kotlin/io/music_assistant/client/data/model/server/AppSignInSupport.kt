package io.music_assistant.client.data.model.server

/**
 * From server 2.11, a Remote Access sign-in with a redirect provider can return the browser to
 * the app (`redirect_target` "app"). The app proves the sign-in with a PKCE verifier through
 * `auth/exchange`, so the browser does not have to reach the server.
 */
fun supportsAppSignIn(schemaVersion: Int?): Boolean =
    schemaVersion != null && schemaVersion >= SERVER_2_11_SCHEMA
