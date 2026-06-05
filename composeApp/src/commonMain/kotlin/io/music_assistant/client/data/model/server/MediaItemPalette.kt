package io.music_assistant.client.data.model.server

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Color palette derived from a MediaItem's artwork. Mirrors the Sendspin color@v1 spec.
 *
 * Each component arrives as a JSON `[r, g, b]` array (the server's tuple[int, int, int]).
 * [RgbColorSerializer] preserves that wire format; a missing, null, or malformed array
 * degrades the affected slot to `null` rather than failing the whole payload.
 */
@Serializable
data class MediaItemPalette(
    @SerialName("background_dark") @Serializable(with = RgbColorSerializer::class) val backgroundDark: RgbColor? = null,
    @SerialName(
        "background_light",
    ) @Serializable(with = RgbColorSerializer::class) val backgroundLight: RgbColor? = null,
    @SerialName("primary") @Serializable(with = RgbColorSerializer::class) val primary: RgbColor? = null,
    @SerialName("accent") @Serializable(with = RgbColorSerializer::class) val accent: RgbColor? = null,
    @SerialName("on_dark") @Serializable(with = RgbColorSerializer::class) val onDark: RgbColor? = null,
    @SerialName("on_light") @Serializable(with = RgbColorSerializer::class) val onLight: RgbColor? = null,
)

/** An 8-bit RGB triple. Serializes to/from a JSON `[r, g, b]` array via [RgbColorSerializer]. */
data class RgbColor(val r: Int, val g: Int, val b: Int)

internal object RgbColorSerializer : KSerializer<RgbColor?> {
    private val delegate = ListSerializer(Int.serializer()).nullable
    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun serialize(encoder: Encoder, value: RgbColor?) =
        encoder.encodeSerializableValue(delegate, value?.let { listOf(it.r, it.g, it.b) })

    override fun deserialize(decoder: Decoder): RgbColor? =
        decoder.decodeSerializableValue(delegate)
            ?.takeIf { it.size == BASE_COLORS_COUNT }
            ?.let { RgbColor(it[0], it[1], it[2]) }

    private const val BASE_COLORS_COUNT = 3
}
