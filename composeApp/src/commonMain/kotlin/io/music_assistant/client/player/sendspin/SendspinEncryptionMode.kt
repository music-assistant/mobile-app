package io.music_assistant.client.player.sendspin

/** Encryption is required by the user but unsupported by the server; no connection is made. */
class EncryptionRequiredUnavailable :
    Exception("Server does not support encrypted Sendspin connections")

/** Which Sendspin protocol a client connection should run. */
enum class SendspinEncryptionMode {
    /** The pre-encryption cleartext protocol, byte-identical to older servers. */
    LEGACY,

    /** The Noise-encrypted protocol. */
    ENCRYPTED,

    /** Encryption required by the user but unsupported by the server. */
    ENCRYPTED_REQUIRED,
    ;

    companion object {
        /** First MA schema version with complete server-side encrypted-Sendspin support. */
        const val MIN_SCHEMA_VERSION = 45

        /** Pure version gate: >= [MIN_SCHEMA_VERSION] is encrypted, never probed
         *  or downgraded; below/unknown falls back to legacy unless required. */
        fun resolve(schemaVersion: Int?, requireEncryption: Boolean): SendspinEncryptionMode =
            when {
                schemaVersion != null && schemaVersion >= MIN_SCHEMA_VERSION -> ENCRYPTED
                requireEncryption -> ENCRYPTED_REQUIRED
                else -> LEGACY
            }
    }
}
