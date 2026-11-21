package ir.hawraa.quran.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.hawraa.quran.R
import ir.hawraa.quran.domain.AppLanguage
import ir.hawraa.quran.domain.ThemeMode
import ir.hawraa.quran.domain.quran.toPersianDigits
import ir.hawraa.quran.domain.settings.DiacriticInk
import ir.hawraa.quran.domain.settings.LetterInk
import ir.hawraa.quran.domain.recitation.Reciter
import ir.hawraa.quran.domain.settings.ReadingSettings
import ir.hawraa.quran.domain.settings.RecitationSettings
import ir.hawraa.quran.ui.appLocale
import ir.hawraa.quran.ui.components.ChevronIcon
import ir.hawraa.quran.ui.components.GlassSurface
import ir.hawraa.quran.ui.components.SegmentedPill
import ir.hawraa.quran.ui.quran.QuranText
import ir.hawraa.quran.ui.quran.quranLineHeight
import ir.hawraa.quran.ui.theme.LocalGlassColors
import ir.hawraa.quran.ui.theme.LocalHawraaDarkTheme
import ir.hawraa.quran.ui.theme.LocalHawraaGeometry
import ir.hawraa.quran.ui.theme.LocalQuranColors
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
@OptIn(ExperimentalMaterial3Api::class)
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
    Scaffold(
        // Transparent so the shell's ambient canvas shows through the settings page too.
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(
                    start = 20.dp,
                    end = 20.dp,
                    top = 8.dp,
                    // Room for the dock, so the last section is never hidden behind it.
                    bottom = geometry.dock.dockInset,
                ),
        ) {
            SectionTitle(stringResource(R.string.settings_theme))
            SegmentedPill(
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

            Spacer(Modifier.height(28.dp))
            SectionTitle(stringResource(R.string.settings_language))
            SegmentedPill(
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

            Spacer(Modifier.height(28.dp))
            SectionTitle(stringResource(R.string.settings_reading))

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
                modifier = Modifier.fillMaxWidth(),
            )

            ReadingPreview(text = previewText, settings = reading)

            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.settings_show_translation),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Switch(
                    checked = reading.showTranslation,
                    onCheckedChange = onShowTranslation,
                )
            }

            Spacer(Modifier.height(20.dp))
            SettingLabel(title = stringResource(R.string.settings_letter_color))
            SegmentedPill(
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

            Spacer(Modifier.height(20.dp))
            SettingLabel(title = stringResource(R.string.settings_diacritic_color))
            SegmentedPill(
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

            if (reciters.isNotEmpty()) {
                Spacer(Modifier.height(28.dp))
                SectionTitle(stringResource(R.string.settings_recitation))
                SettingLabel(title = stringResource(R.string.recitation_reciter))
                ReciterChips(
                    reciters = reciters,
                    selectedKey = recitation.reciterKey,
                    persian = LocalConfiguration.current.appLocale().language == "fa",
                    onSelect = onReciter,
                )
            }

            Spacer(Modifier.height(32.dp))
            SectionTitle(stringResource(R.string.settings_presets))
            LinkRow(
                hint = stringResource(R.string.settings_presets_hint),
                tag = "settings_presets",
                onClick = onPresets,
            )
            Spacer(Modifier.height(16.dp))
            SectionTitle(stringResource(R.string.settings_downloads))
            LinkRow(
                hint = stringResource(R.string.settings_downloads_hint),
                tag = "settings_downloads",
                onClick = onDownloads,
            )

            Spacer(Modifier.height(32.dp))

            // The ayah marker is Delapouite's CC BY icon; the licence requires the
            // attribution to be visible in the product, not only in a text file.
            Text(
                text = stringResource(R.string.settings_credits),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = stringResource(R.string.recitation_credit),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
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
    val glass = LocalGlassColors.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(geometry.radius.md))
            .clickable(onClick = onClick)
            .testTag(tag),
        shape = RoundedCornerShape(geometry.radius.md),
        rimLight = false,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = geometry.spacing.lg, vertical = geometry.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = hint,
                style = MaterialTheme.typography.bodyMedium,
                color = glass.textSecondary,
                modifier = Modifier.weight(1f),
            )
            ChevronIcon(pointsLeft = rtl, tint = glass.icon)
        }
    }
}

/** Live preview of the chosen ink and size, on the actual mushaf paper color. */
@Composable
private fun ReadingPreview(
    text: String,
    settings: ReadingSettings,
) {
    val themeQuran = LocalQuranColors.current
    val dark = LocalHawraaDarkTheme.current
    val quran = remember(themeQuran, settings, dark) {
        themeQuran.withReadingSettings(settings, dark)
    }
    if (text.isBlank()) return

    Spacer(Modifier.height(12.dp))
    CompositionLocalProvider(LocalQuranColors provides quran) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = quran.page,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
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
}

/**
 * Reciter picker. Chips rather than a segmented control because the labels are names,
 * not two-word enum cases, and because the list is meant to grow.
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
    FlowRow(
        modifier = Modifier.fillMaxWidth().testTag("reciter-chips"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        reciters.forEach { reciter ->
            val selected = reciter.key == selectedKey
            Surface(
                onClick = { onSelect(reciter.key) },
                shape = RoundedCornerShape(50),
                color = if (selected) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.surface
                },
                border = BorderStroke(
                    1.dp,
                    if (selected) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
                ),
            ) {
                Text(
                    text = reciter.displayName(persian),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(bottom = 12.dp),
    )
}

@Composable
private fun SettingLabel(title: String, value: String? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (value != null) {
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
