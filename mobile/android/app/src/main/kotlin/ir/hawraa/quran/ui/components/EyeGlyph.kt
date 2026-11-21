package ir.hawraa.quran.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The show/hide-password glyph, drawn rather than imported.
 *
 * `material-icons-core` ships about forty icons and `Visibility`/`VisibilityOff` are not among
 * them; the rest live in `material-icons-extended`, a 10 MB artifact that would be pulled in for
 * two shapes. Same reasoning as [TransportIcons], and the same answer: the almond is two quadratic
 * curves and the pupil is a circle, which is a dozen lines of geometry instead of a dependency.
 *
 * A Canvas rather than an `ImageVector` because the two states are *one* drawing with one optional
 * stroke — an open eye, or the same eye crossed out. Two hand-written path sets would have to be
 * kept visually consistent forever; one cannot drift from itself.
 *
 * The glyph itself carries no semantics. It is a picture of a state; the thing a screen reader has
 * to hear is the *action* ("show password"), which is [PasswordReveal]'s job.
 */
@Composable
fun EyeGlyph(
    open: Boolean,
    tint: Color,
    glyphSize: Dp = 20.dp,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier.size(glyphSize)) {
        val width = size.width
        val height = size.height
        val stroke = width * 0.085f
        val centre = Offset(width * 0.5f, height * 0.5f)

        val almond = Path().apply {
            moveTo(width * 0.05f, height * 0.5f)
            quadraticTo(width * 0.5f, height * 0.02f, width * 0.95f, height * 0.5f)
            quadraticTo(width * 0.5f, height * 0.98f, width * 0.05f, height * 0.5f)
            close()
        }
        drawPath(almond, color = tint, style = Stroke(width = stroke, cap = StrokeCap.Round))

        if (open) {
            drawCircle(color = tint, radius = height * 0.15f, center = centre)
        } else {
            // The slash runs corner to corner, which is the convention every password field uses.
            drawLine(
                color = tint,
                start = Offset(width * 0.14f, height * 0.86f),
                end = Offset(width * 0.86f, height * 0.14f),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
    }
}

/**
 * A password field's reveal control: the glyph inside a full-size touch target.
 *
 * The target is [size] square regardless of how small the drawing is, because a 20 dp eye is a
 * 20 dp finger target and this one sits at the edge of a field where a miss lands on the keyboard.
 * `Role.Button` plus a state-dependent description is what makes it announce as "show password"
 * rather than as an unlabelled picture.
 */
@Composable
fun PasswordReveal(
    revealed: Boolean,
    onToggle: () -> Unit,
    description: String,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clickable(onClick = onToggle)
            .semantics {
                contentDescription = description
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        EyeGlyph(open = revealed, tint = tint)
    }
}
