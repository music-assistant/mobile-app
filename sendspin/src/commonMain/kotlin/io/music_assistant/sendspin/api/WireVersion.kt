package io.music_assistant.sendspin.api

/**
 * The Sendspin wire dialect spoken on one connection. The app selects it from
 * the Music Assistant schema version, because a 1.0.0-rc1 client cannot
 * connect to an aiosendspin 9.x server and a 9.x client is only tolerated by
 * a 10.x server while its `allow_legacy_clients` setting stays on.
 */
enum class WireVersion {
    /** aiosendspin 9.x, Music Assistant server 2.10. */
    LEGACY_9,

    /** Sendspin spec 1.0.0-rc1, aiosendspin 10.x, Music Assistant server 2.11. */
    SPEC_1_0,
}
