package io.music_assistant.client.api

object Defaults {
    const val URI = "homeassistant.local"
    const val PORT = 8095
    const val TLS_PORT = 443

    /** The Home Assistant web port, which users enter by mistake for a Music Assistant add-on. */
    const val HOME_ASSISTANT_PORT = 8123

    fun port(isTls: Boolean): Int = if (isTls) TLS_PORT else PORT
}
