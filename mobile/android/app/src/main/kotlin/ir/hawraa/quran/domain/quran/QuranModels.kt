package ir.hawraa.quran.domain.quran

import ir.hawraa.quran.domain.session.VerseRef

/**
 * Immutable Quran content models. Pure Kotlin — no Android imports — so they can
 * be unit tested and reused. Instances come from the bundled, versioned asset
 * (`assets/quran/pages-v1.json`); sacred text is never fetched or edited at runtime.
 */
data class SurahInfo(
    val number: Int,
    val nameArabic: String,
    val nameFa: String,
    val nameEn: String,
    val ayahCount: Int,
    val revelationPlace: String,
    val bismillahPre: Boolean,
)

/**
 * One translation of one verse, keyed so a verse can carry several at once.
 *
 * The key is the source's own identifier (`ansarian`, …). The reader's selection state is
 * keyed by it, so three translations can be shown and highlighted independently; the
 * bundled asset ships one today, and adding a second is a content change, not a UI one.
 */
data class VerseTranslation(val key: String, val text: String)

data class QuranVerse(
    val key: String,
    val surah: Int,
    val ayah: Int,
    /** Uthmani text exactly as published by the source. Never normalised. */
    val textUthmani: String,
    /** Every bundled translation, in display order. */
    val translations: List<VerseTranslation> = emptyList(),
    val juz: Int,
    val hizb: Int,
    val rubElHizb: Int,
    val sajdah: Int?,
) {
    /** The first translation — for one-line previews (Home), never for the reader's panes. */
    val translationFa: String get() = translations.firstOrNull()?.text.orEmpty()
}

/** Translation keys known to the bundled content. */
object TranslationKeys {
    const val ANSARIAN = "ansarian"
}

data class QuranPage(
    val page: Int,
    val juz: Int,
    val verses: List<QuranVerse>,
)

data class QuranContent(
    val schemaVersion: Int,
    val mushaf: String,
    val basmalaUthmani: String,
    val surahs: Map<Int, SurahInfo>,
    val pages: List<QuranPage>,
) {
    fun surah(number: Int): SurahInfo? = surahs[number]

    fun surahOf(page: QuranPage): SurahInfo? = page.verses.firstOrNull()?.let { surah(it.surah) }
}

/**
 * A surah opens with a basmala line that is *not* a numbered ayah (all surahs but
 * Al-Fatiha, where it is ayah 1, and At-Tawbah, which has none).
 */
fun QuranVerse.showsStandaloneBasmala(surah: SurahInfo?): Boolean =
    ayah == 1 && surah != null && surah.bismillahPre && surah.number != 1 && surah.number != 9

/** This verse as a session-tracking position on [page]. */
fun QuranVerse.toRef(page: Int): VerseRef = VerseRef(surah = surah, ayah = ayah, page = page)
