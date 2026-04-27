package io.music_assistant.client.player.sendspin.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = VersionedRoleSerializer::class)
data class VersionedRole(
    val role: String,
    val version: String,
) {
    val identifier: String
        get() = "$role@$version"

    constructor(stringLiteral: String) : this(
        role = stringLiteral.substringBefore('@', stringLiteral),
        version = stringLiteral.substringAfter('@', "v1"),
    )

    companion object {
        val PLAYER_V1 = VersionedRole("player", "v1")
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VersionedRole) return false
        return role == other.role && version == other.version
    }

    override fun hashCode(): Int {
        return 31 * role.hashCode() + version.hashCode()
    }
}

object VersionedRoleSerializer : KSerializer<VersionedRole> {
    override val descriptor = PrimitiveSerialDescriptor("VersionedRole", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: VersionedRole) {
        encoder.encodeString(value.identifier)
    }

    override fun deserialize(decoder: Decoder): VersionedRole {
        return VersionedRole(stringLiteral = decoder.decodeString())
    }
}
