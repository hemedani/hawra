package ir.hawraa.quran.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import ir.hawraa.quran.ui.theme.LocalHawraaGeometry
import ir.hawraa.quran.ui.theme.LocalHawraaTokens

/**
 * The hairline crest that opens a sheet, and separates the dock's two halves.
 *
 * [Wave] owns the curve; this only supplies the sheet/dock's standard amplitude,
 * wavelength and taper, so the two places cannot drift into two different seas.
 */
@Composable
fun HairlineWave(
    modifier: Modifier = Modifier,
    color: Color = LocalHawraaTokens.current.glassBorder,
    height: Dp = LocalHawraaGeometry.current.wave.hairlineHeight,
    phase: Float = 0.6f,
) {
    val wave = LocalHawraaGeometry.current.wave
    Canvas(
        modifier
            .fillMaxWidth()
            .height(height),
    ) {
        val cy = size.height / 2f
        drawPath(
            path = wavePath(
                from = 0f,
                to = size.width,
                cy = cy,
                amplitude = wave.hairlineAmplitude.toPx(),
                wavelength = wave.hairlineWavelength.toPx(),
                phase = phase,
                taper = wave.hairlineTaper.toPx(),
                variation = 0.5f,
            ),
            color = color,
            style = Stroke(width = wave.hairlineStroke.toPx(), cap = StrokeCap.Round),
        )
    }
}
