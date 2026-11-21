package ir.hawraa.quran.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Raw palette values — the contract from `docs/DESIGN.md` §4.
 *
 * Two families live here and they are **not interchangeable**:
 *
 *  - the **glass chrome** family (canvas, glass, text, accent) for everything that is not the
 *    mushaf — the dock, the action box, cards, sheets, chips;
 *  - the **mushaf** family (page, ink, mark, accent, banner) for the reading surface only.
 *
 * Chrome is translucent because it floats; the reading surface is opaque because scripture is
 * read, not decorated. Never paint Quran text on a glass token.
 *
 * No colour literal may appear outside `ui/theme/`.
 */

// ---- Canvas -------------------------------------------------------------------------------
// The field everything floats on. Light is a cool pale grey-blue — deliberately NOT cream (that
// would drag the app back to the mushaf look it is leaving) and not white (glass over white is
// invisible). Dark is near-black with a blue cast, so panes read as glass over deep water.
val CanvasLight = Color(0xFFE4E9EF)
val CanvasElevatedLight = Color(0xFFEDF1F6)
val CanvasBloomALight = Color(0xFFC9DCE6)
val CanvasBloomBLight = Color(0xFFD3DAEC)
val CanvasBloomCLight = Color(0xFFE4DCEC)

val CanvasDark = Color(0xFF060910)
val CanvasElevatedDark = Color(0xFF0E1520)
val CanvasBloomADark = Color(0xFF0D2C33)
val CanvasBloomBDark = Color(0xFF12203A)
val CanvasBloomCDark = Color(0xFF241C33)

// ---- Glass --------------------------------------------------------------------------------
// Note the border direction: in light mode the hairline is DARK (13 %), because a white border on
// a pale canvas disappears; in dark mode it is LIGHT (22 %). That asymmetry is what makes the
// panes read as glass in both themes.
val GlassFillLight = Color(0xC7FFFFFF) // white 78 %
// 98 %, not 92 %: the dock and sheets sit over text that scrolls behind them, and a measured
// screenshot showed the page still 4 % legible through the panel at 96 % — enough to read whole
// Persian words under the dock's icons, which looks like a rendering fault rather than like glass.
// The border, the sheen and the shadow carry the material; the fill only has to occlude.
val GlassFillStrongLight = Color(0xFAFFFFFF)
val GlassFillFallbackLight = Color(0xE0FFFFFF) // white 88 % — API 29–30, no blur
val GlassBorderLight = Color(0x21142027) // #142027 at 13 %
val GlassHighlightLight = Color(0xF2FFFFFF) // white 95 % — the specular top edge
val ShadowLight = Color(0x29142027) // #142027 at 16 %

val GlassFillDark = Color(0xB81C2532) // #1C2532 at 72 %
// Same 98 % as light, and for the same reason: at 94 % the page's translation text measured an
// 8-lum lift behind the dock, which is more visible on a flat dark panel than 4 % is on white.
val GlassFillStrongDark = Color(0xFA1E2836) // #1E2836 at 98 %
val GlassFillFallbackDark = Color(0xCC141D28) // #141D28 at 80 %
val GlassBorderDark = Color(0x38E2EEFF) // #E2EEFF at 22 %
val GlassHighlightDark = Color(0x3DFFFFFF) // white 24 %
val ShadowDark = Color(0x8C000000) // black 55 %

// ---- Text on glass ------------------------------------------------------------------------
val TextLight = Color(0xFF0E1720)
val TextSecondaryLight = Color(0xFF41505A)
val TextTertiaryLight = Color(0xFF5B6A75)
val IconLight = Color(0xFF2C3A43)

val TextDark = Color(0xFFF0F5F4)
val TextSecondaryDark = Color(0xFFB4C4CB)
val TextTertiaryDark = Color(0xFF8B9EA8)
val IconDark = Color(0xFFD6E2E6)

// ---- Accent & semantic --------------------------------------------------------------------
// One accent, one meaning: "this is the current thing". Turquoise (فیروزه) is also the hook the
// future Iranian design layer will hang on.
val AccentLight = Color(0xFF0A635C)
val AccentDark = Color(0xFF5FD9C6)
val OnAccentLight = Color(0xFFFFFFFF)
val OnAccentDark = Color(0xFF04231F)
val AccentSoftLight = Color(0x1F0A635C) // 12 %
val AccentSoftDark = Color(0x295FD9C6) // 16 %

/**
 * The warm marker. Means *the reader put this here* — a selected verse, an ornament rule.
 * It is **never** an interactive colour: if gold starts looking tappable, the accent has lost its
 * monopoly on "current".
 */
val MarkLight = Color(0xFFA98436)
val MarkDark = Color(0xFFD9BB78)
val MarkSoftLight = Color(0x24A98436) // 14 %
val MarkSoftDark = Color(0x2ED9BB78) // 18 %

val DangerLight = Color(0xFFB02F45)
val DangerDark = Color(0xFFFF8296)
val TrackLight = Color(0x2E41505A) // 18 %
val TrackDark = Color(0x33B4C4CB) // 20 %

// ---- The mushaf surface --------------------------------------------------------------------
// The most opaque surface in the app. A reading pane that let the canvas through would put blooms
// behind diacritics, and diacritics are the smallest strokes on the screen.
val PageLight = Color(0xFFFDFDFE)
val PageVariantLight = Color(0xFFF4F7F9)
val LetterInkLight = Color(0xFF16202A)
val MarkInkLight = Color(0xFF8A6A28)
val TranslationLight = Color(0xFF41505A)

val PageDark = Color(0xFF101720)
val PageVariantDark = Color(0xFF16222C)
val LetterInkDark = Color(0xFFEDE9E0)
val MarkInkDark = Color(0xFFD9BB78)
val TranslationDark = Color(0xFFA8B4BE)

// ---- Quran ink presets (reader choice, not theme) -------------------------------------------
// Letters
val QuranLetterInkLight = LetterInkLight
val QuranLetterGreenLight = Color(0xFF0E5C4E)
val QuranLetterNavyLight = Color(0xFF1B2A4A)
val QuranLetterInkDark = LetterInkDark
val QuranLetterGreenDark = Color(0xFFA9DCC0)
val QuranLetterNavyDark = Color(0xFFB7C8EC)

// Diacritics
val QuranMarkGoldLight = MarkInkLight
val QuranMarkGreenLight = Color(0xFF2C7A57)
val QuranMarkGreyLight = Color(0xFF8A8577)
val QuranMarkBurgundyLight = Color(0xFF7B2233)
val QuranMarkGoldDark = MarkInkDark
val QuranMarkGreenDark = Color(0xFF8FD3AE)
val QuranMarkGreyDark = Color(0xFFA9A493)
val QuranMarkBurgundyDark = Color(0xFFE09AAB)
