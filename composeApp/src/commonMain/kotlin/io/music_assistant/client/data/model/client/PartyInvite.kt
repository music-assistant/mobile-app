package io.music_assistant.client.data.model.client

/** What the host can show guests to let them join the party. */
sealed interface PartyInvite {
    /** Guests join at [url]. [partyName] and [qrText] are null when the host left them blank. */
    data class Open(val url: String, val partyName: String?, val qrText: String?) : PartyInvite

    /** The plugin is loaded, but the host has not turned on guest access in its settings. */
    data object GuestAccessOff : PartyInvite
}
