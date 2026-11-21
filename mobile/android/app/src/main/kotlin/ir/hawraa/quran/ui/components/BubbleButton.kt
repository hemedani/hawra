package ir.hawraa.quran.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ir.hawraa.quran.ui.theme.LocalGlassColors
import ir.hawraa.quran.ui.theme.LocalHawraaGeometry

/**
 * A circular glass icon button (`docs/DESIGN.md` §10.2).
 *
 * Two variants, and the difference is meaning, not decoration:
 *
 *  - **filled** — a solid accent disc with the glyph knocked out. This is *the* primary action of
 *    whatever it sits in, and there is at most one per surface (the play bubble, the current dock
 *    destination).
 *  - **outlined** — a hairline circle with an `icon`-coloured glyph. Everything else.
 *
 * The visible circle can be smaller than the touch target: the outer box always pads out to
 * [HawraaGeometry.touchTarget], so shrinking the glyph never makes a control harder to hit.
 */
@Composable
fun BubbleButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    iconSize: Dp = 18.dp,
    filled: Boolean = false,
    enabled: Boolean = true,
    /** Shows a spinner in place of the glyph while keeping the press — "stop waiting". */
    busy: Boolean = false,
    tint: androidx.compose.ui.graphics.Color? = null,
    content: @Composable (() -> Unit)? = null,
) {
    val glass = LocalGlassColors.current
    val geometry = LocalHawraaGeometry.current
    val glyph = when {
        filled -> tint ?: glass.onAccent
        else -> tint ?: if (enabled) glass.icon else glass.textTertiary
    }

    Box(
        modifier = modifier
            .size(geometry.touchTarget)
            .clip(CircleShape)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClickLabel = contentDescription,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .then(
                    if (filled) {
                        Modifier.background(if (enabled) glass.accent else glass.track)
                    } else {
                        Modifier
                            .background(glass.glassFill)
                            .border(BorderStroke(1.dp, glass.glassBorder), CircleShape)
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            when {
                busy -> CircularProgressIndicator(
                    modifier = Modifier.size(iconSize),
                    color = glyph,
                    strokeWidth = 2.dp,
                )

                content != null -> content()

                else -> Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = glyph,
                    modifier = Modifier.size(iconSize),
                )
            }
        }
    }
}
