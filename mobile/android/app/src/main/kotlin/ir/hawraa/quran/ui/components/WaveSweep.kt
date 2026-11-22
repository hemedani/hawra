package ir.hawraa.quran.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import ir.hawraa.quran.ui.theme.LocalHawraaGeometry
import ir.hawraa.quran.ui.theme.LocalHawraaTokens
import ir.hawraa.quran.ui.theme.RampCyan
import ir.hawraa.quran.ui.theme.WaveSweepEasing
import ir.hawraa.quran.ui.theme.useMotionEnabled
import kotlin.math.PI
import kotlin.math.sin

/**
 * The direction the navigation current is travelling.
 *
 * [TabDrift] keeps the **logical** tab-order direction (positive = towards a higher
 * index); [WaveSweep] mirrors it for RTL, so the shell never has to know the layout
 * direction when it classifies a route change.
 */
sealed interface NavWaveKind {
    data class TabDrift(val dir: Int) : NavWaveKind
    data object Push : NavWaveKind
    data object Pop : NavWaveKind
}

/**
 * The luminous ocean crest that crosses the screen on navigation (DESIGN.md §9).
 *
 * A sheer aqua band, two sine crests and a gliding swimmer trailing a twin wake
 * ([drawWaveWake]) sweep in the current's direction, then vanish. Under reduced motion
 * nothing is drawn at all — the canvas surge is likewise pinned to rest.
 */
@Composable
fun WaveSweep(
    trigger: Int,
    kind: NavWaveKind,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalHawraaTokens.current
    val sweep = LocalHawraaGeometry.current.waveSweep
    val motion = useMotionEnabled()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val progress = remember { Animatable(0f) }
    LaunchedEffect(trigger, motion) {
        if (trigger <= 0 || !motion) {
            progress.snapTo(0f)
            return@LaunchedEffect
        }
        progress.snapTo(0f)
        progress.animateTo(1f, tween(sweep.durationMs, easing = WaveSweepEasing))
    }
    val eased = progress.value
    if (!motion || eased <= 0f || eased >= 1f) return
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        if (w <= 1f || h <= 1f) return@Canvas
        val fade = sin(eased * PI).toFloat()
        val wavelength = sweep.wavelength.toPx()

        val cyBase: Float
        val headX: Float
        val swimDir: Float
        val amp: Float
        val phase: Float
        val echoLag: Float

        when (kind) {
            is NavWaveKind.TabDrift -> {
                val dir = kind.dir * if (rtl) -1 else 1
                swimDir = if (dir >= 0) -1f else 1f
                headX = if (swimDir < 0f) w * (1f - eased) else w * eased
                cyBase = h * sweep.tabCenterFraction - sweep.tabCenterOffset.toPx() * fade
                amp = sweep.tabAmplitude.toPx()
                phase = eased * sweep.tabPhaseSpan * -swimDir
                echoLag = -sweep.tabEchoLag.toPx()
            }

            NavWaveKind.Push -> {
                swimDir = 1f
                headX = w * (sweep.pushStartX + sweep.pushSpanX * eased)
                cyBase = h * (sweep.pushStartY - sweep.pushSpanY * eased)
                amp = sweep.pushAmplitude.toPx()
                phase = eased * sweep.pushPhaseSpan
                echoLag = sweep.pushEchoLag.toPx()
            }

            NavWaveKind.Pop -> {
                swimDir = -1f
                headX = w * (sweep.popStartX - sweep.popSpanX * eased)
                cyBase = h * (sweep.popStartY + sweep.popSpanY * eased)
                amp = sweep.popAmplitude.toPx()
                phase = -eased * sweep.popPhaseSpan
                echoLag = -sweep.popEchoLag.toPx()
            }
        }

        // Sheer translucent water wash riding behind the crest.
        val bandHalf = sweep.bandHalf.toPx()
        val bandTop = (cyBase - bandHalf).coerceIn(0f, (h - 2f).coerceAtLeast(0f))
        val bandBottom = (cyBase + bandHalf).coerceIn(bandTop + 1f, h.coerceAtLeast(bandTop + 1f))
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.Transparent,
                    RampCyan.third.copy(alpha = sweep.bandCyanAlpha * fade),
                    tokens.accentTeal.copy(alpha = sweep.bandTealAlpha * fade),
                    Color.Transparent,
                ),
                startY = bandTop,
                endY = bandBottom,
            ),
            topLeft = Offset(0f, bandTop),
            size = Size(w, bandBottom - bandTop),
        )

        // Primary and secondary wave crests.
        listOf(
            Triple(
                0f,
                amp,
                tokens.accent.copy(alpha = sweep.crestAlpha * fade) to sweep.crestStroke.toPx(),
            ),
            Triple(
                echoLag,
                amp * sweep.echoAmplitudeScale,
                RampCyan.third.copy(alpha = sweep.echoCrestAlpha * fade) to
                    sweep.echoCrestStroke.toPx(),
            ),
        ).forEach { (lag, crestAmp, strokeStyle) ->
            drawPath(
                path = wavePath(
                    from = 0f,
                    to = w,
                    cy = cyBase + lag,
                    amplitude = crestAmp,
                    wavelength = wavelength,
                    phase = phase,
                    taper = sweep.crestTaper.toPx(),
                    variation = sweep.crestVariation,
                ),
                color = strokeStyle.first,
                style = Stroke(width = strokeStyle.second, cap = StrokeCap.Round),
            )
        }

        // Gliding swimmer + twin flanking wake strands following the primary wave curve.
        val headY = cyBase + waveOffsetAt(headX, amp, wavelength, phase, sweep.crestVariation)
        drawWaveWake(
            headX = headX,
            cy = cyBase,
            spread = -sweep.wakeSpread.toPx(),
            phaseShift = sweep.wakePhaseShift,
            amplitude = amp,
            phase = phase,
            color = RampCyan.third,
            scale = 1f,
            lengthDp = sweep.wakeLength,
            wavelength = wavelength,
            alphaBase = sweep.wakeAlpha * fade,
            amplitudeScale = sweep.wakeAmplitudeScale,
            direction = swimDir,
        )
        drawWaveWake(
            headX = headX,
            cy = cyBase,
            spread = sweep.wakeSpread.toPx(),
            phaseShift = -sweep.wakePhaseShift,
            amplitude = amp,
            phase = phase,
            color = tokens.accent,
            scale = 1f,
            lengthDp = sweep.wakeEchoLength,
            wavelength = wavelength,
            alphaBase = sweep.wakeEchoAlpha * fade,
            amplitudeScale = sweep.wakeAmplitudeScale,
            direction = swimDir,
        )

        // Streamlined swimmer crest + soft bioluminescent halo.
        drawCircle(
            color = RampCyan.third.copy(alpha = sweep.haloAlpha * fade),
            radius = sweep.haloRadius.toPx(),
            center = Offset(headX, headY),
        )
        val tailX = (headX - swimDir * sweep.tailLength.toPx()).coerceIn(0f, w)
        val tailY = cyBase + waveOffsetAt(tailX, amp, wavelength, phase, sweep.crestVariation)
        drawLine(
            color = tokens.accent.copy(alpha = sweep.tailAlpha * fade),
            start = Offset(tailX, tailY),
            end = Offset(headX, headY),
            strokeWidth = sweep.tailStroke.toPx(),
            cap = StrokeCap.Round,
        )
        drawCircle(
            color = tokens.accent.copy(alpha = sweep.headAlpha * fade),
            radius = sweep.headRadius.toPx(),
            center = Offset(headX, headY),
        )
    }
}
