package ir.hawraa.quran.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Chrome tokens for the Pejvak glass language (`DESING.md` §3).
 *
 * Everything that is *not* the opaque mushaf paper reads these (or [LocalHawraaTokens]):
 * the header, dock, mini-player, cards, sheets, chips.
 * The reading surface keeps [QuranColors] (`LocalQuranColors`) with its opaque page.
 */
@Immutable
data class GlassColors(
    val canvas: Color,
    val canvasElevated: Color,
    val blooms: List<Color>,
    val glassFill: Color,
    val glassFillStrong: Color,
    val glassFillFallback: Color,
    val glassBorder: Color,
    val glassHighlight: Color,
    val frostBase: Color,
    val frostScrim: Color,
    val frostSheen: Color,
    val frostFallback: Color,
    val shadow: Color,
    val text: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val icon: Color,
    /** Pure white active highlight on dark-teal glass chrome (Pejvak §3). */
    val accent: Color,
    val accentSoft: Color,
    val onAccent: Color,
    /** Chromatic teal accent for tonal highlights & contrast badges on dark-teal glass. */
    val accentTeal: Color,
    val mark: Color,
    val markSoft: Color,
    val danger: Color,
    val track: Color,
    val dark: Boolean,
)

private val LightGlassColors = GlassColors(
    canvas = CanvasBaseLight,
    canvasElevated = CanvasBaseLight,
    blooms = listOf(RampTeal.third, RampBlue.third, RampPurple.third),
    glassFill = GlassFillLight,
    glassFillStrong = GlassFillStrongLight,
    glassFillFallback = GlassFillFallbackLight,
    glassBorder = GlassBorderLight,
    glassHighlight = GlassHighlightLight,
    frostBase = FrostBaseLight,
    frostScrim = FrostScrimLight,
    frostSheen = FrostSheenLight,
    frostFallback = FrostFallbackLight,
    shadow = ShadowLight,
    text = TextLight,
    textSecondary = TextSecondaryLight,
    textTertiary = TextTertiaryLight,
    icon = TextLight,
    accent = Accent,
    accentSoft = GlassFillStrongLight,
    onAccent = OnAccent,
    accentTeal = AccentTealLight,
    mark = MarkDark,
    markSoft = MarkSoftDark,
    danger = DangerLight,
    track = TrackGlassLight,
    dark = false,
)

private val DarkGlassColors = GlassColors(
    canvas = CanvasBaseDark,
    canvasElevated = CanvasBaseDark,
    blooms = listOf(RampTeal.first, RampBlue.first, RampPurple.first),
    glassFill = GlassFillDark,
    glassFillStrong = GlassFillStrongDark,
    glassFillFallback = GlassFillFallbackDark,
    glassBorder = GlassBorderDark,
    glassHighlight = GlassHighlightDark,
    frostBase = FrostBaseDark,
    frostScrim = FrostScrimDark,
    frostSheen = FrostSheenDark,
    frostFallback = FrostFallbackDark,
    shadow = ShadowDark,
    text = TextDark,
    textSecondary = TextSecondaryDark,
    textTertiary = TextTertiaryDark,
    icon = TextDark,
    accent = Accent,
    accentSoft = GlassFillStrongDark,
    onAccent = OnAccent,
    accentTeal = AccentTealDark,
    mark = MarkDark,
    markSoft = MarkSoftDark,
    danger = DangerDark,
    track = TrackGlassDark,
    dark = true,
)

val LocalGlassColors = staticCompositionLocalOf { LightGlassColors }

internal fun glassColorsFor(dark: Boolean): GlassColors =
    if (dark) DarkGlassColors else LightGlassColors
