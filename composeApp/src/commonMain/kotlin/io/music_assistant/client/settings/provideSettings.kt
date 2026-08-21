package io.music_assistant.client.settings

import com.russhwolf.settings.Settings

/** General preferences. Safe to include in a platform backup. */
expect fun provideSettings(): Settings

/**
 * Secrets store: access tokens, server addresses, and connection history.
 *
 * This is a separate store because a platform backup excludes by file, not by
 * key. On Android, `backup_rules.xml` and `data_extraction_rules.xml` exclude
 * this file, so the data stays on the device.
 *
 * Put a key here if it identifies the user's server or authenticates to it.
 */
expect fun provideSecretSettings(): Settings
