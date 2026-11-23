package io.music_assistant.client.watch.data

/**
 * What the iPhone app said about the server, through WatchConnectivity.
 *
 * The Swift side reads the application context (`PhoneConfigReceiver.swift`) and hands the
 * values to `WatchPlayerController`. The phone writes it in
 * `io.music_assistant.client.watchlink.WatchConnectivityLink`.
 */
internal sealed interface PhoneLink {
    /** Nothing received yet, or the phone app has never sent anything. */
    data object Unknown : PhoneLink

    data class Configured(val config: WatchServerConfig) : PhoneLink

    /** The phone reaches the server through WebRTC only, which the watch cannot use. */
    data object RemoteOnly : PhoneLink

    /** The phone signed out, or the server rejected its token. */
    data object SignedOut : PhoneLink
}
