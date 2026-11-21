package ir.hawraa.quran.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ir.hawraa.quran.ui.theme.LocalGlassColors
import ir.hawraa.quran.ui.theme.LocalHawraaGeometry

/**
 * A segmented control painted from the glass tokens (`docs/DESIGN.md` §10.3).
 *
 * [SegmentedPill] does the same job but reads `MaterialTheme.colorScheme`, which is the one place
 * in the app that uses Material's palette instead of [LocalGlassColors]. On a screen whose whole
 * surface is glass that difference shows: the track comes out a slightly different grey from every
 * pane around it. This is the same control with the right palette, and the selected segment is a
 * **filled accent** rather than a tint, so "which one am I in" survives being read at a glance.
 *
 * A chip is a *filter*; this is a *choice*. The two look similar enough that mixing them makes the
 * app feel imprecise, so anything picking one-of-N uses this and anything toggling-many uses a
 * [GlassChip].
 */
@Composable
fun <T> GlassSegments(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: @Composable (T) -> String,
    modifier: Modifier = Modifier,
    testTagOf: ((T) -> String)? = null,
) {
    val glass = LocalGlassColors.current
    val geometry = LocalHawraaGeometry.current
    val shape = RoundedCornerShape(geometry.radius.pill)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(glass.glassFill)
            .border(BorderStroke(1.dp, glass.glassBorder), shape)
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            val fill by animateColorAsState(
                targetValue = if (isSelected) glass.accent else androidx.compose.ui.graphics.Color.Transparent,
                label = "segmentFill",
            )
            val content by animateColorAsState(
                targetValue = if (isSelected) glass.onAccent else glass.textSecondary,
                label = "segmentLabel",
            )
            Text(
                text = label(option),
                style = MaterialTheme.typography.labelLarge,
                color = content,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier
                    .weight(1f)
                    .clip(shape)
                    .background(fill)
                    .clickable { onSelect(option) }
                    .padding(vertical = geometry.spacing.md)
                    .then(
                        if (testTagOf != null) Modifier.testTag(testTagOf(option)) else Modifier,
                    ),
            )
        }
    }
}
