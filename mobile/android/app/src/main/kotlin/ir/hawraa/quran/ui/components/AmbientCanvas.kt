package ir.hawraa.quran.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import ir.hawraa.quran.ui.theme.LocalGlassColors

/**
 * The ambient field every screen floats on (`docs/DESIGN.md` §3).
 *
 * Glass needs something to refract: a frosted pane over flat white is invisible, over a softly
 * coloured field it reads as glass. So the canvas is a base tint plus two or three very large,
 * very soft radial blooms — low frequency, low contrast, no noise, no hard edges.
 *
 * It is **static on purpose.** A slow drift is tempting and was considered, but this is a reading
 * app: a field that moves behind the text is a field that distracts from it. If a drift is ever
 * added it must be ≤ 0.02 alpha/second and stop entirely under reduced motion.
 *
 * The canvas does not scroll. Content scrolls over it.
 */
@Composable
fun AmbientCanvas(modifier: Modifier = Modifier) {
    val glass = LocalGlassColors.current
    val blooms = glass.blooms

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(glass.canvas)

                // Three blooms, each sized in fractions of the larger screen edge so the field
                // looks the same on a phone and a tablet.
                val span = maxOf(size.width, size.height)
                data class Bloom(val x: Float, val y: Float, val radius: Float, val alpha: Float)

                val spots = listOf(
                    Bloom(0.12f, 0.06f, span * 0.78f, 0.72f),
                    Bloom(0.98f, 0.42f, span * 0.62f, 0.52f),
                    Bloom(0.28f, 0.94f, span * 0.70f, 0.60f),
                )

                spots.forEachIndexed { index, spot ->
                    val tint = blooms[index % blooms.size]
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                tint.copy(alpha = spot.alpha),
                                tint.copy(alpha = spot.alpha * 0.35f),
                                Color.Transparent,
                            ),
                            center = Offset(size.width * spot.x, size.height * spot.y),
                            radius = spot.radius,
                        ),
                        radius = spot.radius,
                        center = Offset(size.width * spot.x, size.height * spot.y),
                    )
                }
            },
    )
}
