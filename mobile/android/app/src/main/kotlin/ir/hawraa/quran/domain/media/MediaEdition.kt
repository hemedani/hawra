package ir.hawraa.quran.domain.media

import ir.hawraa.quran.domain.download.EditionGranularity

/**
 * What kind of verse-tied media a `mediaEdition` is — the mobile mirror of the backend enum
 * (`back/models/mediaEdition.ts`). `RECITATION` is accepted for future folding, but tilāwa still
 * comes from the reciter catalogue; this type is how a preset's translation/tafsir step finds the
 * **spoken audio** (ترجمه‌گویا / تفسیرگویا) that narrates it.
 */
enum class MediaContentType(val wire: String) {
    RECITATION("recitation"),
    TRANSLATION("translation"),
    TAFSIR("tafsir"),
    VIDEO("video"),
    IMAGE("image"),
    ;

    companion object {
        fun fromWire(value: String?): MediaContentType? =
            entries.firstOrNull { it.wire == value }
    }
}

/**
 * One server-declared media edition, resolved from `mediaEdition/gets`.
 *
 * The client keeps no URL arithmetic of its own beyond turning the edition's `urlTemplate` into a
 * concrete verse URL — the server owns which edition is per-ayah and how its files are named, the
 * same contract as recitation. A blank/`unverified` edition is never offered.
 */
data class MediaEdition(
    val key: String,
    val contentType: MediaContentType,
    val labelFa: String,
    val labelEn: String,
    val granularity: EditionGranularity,
    val urlTemplate: String,
    val fileExtension: String,
    val language: String?,
    /** The text this media narrates/illustrates, e.g. `fa.ansarian` — matched against a step's key. */
    val textKey: String?,
    val narrator: String?,
    val availability: String,
    val isDefault: Boolean,
) {
    val isVerified: Boolean get() = availability == VERIFIED

    fun displayLabel(persian: Boolean): String =
        if (persian) labelFa.ifBlank { labelEn } else labelEn.ifBlank { labelFa }

    companion object {
        const val VERIFIED = "verified"
    }
}
