package ir.hawraa.quran.data.quran

import kotlinx.serialization.Serializable

/**
 * Wire shape of `assets/quran/recitations-v1.json`.
 *
 * Hand-authored (unlike the Quran pages, there is nothing to compile here), and
 * verified by `tools/verify-recitation-audio.py` before it ships: a template that
 * 404s is a silent failure on the device, so it must fail here instead.
 */
@Serializable
internal data class RecitationCatalogDto(
    val schemaVersion: Int,
    val defaultReciter: String,
    val reciters: List<ReciterDto>,
)

@Serializable
internal data class ReciterDto(
    val key: String,
    val nameFa: String = "",
    val nameEn: String = "",
    val style: String = "murattal",
    val bitrate: Int = 128,
    /**
     * `ayah` or `juz`. Absent means per-ayah, which is every reciter that shipped before the
     * juz-granularity support — an older asset must keep loading unchanged.
     */
    val granularity: String = "ayah",
    /**
     * Absolute (`https://…`), or server-relative (`/uploads/…`). The relative form is how a
     * reciter whose only source is *this* server is described, so the asset does not have to
     * carry an address that changes with the network.
     */
    val urlTemplate: String,
)
