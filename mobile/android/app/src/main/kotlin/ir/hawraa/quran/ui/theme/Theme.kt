package ir.hawraa.quran.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import ir.hawraa.quran.domain.ThemeMode

/**
 * Resolved Pejvak glass tokens for the active theme. Screens and shell chrome read
 * [LocalHawraaTokens] (or [LocalGlassColors]). Screens that draw the mushaf read
 * [LocalQuranColors].
 */
@Immutable
data class HawraaTokens(
    val canvasBase: Color,
    val glassFill: Color,
    val glassFillStrong: Color,
    val glassFillFallback: Color,
    val glassBorder: Color,
    val glassHighlight: Color,
    val frostBase: Color,
    val frostScrim: Color,
    val frostSheen: Color,
    val frostFallback: Color,
    val text: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val accent: Color,
    val onAccent: Color,
    val accentTeal: Color,
    val danger: Color,
    val scrim: Color,
    val isDark: Boolean,
)

val LocalHawraaTokens = staticCompositionLocalOf { lightTokens() }

private fun lightTokens() = HawraaTokens(
    canvasBase = CanvasBaseLight,
    glassFill = GlassFillLight,
    glassFillStrong = GlassFillStrongLight,
    glassFillFallback = GlassFillFallbackLight,
    glassBorder = GlassBorderLight,
    glassHighlight = GlassHighlightLight,
    frostBase = FrostBaseLight,
    frostScrim = FrostScrimLight,
    frostSheen = FrostSheenLight,
    frostFallback = FrostFallbackLight,
    text = TextLight,
    textSecondary = TextSecondaryLight,
    textTertiary = TextTertiaryLight,
    accent = Accent,
    onAccent = OnAccent,
    accentTeal = AccentTealLight,
    danger = DangerLight,
    scrim = Scrim,
    isDark = false,
)

private fun darkTokens() = HawraaTokens(
    canvasBase = CanvasBaseDark,
    glassFill = GlassFillDark,
    glassFillStrong = GlassFillStrongDark,
    glassFillFallback = GlassFillFallbackDark,
    glassBorder = GlassBorderDark,
    glassHighlight = GlassHighlightDark,
    frostBase = FrostBaseDark,
    frostScrim = FrostScrimDark,
    frostSheen = FrostSheenDark,
    frostFallback = FrostFallbackDark,
    text = TextDark,
    textSecondary = TextSecondaryDark,
    textTertiary = TextTertiaryDark,
    accent = Accent,
    onAccent = OnAccent,
    accentTeal = AccentTealDark,
    danger = DangerDark,
    scrim = Scrim,
    isDark = true,
)

/** True when the resolved theme is dark — the mushaf surface needs this to pick ink presets. */
val LocalHawraaDarkTheme = staticCompositionLocalOf { false }

@Composable
fun HawraaTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val tokens = if (dark) darkTokens() else lightTokens()
    val colorScheme = if (dark) {
        darkColorScheme(
            primary = tokens.accent,
            onPrimary = tokens.onAccent,
            background = tokens.canvasBase,
            surface = tokens.canvasBase,
            onBackground = tokens.text,
            onSurface = tokens.text,
            error = tokens.danger,
        )
    } else {
        lightColorScheme(
            primary = tokens.accentTeal,
            onPrimary = Color.White,
            background = tokens.canvasBase,
            surface = tokens.canvasBase,
            onBackground = tokens.text,
            onSurface = tokens.text,
            error = tokens.danger,
        )
    }

    CompositionLocalProvider(
        LocalHawraaDarkTheme provides dark,
        LocalHawraaTokens provides tokens,
        LocalGlassColors provides glassColorsFor(dark),
        LocalQuranColors provides quranColorsFor(dark),
        LocalHawraaGeometry provides HawraaGeometry(),
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = HawraaTypography,
            content = content,
        )
    }
}
