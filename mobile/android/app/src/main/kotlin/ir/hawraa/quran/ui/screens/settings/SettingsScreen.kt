package ir.hawraa.quran.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.hawraa.quran.R
import ir.hawraa.quran.domain.AppLanguage
import ir.hawraa.quran.domain.ThemeMode
import ir.hawraa.quran.domain.quran.toPersianDigits
import ir.hawraa.quran.domain.recitation.Reciter
import ir.hawraa.quran.domain.settings.DiacriticInk
import ir.hawraa.quran.domain.settings.LetterInk
import ir.hawraa.quran.domain.settings.ReadingSettings
import ir.hawraa.quran.domain.settings.RecitationSettings
import ir.hawraa.quran.ui.appLocale
import ir.hawraa.quran.ui.components.BubbleButton
import ir.hawraa.quran.ui.components.ChevronIcon
import ir.hawraa.quran.ui.components.GlassChip
import ir.hawraa.quran.ui.components.GlassHeader
import ir.hawraa.quran.ui.components.GlassSegments
import ir.hawraa.quran.ui.components.GlassSurface
import ir.hawraa.quran.ui.components.ICON_GLYPHS
import ir.hawraa.quran.ui.components.LocalShellInsets
import ir.hawraa.quran.ui.components.ScreenFrame
import ir.hawraa.quran.ui.quran.QuranText
import ir.hawraa.quran.ui.quran.quranLineHeight
import ir.hawraa.quran.ui.theme.LocalHawraaDarkTheme
import ir.hawraa.quran.ui.theme.LocalHawraaGeometry
import ir.hawraa.quran.ui.theme.LocalHawraaTokens
import ir.hawraa.quran.ui.theme.LocalQuranColors
import ir.hawraa.quran.ui.theme.OceanCascade
import ir.hawraa.quran.ui.theme.oceanEntrance
import ir.hawraa.quran.ui.theme.withReadingSettings
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onDownloads: () -> Unit = {},
    onPresets: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val reading by viewModel.readingSettings.collectAsStateWithLifecycle()
    val previewText by viewModel.previewText.collectAsStateWithLifecycle()
    val language by viewModel.appLanguage.collectAsStateWithLifecycle()
    val recitation by viewModel.recitation.collectAsStateWithLifecycle()
    val reciters by viewModel.reciters.collectAsStateWithLifecycle()

    SettingsContent(
        themeMode = themeMode,
        language = language,
        reading = reading,
        recitation = recitation,
        reciters = reciters,
        previewText = previewText,
        onBack = onBack,
        onLanguage = viewModel::setAppLanguage,
        onThemeMode = viewModel::setThemeMode,
        onFontSize = viewModel::setFontSize,
        onShowTranslation = viewModel::setShowTranslation,
        onLetterInk = viewModel::setLetterInk,
        onDiacriticInk = viewModel::setDiacriticInk,
        onReciter = viewModel::setReciter,
        onDownloads = onDownloads,
        onPresets = onPresets,
    )
}

/** Stateless settings body, so it can be rendered in tests without Hilt. */
@Composable
internal fun SettingsContent(
    themeMode: ThemeMode,
    language: AppLanguage,
    reading: ReadingSettings,
    previewText: String,
    recitation: RecitationSettings = RecitationSettings(),
    reciters: List<Reciter> = emptyList(),
    onBack: () -> Unit,
    onLanguage: (AppLanguage) -> Unit,
    onThemeMode: (ThemeMode) -> Unit,
    onFontSize: (Int) -> Unit,
    onShowTranslation: (Boolean) -> Unit,
    onLetterInk: (LetterInk) -> Unit,
    onDiacriticInk: (DiacriticInk) -> Unit,
    onReciter: (String?) -> Unit = {},
    onDownloads: () -> Unit = {},
    onPresets: () -> Unit = {},
) {
    val geometry = LocalHawraaGeometry.current
    val tokens = LocalHawraaTokens.current
    val shellInsets = LocalShellInsets.current

    ScreenFrame(
        header = { headerModifier ->
            GlassHeader(
                modifier = headerModifier,
                title = stringResource(R.string.settings_title),
                userTitle = true,
                leading = {
                    BubbleButton(
                        icon = ICON_GLYPHS.getValue("back"),
                        contentDescription = stringResource(R.string.back),
                        onClick = onBack,
                        size = geometry.controls.md,
                    )
                },
            )
        },
    ) { topInset ->
        OceanCascade {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(
                        start = geometry.spacing.xl,
                        end = geometry.spacing.xl,
                        top = topInset,
                        bottom = shellInsets.bottom,
                    ),
            ) {
                SettingsSection(
                    title = stringResource(R.string.settings_theme),
                    modifier = Modifier.oceanEntrance(0),
                ) {
                    GlassSegments(
                        options = ThemeMode.entries,
                        selected = themeMode,
                        onSelect = onThemeMode,
                        label = { mode ->
                            when (mode) {
                                ThemeMode.LIGHT -> stringResource(R.string.settings_theme_light)
                                ThemeMode.DARK -> stringResource(R.string.settings_theme_dark)
                                ThemeMode.SYSTEM -> stringResource(R.string.settings_theme_system)
                            }
                        },
                    )
                }

                Spacer(Modifier.height(geometry.spacing.md))

                SettingsSection(
                    title = stringResource(R.string.settings_language),
                    modifier = Modifier.oceanEntrance(1),
                ) {
                    GlassSegments(
                        options = AppLanguage.entries,
                        selected = language,
                        onSelect = onLanguage,
                        label = { option ->
                            when (option) {
                                AppLanguage.SYSTEM -> stringResource(R.string.language_system)
                                AppLanguage.PERSIAN -> stringResource(R.string.language_persian)
                                AppLanguage.ENGLISH -> stringResource(R.string.language_english)
                            }
                        },
                    )
                }

                Spacer(Modifier.height(geometry.spacing.md))

                SettingsSection(
                    title = stringResource(R.string.settings_reading),
                    modifier = Modifier.oceanEntrance(2),
                ) {
                    SettingLabel(
                        title = stringResource(R.string.settings_font_size),
                        value = reading.fontSizeSp.toPersianDigits(),
                    )
                    Slider(
                        value = reading.fontSizeSp.toFloat(),
                        onValueChange = { value ->
                            val step = ReadingSettings.FONT_SIZE_STEP_SP
                            onFontSize((value / step).roundToInt() * step)
                        },
                        valueRange = ReadingSettings.MIN_FONT_SIZE_SP.toFloat()..
                            ReadingSettings.MAX_FONT_SIZE_SP.toFloat(),
                        steps = (ReadingSettings.MAX_FONT_SIZE_SP - ReadingSettings.MIN_FONT_SIZE_SP) /
                            ReadingSettings.FONT_SIZE_STEP_SP - 1,
                        colors = SliderDefaults.colors(
                            thumbColor = tokens.accent,
                            activeTrackColor = tokens.accent,
                            inactiveTrackColor = tokens.glassBorder,
                            activeTickColor = tokens.onAccent,
                            inactiveTickColor = tokens.textTertiary,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    ReadingPreview(text = previewText, settings = reading)

                    Spacer(Modifier.height(geometry.spacing.lg))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = stringResource(R.string.settings_show_translation),
                            style = MaterialTheme.typography.bodyLarge,
                            color = tokens.text,
                        )
                        Switch(
                            checked = reading.showTranslation,
                            onCheckedChange = onShowTranslation,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = tokens.onAccent,
                                checkedTrackColor = tokens.accent,
                                uncheckedThumbColor = tokens.textTertiary,
                                uncheckedTrackColor = tokens.glassFill,
                                uncheckedBorderColor = tokens.glassBorder,
                            ),
                        )
                    }

                    Spacer(Modifier.height(geometry.spacing.lg))
                    SettingLabel(title = stringResource(R.string.settings_letter_color))
                    GlassSegments(
                        options = LetterInk.entries,
                        selected = reading.letterInk,
                        onSelect = onLetterInk,
                        label = { ink ->
                            when (ink) {
                                LetterInk.INK -> stringResource(R.string.ink_ink)
                                LetterInk.GREEN -> stringResource(R.string.ink_green)
                                LetterInk.NAVY -> stringResource(R.string.ink_navy)
                            }
                        },
                    )

                    Spacer(Modifier.height(geometry.spacing.lg))
                    SettingLabel(title = stringResource(R.string.settings_diacritic_color))
                    GlassSegments(
                        options = DiacriticInk.entries,
                        selected = reading.diacriticInk,
                        onSelect = onDiacriticInk,
                        label = { ink ->
                            when (ink) {
                                DiacriticInk.GOLD -> stringResource(R.string.mark_gold)
                                DiacriticInk.GREEN -> stringResource(R.string.mark_green)
                                DiacriticInk.GREY -> stringResource(R.string.mark_grey)
                                DiacriticInk.BURGUNDY -> stringResource(R.string.mark_burgundy)
                            }
                        },
                    )
                }

                if (reciters.isNotEmpty()) {
                    Spacer(Modifier.height(geometry.spacing.md))
                    SettingsSection(
                        title = stringResource(R.string.settings_recitation),
                        modifier = Modifier.oceanEntrance(3),
                    ) {
                        SettingLabel(title = stringResource(R.string.recitation_reciter))
                        ReciterChips(
                            reciters = reciters,
                            selectedKey = recitation.reciterKey,
                            persian = LocalConfiguration.current.appLocale().language == "fa",
                            onSelect = onReciter,
                        )
                    }
                }

                Spacer(Modifier.height(geometry.spacing.md))

                SettingsSection(
                    title = stringResource(R.string.settings_presets),
                    modifier = Modifier.oceanEntrance(4),
                ) {
                    LinkRow(
                        hint = stringResource(R.string.settings_presets_hint),
                        tag = "settings_presets",
                        onClick = onPresets,
                    )
                }

                Spacer(Modifier.height(geometry.spacing.md))

                SettingsSection(
                    title = stringResource(R.string.settings_downloads),
                    modifier = Modifier.oceanEntrance(5),
                ) {
                    LinkRow(
                        hint = stringResource(R.string.settings_downloads_hint),
                        tag = "settings_downloads",
                        onClick = onDownloads,
                    )
                }

                Spacer(Modifier.height(geometry.spacing.xl))

                // The ayah marker is Delapouite's CC BY icon; the licence requires the
                // attribution to be visible in the product, not only in a text file.
                Text(
                    text = stringResource(R.string.settings_credits),
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.textTertiary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(R.string.recitation_credit),
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.textTertiary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(geometry.spacing.xl))
            }
        }
    }
}

/**
 * One grouped settings card. The title names the group, not the card: everything that belongs
 * together sits on one pane, so a reader can scan by section instead of by control type.
 */
@Composable
private fun SettingsSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val geometry = LocalHawraaGeometry.current
    val tokens = LocalHawraaTokens.current
    GlassSurface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(geometry.radius.card),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = geometry.spacing.lg, vertical = geometry.spacing.lg),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = tokens.text,
            )
            Spacer(Modifier.height(geometry.spacing.md))
            content()
        }
    }
}

/**
 * A row in Settings that leads somewhere rather than changing something.
 *
 * Presets and downloads each have their own screen because they involve a list, a picker and a
 * queue — more than a settings row can honestly express.
 */
@Composable
private fun LinkRow(hint: String, tag: String, onClick: () -> Unit) {
    val geometry = LocalHawraaGeometry.current
    val tokens = LocalHawraaTokens.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val shape = RoundedCornerShape(geometry.radius.md)
    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(onClick = onClick)
            .testTag(tag),
        shape = shape,
        rimLight = false,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = geometry.spacing.md, vertical = geometry.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = hint,
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.textSecondary,
                modifier = Modifier.weight(1f),
            )
            ChevronIcon(
                pointsLeft = rtl,
                tint = tokens.textSecondary,
                size = geometry.icons.md,
                strokeWidth = geometry.glass.borderWidth,
            )
        }
    }
}

/** Live preview of the chosen ink and size, on the actual mushaf paper color. */
@Composable
private fun ReadingPreview(
    text: String,
    settings: ReadingSettings,
) {
    val geometry = LocalHawraaGeometry.current
    val tokens = LocalHawraaTokens.current
    val themeQuran = LocalQuranColors.current
    val dark = LocalHawraaDarkTheme.current
    val quran = remember(themeQuran, settings, dark) {
        themeQuran.withReadingSettings(settings, dark)
    }
    if (text.isBlank()) return

    Spacer(Modifier.height(geometry.spacing.md))
    CompositionLocalProvider(LocalQuranColors provides quran) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(geometry.radius.md))
                .background(quran.page)
                .border(
                    BorderStroke(geometry.glass.borderWidth, tokens.glassBorder),
                    RoundedCornerShape(geometry.radius.md),
                )
                .padding(horizontal = geometry.spacing.md, vertical = geometry.spacing.lg),
            contentAlignment = Alignment.Center,
        ) {
            QuranText(
                text = text,
                letterColor = quran.letter,
                diacriticColor = quran.diacritic,
                fontSize = settings.fontSizeSp.sp,
                lineHeight = quranLineHeight(settings.fontSizeSp),
            )
        }
    }
}

/**
 * Reciter picker. Chips rather than a segmented control because the labels are names, not
 * two-word enum cases, and because the list is meant to grow — the same picker the downloads
 * screen uses.
 *
 * `selectedKey == null` means "whatever the catalogue defaults to", so the default chip
 * is highlighted without the user having to have ever chosen anything.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReciterChips(
    reciters: List<Reciter>,
    selectedKey: String?,
    persian: Boolean,
    onSelect: (String?) -> Unit,
) {
    val geometry = LocalHawraaGeometry.current
    FlowRow(
        modifier = Modifier.fillMaxWidth().testTag("reciter-chips"),
        horizontalArrangement = Arrangement.spacedBy(geometry.spacing.sm),
        verticalArrangement = Arrangement.spacedBy(geometry.spacing.sm),
    ) {
        reciters.forEach { reciter ->
            GlassChip(
                label = reciter.displayName(persian),
                selected = reciter.key == selectedKey,
                onClick = { onSelect(reciter.key) },
            )
        }
    }
}

@Composable
private fun SettingLabel(title: String, value: String? = null) {
    val geometry = LocalHawraaGeometry.current
    val tokens = LocalHawraaTokens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = geometry.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = tokens.textSecondary,
        )
        if (value != null) {
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge,
                color = tokens.text,
            )
        }
    }
}
