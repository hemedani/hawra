package ir.hawraa.quran.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.hawraa.quran.ui.theme.LocalGlassColors
import ir.hawraa.quran.ui.theme.LocalHawraaGeometry

/**
 * A filter / toggle pill (`docs/DESIGN.md` §10.3).
 *
 * A chip is a *filter*; a segmented control is a *choice*. Do not use a chip where the reader is
 * picking one of two — that is a segmented control's job, and the two look similar enough that
 * mixing them makes the app feel imprecise.
 *
 * Selected is `accentSoft` + an accent hairline + an accent label, which is the same "accent means
 * current" rule the dock and the play bubble follow.
 */
@Composable
fun GlassChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Spoken description when the label alone is ambiguous ("۱٫۲۵×" means nothing read aloud). */
    description: String? = null,
) {
    val glass = LocalGlassColors.current
    val geometry = LocalHawraaGeometry.current
    val shape = RoundedCornerShape(geometry.radius.pill)

    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = if (selected) glass.accent else glass.textSecondary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .clip(shape)
            .background(if (selected) glass.accentSoft else glass.glassFill)
            .border(
                BorderStroke(1.dp, if (selected) glass.accent else glass.glassBorder),
                shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp)
            .then(
                if (description != null) {
                    Modifier.semantics { contentDescription = description }
                } else {
                    Modifier
                },
            ),
    )
}
