package ir.hawraa.quran.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import ir.hawraa.quran.domain.settings.DiacriticInk
import ir.hawraa.quran.domain.settings.LetterInk
import ir.hawraa.quran.domain.settings.ReadingSettings

/**
 * Tokens for the **reading surface only** (`docs/DESIGN.md` §4.5).
 *
 * Kept apart from the Material scheme and from [GlassColors] because the mushaf has requirements
 * chrome does not: the text must stay legible for long sittings, and the diacritics are the
 * smallest strokes on the screen. So [page] is the most opaque surface in the app — a glass pane
 * that let the ambient canvas through would put blooms behind marks.
 *
 * Names are semantic, not chromatic: there is no "green" and no "gold" here, because the accent is
 * now turquoise and the warm marker is only one of its uses.
 */
@Immutable
data class QuranColors(
    /** The reading pane. Opaque by design. */
    val page: Color,
    /** Inset rows inside the pane (settings preview, history cards). */
    val pageVariant: Color,
    val letter: Color,
    val diacritic: Color,
    val translation: Color,
    /** Hairline rules inside the pane. */
    val hairline: Color,

    /** The warm marker: "the reader put this here". Never interactive. */
    val mark: Color,
    val markSoft: Color,

    /** "This is the current thing": the reciting ayah, and the primary action. */
    val accent: Color,
    val accentSoft: Color,
    /** A glyph knocked out of a filled accent bubble. */
    val onAccent: Color,

    /** The ayah rosette, unfilled. */
    val ornamentRing: Color,
    val ornamentNumber: Color,

    /** The surah plate — a flat tinted pane, no gradient. */
    val bannerFill: Color,
    val bannerEdge: Color,
    val bannerText: Color,
    val bannerSubText: Color,

    /** Wash behind a tapped pane. Warm, so it never reads as "playing". */
    val selection: Color,
    /** The leading bar of a tapped pane. */
    val selectionEdge: Color,
) {
    /**
     * Written-but-not-recited letters (the assimilated lām of `ٱلشَّمْس`, the silent wāw of
     * `أُو۟لَٰٓئِكَ`): the same ink as [letter], at a much lower weight. Derived rather than stored
     * so it always follows the reader's chosen ink.
     */
    val letterMuted: Color get() = letter.copy(alpha = MUTED_LETTER_ALPHA)

    /**
     * Wash behind the ayah the recitation is on. Cool, so "being read to" and "I tapped this"
     * never look the same.
     */
    val playing: Color get() = accent.copy(alpha = PLAYING_ALPHA)

    /** Leading bar of the playing ayah — solid, so it reads at a glance while scrolling. */
    val playingEdge: Color get() = accent
}

/**
 * Weight of a silent letter. Low enough to read as "not pronounced", high enough that the word is
 * still legible as written — the mushaf must not look like it has holes.
 */
const val MUTED_LETTER_ALPHA = 0.34f

/**
 * Weight of the wash behind the ayah being recited. Low enough that the letters keep their
 * contrast — the highlight must not make the very text being recited harder to read.
 */
const val PLAYING_ALPHA = 0.12f

private val LightQuranColors = QuranColors(
    page = PageLight,
    pageVariant = PageVariantLight,
    letter = LetterInkLight,
    diacritic = MarkInkLight,
    translation = TranslationLight,
    hairline = TrackLight,
    mark = MarkLight,
    markSoft = MarkSoftLight,
    accent = AccentLight,
    accentSoft = AccentSoftLight,
    onAccent = OnAccentLight,
    ornamentRing = AccentLight.copy(alpha = 0.40f),
    ornamentNumber = TextSecondaryLight,
    bannerFill = AccentSoftLight,
    bannerEdge = AccentLight.copy(alpha = 0.35f),
    bannerText = AccentLight,
    bannerSubText = TextSecondaryLight,
    selection = MarkSoftLight,
    selectionEdge = MarkLight,
)

private val DarkQuranColors = QuranColors(
    page = PageDark,
    pageVariant = PageVariantDark,
    letter = LetterInkDark,
    diacritic = MarkInkDark,
    translation = TranslationDark,
    hairline = TrackDark,
    mark = MarkDark,
    markSoft = MarkSoftDark,
    accent = AccentDark,
    accentSoft = AccentSoftDark,
    onAccent = OnAccentDark,
    ornamentRing = AccentDark.copy(alpha = 0.45f),
    ornamentNumber = TextSecondaryDark,
    bannerFill = AccentSoftDark,
    bannerEdge = AccentDark.copy(alpha = 0.35f),
    bannerText = AccentDark,
    bannerSubText = TextSecondaryDark,
    selection = MarkSoftDark,
    selectionEdge = MarkDark,
)

val LocalQuranColors = staticCompositionLocalOf { LightQuranColors }

internal fun quranColorsFor(dark: Boolean): QuranColors =
    if (dark) DarkQuranColors else LightQuranColors

/**
 * Applies the user's ink choices on top of the theme defaults. Anything the user has not chosen
 * (or a preset that has no dark variant yet) falls back to the theme.
 */
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
