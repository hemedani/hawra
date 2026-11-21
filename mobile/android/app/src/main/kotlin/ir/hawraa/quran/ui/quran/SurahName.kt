package ir.hawraa.quran.ui.quran

import ir.hawraa.quran.domain.quran.SurahInfo
import java.util.Locale

/**
 * Surah names are content, not UI chrome: they come from the bundled asset in
 * both languages, so adding a language costs no new strings. Falls back to the
 * Latin name for any locale that has no translated name yet.
 */
fun SurahInfo.displayName(languageTag: String = Locale.getDefault().language): String =
    if (languageTag.startsWith("fa")) nameFa else nameEn
