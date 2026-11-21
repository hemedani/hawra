package ir.hawraa.quran.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import ir.hawraa.quran.domain.ThemeMode

/**
 * The Material scheme is **derived** from the glass tokens rather than being a palette of its own.
 *
 * Anything that reads `MaterialTheme.colorScheme` is chrome by definition, so it must agree with
 * the glass language: `primary` is the accent, `surface` is the glass fill, `background` is the
 * canvas. Screens that need the mushaf read [LocalQuranColors] instead.
 */
private val LightColors = lightColorScheme(
    primary = AccentLight,
    onPrimary = OnAccentLight,
    primaryContainer = AccentSoftLight,
    onPrimaryContainer = AccentLight,
    secondary = MarkLight,
    onSecondary = OnAccentLight,
    secondaryContainer = MarkSoftLight,
    onSecondaryContainer = MarkLight,
    tertiary = AccentLight,
    onTertiary = OnAccentLight,
    tertiaryContainer = AccentSoftLight,
    onTertiaryContainer = AccentLight,
    background = CanvasLight,
    onBackground = TextLight,
    surface = GlassFillStrongLight,
    onSurface = TextLight,
    surfaceVariant = GlassFillLight,
    onSurfaceVariant = TextSecondaryLight,
    surfaceContainer = GlassFillLight,
    surfaceContainerHigh = GlassFillStrongLight,
    outline = GlassBorderLight,
    outlineVariant = TrackLight,
    error = DangerLight,
    onError = OnAccentLight,
)

private val DarkColors = darkColorScheme(
    primary = AccentDark,
    onPrimary = OnAccentDark,
    primaryContainer = AccentSoftDark,
    onPrimaryContainer = AccentDark,
    secondary = MarkDark,
    onSecondary = OnAccentDark,
    secondaryContainer = MarkSoftDark,
    onSecondaryContainer = MarkDark,
    tertiary = AccentDark,
    onTertiary = OnAccentDark,
    tertiaryContainer = AccentSoftDark,
    onTertiaryContainer = AccentDark,
    background = CanvasDark,
    onBackground = TextDark,
    surface = GlassFillStrongDark,
    onSurface = TextDark,
    surfaceVariant = GlassFillDark,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainer = GlassFillDark,
    surfaceContainerHigh = GlassFillStrongDark,
    outline = GlassBorderDark,
    outlineVariant = TrackDark,
    error = DangerDark,
    onError = OnAccentDark,
)

/** True when the resolved theme is dark — the mushaf surface needs this to pick ink presets. */
val LocalHawraaDarkTheme = staticCompositionLocalOf { false }

/**
 * App theme. [themeMode] is the user's persisted choice; SYSTEM follows the device.
 *
 * Provides all three token sets in one place so a screen can never half-adopt the design language:
 * [LocalGlassColors] for chrome, [LocalQuranColors] for the mushaf, [LocalHawraaGeometry] for
 * shape and spacing.
 */
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
    CompositionLocalProvider(
        LocalHawraaDarkTheme provides dark,
        LocalGlassColors provides glassColorsFor(dark),
        LocalQuranColors provides quranColorsFor(dark),
        LocalHawraaGeometry provides HawraaGeometry(),
    ) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = HawraaTypography,
            content = content,
        )
    }
}
