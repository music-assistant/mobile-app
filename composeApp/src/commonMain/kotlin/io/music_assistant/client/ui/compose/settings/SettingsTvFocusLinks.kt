package io.music_assistant.client.ui.compose.settings

import io.music_assistant.client.ui.compose.common.TvFocusFlow
import io.music_assistant.client.utils.SessionState

// Android TV: Compose's geometric focus search does not reliably move focus between siblings
// on this hardware, so the initial-config form declares explicit directional links per target
// via FocusProperties instead of trusting geometry. Both connection tabs stay in the graph and
// link sideways; UP from the first field lands on the right-hand tab.
fun buildConfigLinks(isDirect: Boolean, hasCrashLog: Boolean): Map<String, TvFocusFlow.Links> =
    if (isDirect) {
        buildMap {
            put("exitApp", TvFocusFlow.Links(down = "tabDirect"))
            put("tabDirect", TvFocusFlow.Links(up = "exitApp", right = "tabWebRTC", down = "host"))
            put("tabWebRTC", TvFocusFlow.Links(up = "exitApp", left = "tabDirect", down = "host"))
            put("host", TvFocusFlow.Links(up = "tabWebRTC", down = "port"))
            put("port", TvFocusFlow.Links(up = "host", down = "tls"))
            put("tls", TvFocusFlow.Links(up = "port", down = "connect"))
            put("connect", TvFocusFlow.Links(up = "tls", down = "history", right = "history"))
            put("history", TvFocusFlow.Links(up = "connect", left = "connect", down = "shareLogs"))
            put("shareLogs", TvFocusFlow.Links(up = "history"))
            if (hasCrashLog) {
                put("shareLogs", TvFocusFlow.Links(up = "history", down = "crashShare"))
                put("crashShare", TvFocusFlow.Links(up = "shareLogs", right = "crashDelete"))
                put("crashDelete", TvFocusFlow.Links(up = "shareLogs", left = "crashShare"))
            }
        }
    } else {
        buildMap {
            put("exitApp", TvFocusFlow.Links(down = "tabDirect"))
            put("tabDirect", TvFocusFlow.Links(up = "exitApp", right = "tabWebRTC", down = "remoteId"))
            put("tabWebRTC", TvFocusFlow.Links(up = "exitApp", left = "tabDirect", down = "remoteId"))
            put("remoteId", TvFocusFlow.Links(up = "tabWebRTC", down = "connect"))
            put("connect", TvFocusFlow.Links(up = "remoteId", down = "history", right = "scanQr"))
            put("scanQr", TvFocusFlow.Links(left = "connect", right = "history", down = "history"))
            put("history", TvFocusFlow.Links(up = "connect", left = "scanQr", down = "shareLogs"))
            put("shareLogs", TvFocusFlow.Links(up = "history"))
            if (hasCrashLog) {
                put("shareLogs", TvFocusFlow.Links(up = "history", down = "crashShare"))
                put("crashShare", TvFocusFlow.Links(up = "shareLogs", right = "crashDelete"))
                put("crashDelete", TvFocusFlow.Links(up = "shareLogs", left = "crashShare"))
            }
        }
    }

// Same explicit-link strategy for the connected screen: the Server/Login/Local-player cards are
// separate composables whose controls the geometric search on this hardware also fails to chain
// (verified: D-pad focus gets stuck on the Codec and Enable rows and can't move up). The chain
// follows the on-screen order and adapts to whichever controls actually exist right now (the
// login form vs. the logged-in row). Local-player fields stay in the chain regardless of
// [SendspinPlayerSettings] enablement: the draft/save/reset flow (SendspinSettingsViewModel)
// keeps them editable even while the player runs.
//
// Hi-res audio, buffer size and the custom-connection fields live in their own dialog
// (SendspinAdvancedDialogTv, opened from the "advanced" tune icon next to the enable switch) and
// own their own focus chain there, not this one: with every local-player field on this one screen,
// the form no longer fits a 1080p TV screen (verified live -- D-pad focus walked off the bottom of
// the visible area with no way back, since TV forms don't scroll), even after collapsing those
// fields behind an inline toggle row. A separate dialog window isn't bound by the rest of the
// form's height, so it's the only one of the two fixes that actually worked.
fun buildAuthLinks(
    sessionState: SessionState,
    isAuthenticated: Boolean,
    hasCrashLog: Boolean,
): Map<String, TvFocusFlow.Links> = when {
    sessionState is SessionState.Connected && !isAuthenticated -> buildMap {
        put("exitApp", TvFocusFlow.Links(down = "disconnect"))
        put("disconnect", TvFocusFlow.Links(up = "exitApp", down = "loginTab"))
        put("loginTab", TvFocusFlow.Links(up = "disconnect", down = "username"))
        put("username", TvFocusFlow.Links(up = "loginTab", down = "password"))
        put("password", TvFocusFlow.Links(up = "username", down = "login", right = "passwordToggle"))
        put("passwordToggle", TvFocusFlow.Links(up = "password", down = "login", left = "password"))
        put("login", TvFocusFlow.Links(up = "password", down = "shareLogs"))
        put("shareLogs", TvFocusFlow.Links(up = "login"))
        if (hasCrashLog) {
            put("shareLogs", TvFocusFlow.Links(up = "login", down = "crashShare"))
            put("crashShare", TvFocusFlow.Links(up = "shareLogs", right = "crashDelete"))
            put("crashDelete", TvFocusFlow.Links(up = "shareLogs", left = "crashShare"))
        }
    }

    sessionState is SessionState.Connected -> buildMap {
        put("disconnect", TvFocusFlow.Links(down = "logout"))
        put("logout", TvFocusFlow.Links(up = "disconnect", down = "playerToggle"))
        put("playerToggle", TvFocusFlow.Links(up = "logout", down = "playerName", left = "advanced"))
        put("advanced", TvFocusFlow.Links(up = "logout", down = "playerName", right = "playerToggle"))
        put("playerName", TvFocusFlow.Links(up = "playerToggle", down = "codec"))
        put("codec", TvFocusFlow.Links(up = "playerName", down = "reset"))
        put("reset", TvFocusFlow.Links(up = "codec", right = "save", down = "shareLogs"))
        put("save", TvFocusFlow.Links(up = "codec", left = "reset", down = "shareLogs"))
        put("shareLogs", TvFocusFlow.Links(up = "reset"))
        if (hasCrashLog) {
            put("shareLogs", TvFocusFlow.Links(up = "reset", down = "crashShare"))
            put("crashShare", TvFocusFlow.Links(up = "shareLogs", right = "crashDelete"))
            put("crashDelete", TvFocusFlow.Links(up = "shareLogs", left = "crashShare"))
        }
    }

    else -> emptyMap()
}
