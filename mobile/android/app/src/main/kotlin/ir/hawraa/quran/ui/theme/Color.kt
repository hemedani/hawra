package ir.hawraa.quran.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Pejvak glass palette — the contract from DESIGN.md §3, extended with Hawraa's
 * opaque mushaf tokens (`Page*`, `LetterInk*`, `MarkInk*`) which are never mixed
 * with translucent chrome. All colors live in `ui/theme/`.
 */

// --- Canvas (the field behind the glass; never near-white in light) ---
// Deep mid-tone so white-on-glass clears 4.5:1 against the worst-case bloom
// (the contract in docs/DESIGN.md §11; a ContrastContractTest is planned). Light is a
// brighter field than dark, not white.
val CanvasBaseLight = Color(0xFF33505F)
val CanvasBaseDark = Color(0xFF0A0F16)

// --- Glass fills ---
val GlassFillLight = Color(0x29FFFFFF)        // white 16%
val GlassFillDark = Color(0x14FFFFFF)         // white 8%
val GlassFillStrongLight = Color(0x42FFFFFF)  // white 26%
val GlassFillStrongDark = Color(0x24FFFFFF)   // white 14%
val GlassFillFallbackLight = Color(0x4DFFFFFF)// white 30% (API 29-30)
val GlassFillFallbackDark = Color(0xB8141C26) // dark 72% (API 29-30)

// --- Frost material (shell chrome only: header, dock, mini-player) ---
// The pane must REVEAL the blurred backdrop as a luminous crystalline wash, not an
// opaque grey/teal slab. Keeping base + scrim + sheen around ~48-49% combined opacity
// leaves ~51-52% of the blurred canvas and scrolling artwork glowing through, while
// still meeting the contrast contract in docs/DESIGN.md §11 (a ContrastContractTest is
// planned) against the brightest aurora bloom.
val FrostBaseLight = Color(0x38284452)        // deep teal-slate 22%
val FrostBaseDark = Color(0x3D08121A)         // deep slate 24%
val FrostScrimLight = Color(0x381E4752)       // cool teal 22%
val FrostScrimDark = Color(0x3D0B2830)        // crystalline teal 24%
val FrostSheenLight = Color(0x24E6F9FF)       // ice highlight 14%
val FrostSheenDark = Color(0x1FDFF6FF)        // pale cyan ice 12%
val FrostFallbackLight = Color(0xD133505F)    // canvasBase 82% (API 29-30)
val FrostFallbackDark = Color(0xDB06090F)     // #06090F 86% (API 29-30)

// --- Glass edges ---
// A crisp, bright hairline is what sells "cut glass": light catches the top edge and
// separates the pane from the field.
val GlassBorderLight = Color(0x99FFFFFF)      // white 60%
val GlassBorderDark = Color(0x66FFFFFF)       // white 40%
val GlassHighlightLight = Color(0xBFFFFFFF)   // white 75%
val GlassHighlightDark = Color(0x8CFFFFFF)    // white 55%

// --- Text on glass ---
val TextLight = Color(0xFFFFFFFF)
val TextDark = Color(0xFFF2F6F7)
val TextSecondaryLight = Color(0xBDFFFFFF)    // white 74%
val TextSecondaryDark = Color(0xB8F0F5F6)     // 72%
val TextTertiaryLight = Color(0x85FFFFFF)     // white 52%
val TextTertiaryDark = Color(0x80F0F5F6)      // 50%

// --- Accent & semantic ---
val Accent = Color(0xFFFFFFFF)
val OnAccent = Color(0xFF0E1720)
val AccentTealLight = Color(0xFF0A635C)
val AccentTealDark = Color(0xFF5FD9C6)
val DangerLight = Color(0xFFFFD7DD)
val DangerDark = Color(0xFFFF8296)

// --- Semantic glass helpers (tracks, shadows, mushaf markers) ---
val ShadowLight = Color(0x29000000)
val ShadowDark = Color(0x8C000000)
val TrackGlassLight = Color(0x2EFFFFFF)
val TrackGlassDark = Color(0x33F0F5F6)

// --- Modal scrim behind a glass sheet (DESIGN.md §9.1) ---
val Scrim = Color(0x59000000)                 // black 35%

val MarkLight = Color(0xFFA98436)
val MarkDark = Color(0xFFD9BB78)
val MarkSoftLight = Color(0x24A98436)
val MarkSoftDark = Color(0x2ED9BB78)

val OnAccentMushafLight = Color(0xFFFFFFFF)
val OnAccentMushafDark = Color(0xFF04231F)
val AccentSoftMushafLight = Color(0x1F0A635C)
val AccentSoftMushafDark = Color(0x295FD9C6)
val TrackMushafLight = Color(0x2E41505A)
val TrackMushafDark = Color(0x33B4C4CB)

// --- Aurora ramps (content identity) [deep, mid, light] ---
val RampTeal = Triple(Color(0xFF0A635C), Color(0xFF159A8C), Color(0xFF5FD9C6))
val RampBlue = Triple(Color(0xFF123A6B), Color(0xFF2D6CB5), Color(0xFF7FB6F0))
val RampPurple = Triple(Color(0xFF3A2A6B), Color(0xFF6C4FC1), Color(0xFFB79CF0))
val RampCoral = Triple(Color(0xFF7A2E3A), Color(0xFFC75B6B), Color(0xFFFF9FAE))
val RampAmber = Triple(Color(0xFF7A4A12), Color(0xFFC78A2D), Color(0xFFFFD08A))
val RampGreen = Triple(Color(0xFF1E5A34), Color(0xFF3E9A5F), Color(0xFF8FD9A8))
val RampPink = Triple(Color(0xFF6B2A5A), Color(0xFFB54F9A), Color(0xFFF09FD9))
val RampCyan = Triple(Color(0xFF0E4A5A), Color(0xFF2D8FA8), Color(0xFF7FD9E8))

val AuroraRamps = listOf(
    RampTeal, RampBlue, RampPurple, RampCoral,
    RampAmber, RampGreen, RampPink, RampCyan,
)

// --- The mushaf surface (opaque by design — never mixed with translucent chrome) ---
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

// --- Quran ink presets (reader choice, not theme) ---
val QuranLetterInkLight = LetterInkLight
val QuranLetterGreenLight = Color(0xFF0E5C4E)
val QuranLetterNavyLight = Color(0xFF1B2A4A)
val QuranLetterInkDark = LetterInkDark
val QuranLetterGreenDark = Color(0xFFA9DCC0)
val QuranLetterNavyDark = Color(0xFFB7C8EC)

val QuranMarkGoldLight = MarkInkLight
val QuranMarkGreenLight = Color(0xFF2C7A57)
val QuranMarkGreyLight = Color(0xFF8A8577)
val QuranMarkBurgundyLight = Color(0xFF7B2233)
val QuranMarkGoldDark = MarkInkDark
val QuranMarkGreenDark = Color(0xFF8FD3AE)
val QuranMarkGreyDark = Color(0xFFA9A493)
val QuranMarkBurgundyDark = Color(0xFFE09AAB)
