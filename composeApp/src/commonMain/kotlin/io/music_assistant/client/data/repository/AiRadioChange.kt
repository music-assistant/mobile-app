package io.music_assistant.client.data.repository

/**
 * What the client must re-read after the `ai_radio` plugin announces a change. The server's
 * events carry no state on purpose (they reach every client unfiltered), so each value only names
 * the endpoint to refetch.
 */
enum class AiRadioChange {
    /** Refetch `ai_radio/hosts/list`. */
    HOSTS,

    /** Refetch `ai_radio/stations/list`. */
    STATIONS,

    /** Refetch `ai_radio/queue_dj/status`. */
    QUEUE_DJ,

    /** The command session came back; events sent while it was down are lost, so refetch all. */
    RESYNC,
    ;

    companion object {
        /** Maps the plugin's `{"event": name}` payload. Sections are not shown, so they map to null. */
        fun fromEventName(name: String?): AiRadioChange? = when (name) {
            "hosts_updated" -> HOSTS
            "stations_updated" -> STATIONS
            "queue_dj_updated" -> QUEUE_DJ
            else -> null
        }
    }
}
