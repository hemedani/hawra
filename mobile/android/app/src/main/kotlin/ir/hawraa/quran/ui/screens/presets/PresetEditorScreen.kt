package ir.hawraa.quran.ui.screens.presets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.hawraa.quran.R
import ir.hawraa.quran.domain.preset.PresetStep
import ir.hawraa.quran.domain.preset.PresetStepKind
import ir.hawraa.quran.domain.quran.toPersianDigits
import ir.hawraa.quran.ui.appLocale
import ir.hawraa.quran.ui.components.BubbleButton
import ir.hawraa.quran.ui.components.GlassChip
import ir.hawraa.quran.ui.components.GlassHeader
import ir.hawraa.quran.ui.components.GlassSurface
import ir.hawraa.quran.ui.components.ICON_GLYPHS
import ir.hawraa.quran.ui.components.LocalShellInsets
import ir.hawraa.quran.ui.components.ScreenFrame
import ir.hawraa.quran.ui.theme.LocalGlassColors
import ir.hawraa.quran.ui.theme.LocalHawraaGeometry

/**
 * The preset editor: a name, an ordered list of steps, and nothing else.
 *
 * Ordering is the whole feature — "verse twice, then the translation" is a sequence — so the
 * steps are a vertical list in play order with a repeat count per row, not a set of toggles.
 */
@Composable
fun PresetEditorScreen(
    onBack: () -> Unit,
    viewModel: PresetEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Saving pops: the list is the place where the result is visible, and staying on a form that
    // has already been committed invites a second, accidental save.
    LaunchedEffect(state.finished) {
        if (state.finished) onBack()
    }

    PresetEditorContent(
        state = state,
        onBack = onBack,
        onName = viewModel::onName,
        onAddStep = viewModel::addStep,
        onRemoveStep = viewModel::removeStep,
        onChangeRepeat = viewModel::changeRepeat,
        onSave = viewModel::save,
    )
}

/** Stateless body, so a screenshot test can render it without Hilt. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PresetEditorContent(
    state: PresetEditorUiState,
    onBack: () -> Unit,
    onName: (String) -> Unit = {},
    onAddStep: (EditorStep) -> Unit = {},
    onRemoveStep: (Int) -> Unit = {},
    onChangeRepeat: (Int, Int) -> Unit = { _, _ -> },
    onSave: () -> Unit = {},
) {
    val geometry = LocalHawraaGeometry.current
    val glass = LocalGlassColors.current
    val shellInsets = LocalShellInsets.current
    val persian = LocalConfiguration.current.appLocale().language == "fa"

    ScreenFrame(
        header = { headerModifier ->
            GlassHeader(
                modifier = headerModifier,
                title = stringResource(
                    if (state.isNew) R.string.editor_title_new else R.string.editor_title_edit,
                ),
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
            OutlinedTextField(
                value = state.name,
                onValueChange = onName,
                label = { Text(stringResource(R.string.editor_name)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth().testTag("editor_name"),
            )

            Spacer(Modifier.height(geometry.spacing.lg))
            Text(
                text = stringResource(R.string.editor_hint),
                style = MaterialTheme.typography.labelSmall,
                color = glass.textTertiary,
            )

            Spacer(Modifier.height(geometry.spacing.md))
            if (state.steps.isEmpty()) {
                Text(
                    text = stringResource(R.string.editor_no_steps),
                    style = MaterialTheme.typography.bodyMedium,
                    color = glass.textSecondary,
                )
            } else {
                state.steps.forEachIndexed { index, step ->
                    StepRow(
                        index = index,
                        step = step,
                        persian = persian,
                        onChangeRepeat = { delta -> onChangeRepeat(index, delta) },
                        onRemove = { onRemoveStep(index) },
                    )
                    Spacer(Modifier.height(geometry.spacing.sm))
                }
            }

            Spacer(Modifier.height(geometry.spacing.md))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(geometry.spacing.sm),
                verticalArrangement = Arrangement.spacedBy(geometry.spacing.sm),
            ) {
                EditorStep.entries.forEach { option ->
                    GlassChip(
                        label = stringResource(
                            when (option) {
                                EditorStep.AYAH -> R.string.editor_add_ayah
                                EditorStep.TRANSLATION -> R.string.editor_add_translation
                                EditorStep.SILENCE -> R.string.editor_add_silence
                            },
                        ),
                        selected = false,
                        onClick = { onAddStep(option) },
                        modifier = Modifier.testTag("editor_add_${option.name}"),
                    )
                }
            }

            Spacer(Modifier.height(geometry.spacing.xl))
            SaveAction(enabled = state.canSave, busy = state.saving, onClick = onSave)
        }
    }
}

@Composable
private fun StepRow(
    index: Int,
    step: PresetStep,
    persian: Boolean,
    onChangeRepeat: (Int) -> Unit,
    onRemove: () -> Unit,
) {
    val geometry = LocalHawraaGeometry.current
    val glass = LocalGlassColors.current
    val label = stringResource(
        when (step.kind) {
            PresetStepKind.AYAH -> R.string.preset_step_ayah
            PresetStepKind.TRANSLATION -> R.string.preset_step_translation
            PresetStepKind.TAFSIR -> R.string.preset_step_tafsir
            PresetStepKind.SILENCE -> R.string.preset_step_silence
        },
    )

    GlassSurface(
        modifier = Modifier.fillMaxWidth().testTag("editor_step_$index"),
        shape = RoundedCornerShape(geometry.radius.md),
        rimLight = false,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = geometry.spacing.lg, vertical = geometry.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "${digits(index + 1, persian)}. $label",
                style = MaterialTheme.typography.bodyLarge,
                color = glass.text,
                modifier = Modifier.weight(1f),
            )

            // Repeat is a stepper, not a slider: the meaningful values are 1…10 and a tap is
            // more precise than a drag for a number this small.
            if (step.kind != PresetStepKind.SILENCE) {
                Stepper(
                    value = digits(step.repeat, persian),
                    decrease = { onChangeRepeat(-1) },
                    increase = { onChangeRepeat(1) },
                    decreaseEnabled = step.repeat > 1,
                    increaseEnabled = step.repeat < PresetStep.MAX_REPEAT,
                    description = stringResource(R.string.editor_step_repeat),
                )
            }

            val removeLabel = stringResource(R.string.editor_remove)
            Text(
                text = removeLabel,
                style = MaterialTheme.typography.labelMedium,
                color = glass.danger,
                modifier = Modifier
                    .clip(RoundedCornerShape(geometry.radius.pill))
                    .clickable(onClick = onRemove)
                    .padding(horizontal = geometry.spacing.sm, vertical = geometry.spacing.xs)
                    .semantics { contentDescription = removeLabel },
            )
        }
    }
}

/**
 * `−  ۲  +` for the repeat count.
 *
 * Text glyphs rather than icons: `material-icons-core` has `Add` but no `Remove`, and pulling in
 * the extended set for one minus sign would add megabytes for one glyph.
 */
@Composable
private fun Stepper(
    value: String,
    decrease: () -> Unit,
    increase: () -> Unit,
    decreaseEnabled: Boolean,
    increaseEnabled: Boolean,
    description: String,
) {
    val geometry = LocalHawraaGeometry.current
    val glass = LocalGlassColors.current

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(geometry.spacing.xs),
        modifier = Modifier.semantics { contentDescription = description },
    ) {
        StepperGlyph("\u2212", decreaseEnabled, glass.icon, decrease)
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            color = glass.text,
        )
        StepperGlyph("+", increaseEnabled, glass.icon, increase)
    }
}

@Composable
private fun StepperGlyph(glyph: String, enabled: Boolean, tint: Color, onClick: () -> Unit) {
    val geometry = LocalHawraaGeometry.current
    Text(
        text = glyph,
        style = MaterialTheme.typography.titleMedium,
        color = if (enabled) tint else LocalGlassColors.current.textTertiary,
        modifier = Modifier
            .clip(RoundedCornerShape(geometry.radius.pill))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = geometry.spacing.sm, vertical = geometry.spacing.xs),
    )
}

@Composable
private fun SaveAction(enabled: Boolean, busy: Boolean, onClick: () -> Unit) {
    val geometry = LocalHawraaGeometry.current
    val glass = LocalGlassColors.current
    val shape = RoundedCornerShape(geometry.radius.pill)
    val active = enabled && !busy

    Text(
        text = stringResource(R.string.editor_save),
        style = MaterialTheme.typography.labelLarge,
        color = if (active) glass.accent else glass.textTertiary,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (active) glass.accentSoft else glass.glassFill)
            .border(BorderStroke(1.dp, if (active) glass.accent else glass.glassBorder), shape)
            .clickable(enabled = active, onClick = onClick)
            .padding(vertical = geometry.spacing.md)
            .testTag("editor_save"),
    )
}

private fun digits(value: Int, persian: Boolean): String =
    if (persian) value.toString().toPersianDigits() else value.toString()
