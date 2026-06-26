@file:Suppress("OPT_IN_USAGE")

package com.sparklaw.platen

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable
data class Profile(
    val id: String,
    val name: String,
    val folderUri: String?,
    val colorMode: ColorMode,
    val quality: Quality,
    @Serializable(with = PageSizeSerializer::class) val pageSize: PageSize,
    val ocrEnabled: Boolean,
    val autoDetect: Boolean
)

@Serializable
enum class ColorMode { BITONAL, GRAYSCALE }

@Serializable
enum class Quality { STANDARD, HIGH }

object PageSizeSerializer : KSerializer<PageSize> {
    override val descriptor =
        PrimitiveSerialDescriptor("PageSize", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: PageSize) {
        encoder.encodeString(value.name)
    }

    override fun deserialize(decoder: Decoder): PageSize {
        return PageSize.valueOf(decoder.decodeString())
    }
}
