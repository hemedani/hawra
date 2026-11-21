package ir.hawraa.quran.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import ir.hawraa.quran.ui.theme.LocalHawraaGeometry
import ir.hawraa.quran.ui.theme.LocalHawraaTokens

/**
 * The read-only sibling of the wave scrubber: the same track, no handle.
 *
 * Used wherever a value has to be *shown* rather than changed — how far into a track a
 * partial listen got, where a session's span sat inside its file. Same curve, same
 * accents, so a progress readout looks like the player instead of like a generic bar.
 */
@Composable
fun WaveProgressBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    height: Dp = LocalHawraaGeometry.current.wave.progressHeight,
    color: Color? = null,
    showHead: Boolean = true,
    restingColor: Color? = null,
) {
    val tokens = LocalHawraaTokens.current
    val wave = LocalHawraaGeometry.current.wave
    val accent = color ?: tokens.accent
    val resting = restingColor ?: tokens.glassBorder
    val value = fraction.coerceIn(0f, 1f)
    Canvas(modifier.fillMaxWidth().height(height)) {
        val cy = size.height / 2f
        val wavelength = wave.progressWavelength.toPx()
        val amplitude = wave.progressAmplitude.toPx()
        drawPath(
            path = wavePath(
                from = 0f,
                to = size.width,
                cy = cy,
                amplitude = amplitude * wave.progressRestingAmplitudeScale,
                wavelength = wavelength * wave.progressRestingWavelengthScale,
                phase = 0f,
                taper = wave.progressRestingTaper.toPx(),
                variation = wave.progressRestingVariation,
            ),
            color = resting,
            style = Stroke(width = wave.progressRestingStroke.toPx(), cap = StrokeCap.Round),
        )
        val headX = size.width * value
        if (headX <= 0f) return@Canvas
        drawPath(
            path = wavePath(
                from = 0f,
                to = headX,
                cy = cy,
                amplitude = amplitude,
                wavelength = wavelength,
                phase = 0f,
                taper = wave.progressActiveTaper.toPx(),
                variation = wave.progressActiveVariation,
                taperEnd = 0f,
            ),
            color = accent,
            style = Stroke(width = wave.progressActiveStroke.toPx(), cap = StrokeCap.Round),
        )
        if (showHead) {
            val headY = cy + waveOffsetAt(headX, amplitude, wavelength, 0f, wave.progressActiveVariation)
            drawCircle(
                accent.copy(alpha = 0.18f),
                wave.progressHeadGlowRadius.toPx(),
                Offset(headX, headY),
            )
            drawCircle(accent, wave.progressHeadRadius.toPx(), Offset(headX, headY))
        }
    }
}
