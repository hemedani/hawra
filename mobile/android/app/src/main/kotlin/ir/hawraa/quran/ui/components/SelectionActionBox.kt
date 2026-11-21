package ir.hawraa.quran.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.hawraa.quran.R
import ir.hawraa.quran.domain.quran.toPersianDigits
import ir.hawraa.quran.ui.theme.LocalGlassColors
import ir.hawraa.quran.ui.theme.LocalHawraaGeometry

/**
 * One thing the reader can do with the current selection.
 *
 * A list, not a fixed row: today it is [SelectionActionBox]'s single primary, and the glossary /
 * terminology action, "note", "copy" and "share" are later entries that need no layout change.
 */
data class SelectionAction(
    val key: String,
    val label: String,
    val icon: ImageVector,
    /** The one filled accent bubble. At most one action is primary. */
    val primary: Boolean = false,
    val onClick: () -> Unit,
)

/**
 * The floating action box that appears over the current selection (`docs/DESIGN.md` §10.6).
 *
 * This replaces the old full-width bottom transport bar. That bar took a permanent strip of the
 * screen and was never where the reader was looking; this box appears only when something is
 * selected, and it appears *near the thing it acts on*.
 *
 * Positioning is the caller's job: the box only draws, so the reader can anchor it above the
 * selected verse, flip it below when there is no room, and keep it clear of the dock.
 *
 * The primary action carries its **label** rather than a bare glyph. The box is transient and the
 * reader did not necessarily arrive at it by pressing play, so "پخش آیه ۳" says what will happen;
 * a lone triangle does not. Secondary actions are icon-only, with the label on the accessibility
 * node — the box is small and its job is to stay out of the way.
 */
@Composable
fun SelectionActionBox(
    actions: List<SelectionAction>,
    modifier: Modifier = Modifier,
) {
    if (actions.isEmpty()) return
    val glass = LocalGlassColors.current
    val geometry = LocalHawraaGeometry.current

    GlassSurface(
        modifier = modifier
            .height(56.dp)
            .testTag("selection-actions"),
        shape = RoundedCornerShape(geometry.radius.panel),
        strong = true,
        elevation = GlassElevation.Panel,
    ) {
        Row(
            modifier = Modifier
                .height(56.dp)
                .padding(start = geometry.spacing.sm, end = geometry.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(geometry.spacing.xs),
        ) {
            actions.forEach { action ->
                if (action.primary) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(geometry.spacing.xs),
                    ) {
                        BubbleButton(
                            icon = action.icon,
                            contentDescription = action.label,
                            onClick = action.onClick,
                            size = geometry.controls.lg,
                            iconSize = 18.dp,
                            filled = true,
                            modifier = Modifier.testTag("selection-${action.key}"),
                        )
                        Text(
                            text = action.label,
                            style = MaterialTheme.typography.labelLarge,
                            color = glass.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.width(geometry.spacing.xs))
                    }
                } else {
                    BubbleButton(
                        icon = action.icon,
                        contentDescription = action.label,
                        onClick = action.onClick,
                        size = geometry.controls.md,
                        iconSize = 17.dp,
                        modifier = Modifier.testTag("selection-${action.key}"),
                    )
                }
            }
        }
    }
}

/** Convenience for the reader's primary action, so its label stays in one place. */
@Composable
fun playAyahAction(ayah: Int, onClick: () -> Unit): SelectionAction = SelectionAction(
    key = "play",
    label = stringResource(R.string.selection_play_ayah, ayah.toPersianDigits()),
    icon = TransportIcons.Play,
    primary = true,
    onClick = onClick,
)

/**
 * The primary action when the reader has selected a run worth narrowing to (D32): *play selection*
 * rather than *play ayah N*, because the two promise different scopes and one label must not blur
 * them into the other.
 */
@Composable
fun playSelectionAction(onClick: () -> Unit): SelectionAction = SelectionAction(
    key = "play",
    label = stringResource(R.string.selection_play_selection),
    icon = TransportIcons.Play,
    primary = true,
    onClick = onClick,
)
