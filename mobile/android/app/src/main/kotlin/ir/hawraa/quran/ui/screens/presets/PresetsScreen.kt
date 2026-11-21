package ir.hawraa.quran.ui.screens.presets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.hawraa.quran.R
import ir.hawraa.quran.data.repository.PresetCoverageSummary
import ir.hawraa.quran.domain.preset.CoverageRange
import ir.hawraa.quran.domain.preset.Preset
import ir.hawraa.quran.domain.preset.PresetStep
import ir.hawraa.quran.domain.preset.PresetStepKind
import ir.hawraa.quran.domain.quran.toPersianDigits
import ir.hawraa.quran.ui.appLocale
import ir.hawraa.quran.ui.components.GlassChip
import ir.hawraa.quran.ui.components.GlassSurface
import ir.hawraa.quran.ui.theme.LocalGlassColors
import ir.hawraa.quran.ui.theme.LocalHawraaGeometry
import java.util.Locale

/**
 * The preset list: what each recipe does, and how much of the Quran it has covered.
 *
 * Coverage lives here rather than in History because it is a property of a *preset*, not of a
 * sitting: History answers "what did I do on Tuesday", this answers "how much have I heard with
 * this recipe", which is the question a preset exists to make askable.
 */
@Composable
fun PresetsScreen(
    onBack: () -> Unit,
    onNew: () -> Unit,
    onEdit: (String) -> Unit,
    viewModel: PresetsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    PresetsContent(
        state = state,
        onBack = onBack,
        onNew = onNew,
        onEdit = onEdit,
        onSetDefault = viewModel::setDefault,
        onDelete = viewModel::remove,
    )
}

/** Stateless body, so a screenshot test can render it without Hilt. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PresetsContent(
    state: PresetsUiState,
    onBack: () -> Unit,
    onNew: () -> Unit = {},
    onEdit: (String) -> Unit = {},
    onSetDefault: (String) -> Unit = {},
    onDelete: (String) -> Unit = {},
) {
    val geometry = LocalHawraaGeometry.current
    val glass = LocalGlassColors.current
    val persian = LocalConfiguration.current.appLocale().language == "fa"

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.presets_title)) },
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
                    start = geometry.screenPadding,
                    end = geometry.screenPadding,
                    top = geometry.spacing.sm,
                    bottom = geometry.dock.dockInset,
                ),
        ) {
            PrimaryAction(
                label = stringResource(R.string.presets_new),
                onClick = onNew,
                modifier = Modifier.testTag("presets_new"),
            )

            Spacer(Modifier.height(geometry.spacing.xl))
            if (state.isEmpty) {
                Text(
                    text = stringResource(R.string.presets_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = glass.textSecondary,
                )
            } else {
                state.rows.forEach { row ->
                    PresetCard(
                        row = row,
                        persian = persian,
                        onEdit = { onEdit(row.preset.clientId) },
                        onSetDefault = { onSetDefault(row.preset.clientId) },
                        onDelete = { onDelete(row.preset.clientId) },
                    )
                    Spacer(Modifier.height(geometry.spacing.md))
                }
            }
        }
    }
}

@Composable
private fun PresetCard(
    row: PresetRow,
    persian: Boolean,
    onEdit: () -> Unit,
    onSetDefault: () -> Unit,
    onDelete: () -> Unit,
) {
    val geometry = LocalHawraaGeometry.current
    val glass = LocalGlassColors.current
    val preset = row.preset
    val coverage = row.coverage

    GlassSurface(
        modifier = Modifier.fillMaxWidth().testTag("preset_card_${preset.clientId}"),
        shape = RoundedCornerShape(geometry.radius.card),
        rimLight = false,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = geometry.spacing.lg, vertical = geometry.spacing.md),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = preset.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = glass.text,
                    modifier = Modifier.weight(1f),
                )
                if (preset.isDefault) {
                    Text(
                        text = stringResource(R.string.presets_default_badge),
                        style = MaterialTheme.typography.labelSmall,
                        color = glass.accent,
                    )
                }
            }

            Spacer(Modifier.height(geometry.spacing.xs))
            Text(
                text = recipeSummary(preset.steps, persian),
                style = MaterialTheme.typography.bodyMedium,
                color = glass.textSecondary,
            )

            Spacer(Modifier.height(geometry.spacing.md))
            CoverageBlock(coverage = coverage, persian = persian)

            Spacer(Modifier.height(geometry.spacing.sm))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(geometry.spacing.lg),
            ) {
                if (!preset.isDefault) {
                    TextAction(stringResource(R.string.presets_make_default), glass.accent, onSetDefault)
                }
                TextAction(stringResource(R.string.presets_edit), glass.icon, onEdit)
                TextAction(stringResource(R.string.presets_delete), glass.danger, onDelete)
            }
        }
    }
}

/**
 * How much of the Quran this preset has covered, plus the parts themselves.
 *
 * The percentage is rounded to one decimal below 1 % and to whole numbers above it, because
 * "0.1 %" is meaningful early on and "12.4 %" is false precision later.
 */
@Composable
private fun CoverageBlock(coverage: PresetCoverageSummary?, persian: Boolean) {
    val geometry = LocalHawraaGeometry.current
    val glass = LocalGlassColors.current
    if (coverage == null || coverage.isEmpty) {
        Text(
            text = stringResource(R.string.presets_no_coverage),
            style = MaterialTheme.typography.labelSmall,
            color = glass.textTertiary,
        )
        return
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(
                R.string.presets_coverage_line,
                percent(coverage.percent, persian),
                digits(coverage.coveredAyahs, persian),
            ),
            style = MaterialTheme.typography.labelMedium,
            color = glass.textSecondary,
        )
    }
    Spacer(Modifier.height(geometry.spacing.xs))
    LinearProgressIndicator(
        progress = { (coverage.percent / 100.0).toFloat().coerceIn(0f, 1f) },
        modifier = Modifier.fillMaxWidth(),
        color = glass.accent,
        trackColor = glass.track,
    )
    if (coverage.ranges.isNotEmpty()) {
        Spacer(Modifier.height(geometry.spacing.xs))
        Text(
            text = stringResource(R.string.presets_parts, rangeLabels(coverage.ranges, persian)),
            style = MaterialTheme.typography.labelSmall,
            color = glass.textTertiary,
        )
    }
}

/**
 * The step list as one readable line: `آیه ×۲ ← ترجمه ← آیه`.
 *
 * Repeated consecutive steps are shown with a count rather than repeated, so a recipe of six
 * verses does not read as six identical words.
 */
@Composable
private fun recipeSummary(steps: List<PresetStep>, persian: Boolean): String {
    val playable = steps.filter { it.isPlayable }
    if (playable.isEmpty()) return stringResource(R.string.preset_empty)

    val parts = playable.map { step ->
        val label = stringResource(
            when (step.kind) {
                PresetStepKind.AYAH -> R.string.preset_step_ayah
                PresetStepKind.TRANSLATION -> R.string.preset_step_translation
                PresetStepKind.TAFSIR -> R.string.preset_step_tafsir
                PresetStepKind.SILENCE -> R.string.preset_step_silence
            },
        )
        if (step.repeat > 1) "$label ${stringResource(R.string.presets_repeat, digits(step.repeat, persian))}" else label
    }
    return parts.joinToString(SEPARATOR)
}

/**
 * The parts that were actually heard, e.g. `۱:۱–۱:۷, ۲:۲۵۵`.
 *
 * The label comes from [CoverageRange.label] rather than being formatted here: the domain already
 * owns the rule that a single ayah is written once and a run is written as two ends, and a second
 * copy of it would drift the first time the format changed.
 */
@Composable
private fun rangeLabels(ranges: List<CoverageRange>, persian: Boolean): String {
    val shown = ranges.take(MAX_RANGES).joinToString(", ") { range ->
        range.label { text -> if (persian) text.toPersianDigits() else text }
    }
    return if (ranges.size > MAX_RANGES) "$shown …" else shown
}

@Composable
private fun TextAction(label: String, tint: Color, onClick: () -> Unit) {
    val geometry = LocalHawraaGeometry.current
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = tint,
        modifier = Modifier
            .clip(RoundedCornerShape(geometry.radius.pill))
            .clickable(onClick = onClick)
            .padding(horizontal = geometry.spacing.sm, vertical = geometry.spacing.xs)
            .semantics { contentDescription = label },
    )
}

@Composable
private fun PrimaryAction(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val geometry = LocalHawraaGeometry.current
    val glass = LocalGlassColors.current
    val shape = RoundedCornerShape(geometry.radius.pill)
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = glass.accent,
        textAlign = TextAlign.Center,
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(glass.accentSoft)
            .border(BorderStroke(1.dp, glass.accent), shape)
            .clickable(onClick = onClick)
            .padding(vertical = geometry.spacing.md),
    )
}

/** One decimal below 1 %, whole numbers above — see [CoverageBlock]. */
private fun percent(value: Double, persian: Boolean): String {
    val raw = if (value < 1.0) {
        String.format(Locale.ROOT, "%.1f", value)
    } else {
        value.toInt().toString()
    }
    return if (persian) raw.replace(".", "\u066B").toPersianDigits() else raw
}

private fun digits(value: Int, persian: Boolean): String =
    if (persian) value.toString().toPersianDigits() else value.toString()

/** Direction-agnostic: the arrow is drawn between labels by the text run, not by the layout. */
private const val SEPARATOR = " ← "
private const val MAX_RANGES = 4
