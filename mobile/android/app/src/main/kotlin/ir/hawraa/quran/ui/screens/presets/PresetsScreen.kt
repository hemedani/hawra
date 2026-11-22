package ir.hawraa.quran.ui.screens.presets

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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import ir.hawraa.quran.ui.components.BubbleButton
import ir.hawraa.quran.ui.components.GlassHeader
import ir.hawraa.quran.ui.components.GlassSurface
import ir.hawraa.quran.ui.components.ICON_GLYPHS
import ir.hawraa.quran.ui.components.LocalShellInsets
import ir.hawraa.quran.ui.components.PrimaryButton
import ir.hawraa.quran.ui.components.ScreenFrame
import ir.hawraa.quran.ui.components.SkeletonRows
import ir.hawraa.quran.ui.theme.LocalGlassColors
import ir.hawraa.quran.ui.theme.LocalHawraaGeometry
import ir.hawraa.quran.ui.theme.OceanCascade
import ir.hawraa.quran.ui.theme.oceanEntrance
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
    val shellInsets = LocalShellInsets.current
    val persian = LocalConfiguration.current.appLocale().language == "fa"

    ScreenFrame(
        header = { headerModifier ->
            GlassHeader(
                modifier = headerModifier,
                title = stringResource(R.string.presets_title),
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
                        start = geometry.screenPadding,
                        end = geometry.screenPadding,
                        top = topInset,
                        bottom = shellInsets.bottom,
                    ),
            ) {
                PrimaryButton(
                    label = stringResource(R.string.presets_new),
                    onClick = onNew,
                    modifier = Modifier
                        .testTag("presets_new")
                        .oceanEntrance(0),
                )

                Spacer(Modifier.height(geometry.spacing.xl))
                when {
                    state.loading -> SkeletonRows(
                        count = 3,
                        modifier = Modifier.oceanEntrance(1),
                    )

                    state.isEmpty -> Text(
                        text = stringResource(R.string.presets_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = glass.textSecondary,
                        modifier = Modifier.oceanEntrance(1),
                    )

                    else -> state.rows.forEachIndexed { index, row ->
                        PresetCard(
                            row = row,
                            persian = persian,
                            onEdit = { onEdit(row.preset.clientId) },
                            onSetDefault = { onSetDefault(row.preset.clientId) },
                            onDelete = { onDelete(row.preset.clientId) },
                            modifier = Modifier.oceanEntrance(index + 1),
                        )
                        Spacer(Modifier.height(geometry.spacing.md))
                    }
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
    modifier: Modifier = Modifier,
) {
    val geometry = LocalHawraaGeometry.current
    val glass = LocalGlassColors.current
    val preset = row.preset
    val coverage = row.coverage

    GlassSurface(
        modifier = modifier.fillMaxWidth().testTag("preset_card_${preset.clientId}"),
        shape = RoundedCornerShape(geometry.radius.card),
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
