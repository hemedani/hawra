package ir.hawraa.quran.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Chrome tokens for the glass language (`docs/DESIGN.md` §4).
 *
 * Everything that is *not* the mushaf reads these: the dock, the selection action box, cards,
 * sheets, chips, the top bar. The reading surface has its own set ([QuranColors]) and the two are
 * never mixed — see the note on [QuranColors.page].
 */
@Immutable
data class GlassColors(
    /** The field everything floats on. */
    val canvas: Color,
    /** One step up from the canvas, for the layer behind a floating pane. */
    val canvasElevated: Color,
    /** The soft blooms drawn on the canvas. Low frequency, low contrast. */
    val blooms: List<Color>,
    /** A normal floating pane. */
    val glassFill: Color,
    /** Dock, sheets, action box — anything that must read over busy content. */
    val glassFillStrong: Color,
    /** API 29–30 has no blur, so the fill has to carry the pane on its own. */
    val glassFillFallback: Color,
    /** The hairline edge. Dark in light mode, light in dark mode — see [GlassBorderLight]. */
    val glassBorder: Color,
    /** The specular top edge that sells the material. */
    val glassHighlight: Color,
    val shadow: Color,
    val text: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val icon: Color,
    /** "This is the current thing" — current destination, playing ayah, primary action. */
    val accent: Color,
    val accentSoft: Color,
    val onAccent: Color,
    /** "The reader put this here" — selection, ornament. Never interactive. */
    val mark: Color,
    val markSoft: Color,
    val danger: Color,
    /** Progress tracks and in-pane dividers. */
    val track: Color,
    /** True when this set is the dark one — used to gate blur and elevation choices. */
    val dark: Boolean,
)

private val LightGlassColors = GlassColors(
    canvas = CanvasLight,
    canvasElevated = CanvasElevatedLight,
    blooms = listOf(CanvasBloomALight, CanvasBloomBLight, CanvasBloomCLight),
    glassFill = GlassFillLight,
    glassFillStrong = GlassFillStrongLight,
    glassFillFallback = GlassFillFallbackLight,
    glassBorder = GlassBorderLight,
    glassHighlight = GlassHighlightLight,
    shadow = ShadowLight,
    text = TextLight,
    textSecondary = TextSecondaryLight,
    textTertiary = TextTertiaryLight,
    icon = IconLight,
    accent = AccentLight,
    accentSoft = AccentSoftLight,
    onAccent = OnAccentLight,
    mark = MarkLight,
    markSoft = MarkSoftLight,
    danger = DangerLight,
    track = TrackLight,
    dark = false,
)

private val DarkGlassColors = GlassColors(
    canvas = CanvasDark,
    canvasElevated = CanvasElevatedDark,
    blooms = listOf(CanvasBloomADark, CanvasBloomBDark, CanvasBloomCDark),
    glassFill = GlassFillDark,
    glassFillStrong = GlassFillStrongDark,
    glassFillFallback = GlassFillFallbackDark,
    glassBorder = GlassBorderDark,
    glassHighlight = GlassHighlightDark,
    shadow = ShadowDark,
    text = TextDark,
    textSecondary = TextSecondaryDark,
    textTertiary = TextTertiaryDark,
    icon = IconDark,
    accent = AccentDark,
    accentSoft = AccentSoftDark,
    onAccent = OnAccentDark,
    mark = MarkDark,
    markSoft = MarkSoftDark,
    danger = DangerDark,
    track = TrackDark,
    dark = true,
)

val LocalGlassColors = staticCompositionLocalOf { LightGlassColors }

internal fun glassColorsFor(dark: Boolean): GlassColors =
    if (dark) DarkGlassColors else LightGlassColors
