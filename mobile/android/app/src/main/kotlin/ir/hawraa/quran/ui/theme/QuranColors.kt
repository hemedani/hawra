package ir.hawraa.quran.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import ir.hawraa.quran.domain.settings.DiacriticInk
import ir.hawraa.quran.domain.settings.LetterInk
import ir.hawraa.quran.domain.settings.ReadingSettings

/**
 * Tokens for the **reading surface only** (`DESIGN.md` / Hawraa mushaf contract).
 *
 * Kept apart from the Material scheme and from glass chrome tokens because scripture has
 * requirements chrome does not: long-sitting legibility, and diacritics are the smallest strokes
 * on the screen. [page] is the most opaque surface in the app — ambient blooms never leak
 * behind Arabic diacritics.
 */
@Immutable
data class QuranColors(
    val page: Color,
    val pageVariant: Color,
    val letter: Color,
    val diacritic: Color,
    val translation: Color,
    val hairline: Color,
    val mark: Color,
    val markSoft: Color,
    val accent: Color,
    val accentSoft: Color,
    val onAccent: Color,
    val ornamentRing: Color,
    val ornamentNumber: Color,
    val bannerFill: Color,
    val bannerEdge: Color,
    val bannerText: Color,
    val bannerSubText: Color,
    val selection: Color,
    val selectionEdge: Color,
) {
    val letterMuted: Color get() = letter.copy(alpha = MUTED_LETTER_ALPHA)
    val playing: Color get() = accent.copy(alpha = PLAYING_ALPHA)
    val playingEdge: Color get() = accent
}

const val MUTED_LETTER_ALPHA = 0.34f
const val PLAYING_ALPHA = 0.12f

private val LightQuranColors = QuranColors(
    page = PageLight,
    pageVariant = PageVariantLight,
    letter = LetterInkLight,
    diacritic = MarkInkLight,
    translation = TranslationLight,
    hairline = TrackMushafLight,
    mark = MarkLight,
    markSoft = MarkSoftLight,
    accent = AccentTealLight,
    accentSoft = AccentSoftMushafLight,
    onAccent = OnAccentMushafLight,
    ornamentRing = AccentTealLight.copy(alpha = 0.40f),
    ornamentNumber = TranslationLight,
    bannerFill = AccentSoftMushafLight,
    bannerEdge = AccentTealLight.copy(alpha = 0.35f),
    bannerText = AccentTealLight,
    bannerSubText = TranslationLight,
    selection = MarkSoftLight,
    selectionEdge = MarkLight,
)

private val DarkQuranColors = QuranColors(
    page = PageDark,
    pageVariant = PageVariantDark,
    letter = LetterInkDark,
    diacritic = MarkInkDark,
    translation = TranslationDark,
    hairline = TrackMushafDark,
    mark = MarkDark,
    markSoft = MarkSoftDark,
    accent = AccentTealDark,
    accentSoft = AccentSoftMushafDark,
    onAccent = OnAccentMushafDark,
    ornamentRing = AccentTealDark.copy(alpha = 0.45f),
    ornamentNumber = TranslationDark,
    bannerFill = AccentSoftMushafDark,
    bannerEdge = AccentTealDark.copy(alpha = 0.35f),
    bannerText = AccentTealDark,
    bannerSubText = TranslationDark,
    selection = MarkSoftDark,
    selectionEdge = MarkDark,
)

val LocalQuranColors = staticCompositionLocalOf { LightQuranColors }

internal fun quranColorsFor(dark: Boolean): QuranColors =
    if (dark) DarkQuranColors else LightQuranColors

fun QuranColors.withReadingSettings(settings: ReadingSettings, dark: Boolean): QuranColors =
    copy(
        letter = letterInkColor(settings.letterInk, dark),
        diacritic = diacriticInkColor(settings.diacriticInk, dark),
    )

private fun letterInkColor(ink: LetterInk, dark: Boolean): Color = when (ink) {
    LetterInk.INK -> if (dark) QuranLetterInkDark else QuranLetterInkLight
    LetterInk.GREEN -> if (dark) QuranLetterGreenDark else QuranLetterGreenLight
    LetterInk.NAVY -> if (dark) QuranLetterNavyDark else QuranLetterNavyLight
}

private fun diacriticInkColor(ink: DiacriticInk, dark: Boolean): Color = when (ink) {
    DiacriticInk.GOLD -> if (dark) QuranMarkGoldDark else QuranMarkGoldLight
    DiacriticInk.GREEN -> if (dark) QuranMarkGreenDark else QuranMarkGreenLight
    DiacriticInk.GREY -> if (dark) QuranMarkGreyDark else QuranMarkGreyLight
    DiacriticInk.BURGUNDY -> if (dark) QuranMarkBurgundyDark else QuranMarkBurgundyLight
}
