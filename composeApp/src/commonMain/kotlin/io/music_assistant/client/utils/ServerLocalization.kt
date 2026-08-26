package io.music_assistant.client.utils

/** Server schema that introduced localization of API response strings. */
const val SERVER_LOCALIZATION_SCHEMA = 32

/**
 * Return the locale to send during authentication, or null for servers that predate localization.
 * Unknown schemas intentionally retain the legacy payload for compatibility.
 */
fun serverLocalizationLocale(schemaVersion: Int?, locale: String): String? =
    locale.takeIf {
        it.isNotBlank() && schemaVersion != null && schemaVersion >= SERVER_LOCALIZATION_SCHEMA
    }
