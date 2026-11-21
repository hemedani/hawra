package ir.hawraa.quran.data.media

import ir.hawraa.quran.domain.download.EditionGranularity
import ir.hawraa.quran.domain.media.MediaContentType
import ir.hawraa.quran.domain.media.MediaEdition
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Wire shape for `mediaEdition/gets`, mirroring `back/models/mediaEdition.ts`. Every field has a
 * default so a newer server adding a column does not break an older build (`ignoreUnknownKeys`),
 * and an unknown `contentType` decodes to `null` and is dropped rather than guessed.
 */
@Serializable
data class MediaEditionDto(
    val key: String = "",
    val contentType: String = "",
    val labelFa: String = "",
    val labelEn: String = "",
    val granularity: String = "ayah",
    val urlTemplate: String = "",
    val fileExtension: String = "mp3",
    val language: String? = null,
    val textKey: String? = null,
    val narrator: String? = null,
    val availability: String = "unverified",
    val isDefault: Boolean = false,
)

object MediaCodec {
    val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    /** `null` when the row is unusable: no key, or a content type this build does not know. */
    fun editionOf(dto: MediaEditionDto): MediaEdition? {
        val contentType = MediaContentType.fromWire(dto.contentType) ?: return null
        if (dto.key.isBlank()) return null
        return MediaEdition(
            key = dto.key,
            contentType = contentType,
            labelFa = dto.labelFa,
            labelEn = dto.labelEn,
            granularity = EditionGranularity.fromWire(dto.granularity),
            urlTemplate = dto.urlTemplate,
            fileExtension = dto.fileExtension,
            language = dto.language,
            textKey = dto.textKey,
            narrator = dto.narrator,
            availability = dto.availability,
            isDefault = dto.isDefault,
        )
    }
}
