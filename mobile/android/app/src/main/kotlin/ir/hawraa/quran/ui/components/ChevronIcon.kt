package ir.hawraa.quran.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A chevron drawn as geometry — keeps the page-navigation controls visually
 * consistent with the mushaf ornament work and independent of the icon set.
 */
@Composable
fun ChevronIcon(
    pointsLeft: Boolean,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    strokeWidth: Dp = 2.dp,
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val path = Path()
        if (pointsLeft) {
            path.moveTo(w * 0.62f, h * 0.18f)
            path.lineTo(w * 0.36f, h * 0.5f)
            path.lineTo(w * 0.62f, h * 0.82f)
        } else {
            path.moveTo(w * 0.38f, h * 0.18f)
            path.lineTo(w * 0.64f, h * 0.5f)
            path.lineTo(w * 0.38f, h * 0.82f)
        }
        drawPath(
            path = path,
            color = tint,
            style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round),
        )
    }
}

/** Small diamond used as a decorative separator. */
@Composable
fun DiamondBullet(
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 5.dp,
) {
    Canvas(modifier = modifier.size(size)) {
        val d = this.size.minDimension / 2f
        val cx = this.size.width / 2f
        val cy = this.size.height / 2f
        val path = Path().apply {
            moveTo(cx, cy - d)
            lineTo(cx + d, cy)
            lineTo(cx, cy + d)
            lineTo(cx - d, cy)
            close()
        }
        drawPath(path, color = tint)
    }
}
