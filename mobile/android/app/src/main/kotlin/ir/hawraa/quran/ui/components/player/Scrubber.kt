package ir.hawraa.quran.ui.components.player

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import ir.hawraa.quran.R
import ir.hawraa.quran.core.HapticIntent
import ir.hawraa.quran.core.rememberHaptics
import ir.hawraa.quran.domain.WAVEFORM_BUCKETS
import ir.hawraa.quran.domain.formatClock
import ir.hawraa.quran.domain.formatRemaining
import ir.hawraa.quran.domain.sampleWaveform
import ir.hawraa.quran.domain.sampleWaveformFor
import ir.hawraa.quran.ui.appLocale
import ir.hawraa.quran.ui.components.drawWaveWake
import ir.hawraa.quran.ui.components.ramp
import ir.hawraa.quran.ui.components.rememberWavePhase
import ir.hawraa.quran.ui.components.waveOffsetAt
import ir.hawraa.quran.ui.components.wavePath
import ir.hawraa.quran.ui.theme.HawraaDuration
import ir.hawraa.quran.ui.theme.LocalHawraaGeometry
import ir.hawraa.quran.ui.theme.LocalHawraaTokens
import ir.hawraa.quran.ui.theme.ScrubberGeometry
import ir.hawraa.quran.ui.theme.WaveGeometry
import ir.hawraa.quran.ui.theme.caption
import ir.hawraa.quran.ui.theme.useMotionEnabled
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** A seek-bar dot, e.g. a verse boundary or a bookmark. */
data class ScrubberMarker(val id: String, val positionSec: Int, val color: Color?)

/** Position as a 0..1 fraction of the track. Unknown durations rest at the start. */
internal fun scrubberFraction(positionSec: Int, durationSec: Int): Float =
    if (durationSec <= 0) 0f else (positionSec.toFloat() / durationSec.toFloat()).coerceIn(0f, 1f)

/** A horizontal gesture position as a 0..1 fraction of the track. */
internal fun scrubberFractionAt(x: Float, width: Float): Float =
    if (width <= 0f) 0f else (x / width).coerceIn(0f, 1f)

/** The wire wants milliseconds, so the seek callback leaves this component in ms. */
internal fun scrubberSeekMs(fraction: Float, durationSec: Int): Int =
    if (durationSec <= 0) 0 else (fraction.coerceIn(0f, 1f) * durationSec * 1_000f).roundToInt()

/**
 * The deterministic loudness strip used when no measured waveform is available
 * (streams, undecodable files, the dock before analysis lands). It is seeded by the
 * track identity, so it paints the same water every time and is never a bare line.
 */
internal fun decorativeWaveform(
    seed: String = "hawraa",
    buckets: Int = WAVEFORM_BUCKETS,
): FloatArray = sampleWaveformFor(seed.ifBlank { "hawraa" }, buckets)

/**
 * The seek bar (`docs/DESIGN.md` §10.6).
 *
 * Two layers carry the track: a **loudness strip** — one bar per RMS window of the
 * audio — and, over the played part, a single wave with a **wake**: two strands fan
 * out from the playhead, then fade, break into dots and sink back into the one wave
 * behind it. The wake is the movement; the strip is the content.
 *
 * The playhead rides that wave rather than the axis — a body on the surface, not a pin
 * through the bar. [WavyProgress] draws the same thing at a smaller scale through the
 * same [drawTrack].
 */
@Composable
fun Scrubber(
    positionSec: Int,
    durationSec: Int,
    isPlaying: Boolean,
    onSeek: (Int) -> Unit,
    modifier: Modifier = Modifier,
    markers: List<ScrubberMarker> = emptyList(),
    waiting: Boolean = false,
    waveform: FloatArray? = null,
    waveSeed: String = "hawraa",
    onMarkerClick: (String) -> Unit = {},
) {
    val tokens = LocalHawraaTokens.current
    val geometry = LocalHawraaGeometry.current
    val scrubber = geometry.scrubber
    val motion = useMotionEnabled()
    val haptics = rememberHaptics()
    val persian = LocalConfiguration.current.appLocale().language == "fa"
    val trackLabel = stringResource(R.string.player_scrubber)
    val currentOnSeek by rememberUpdatedState(onSeek)
    val currentOnMarkerClick by rememberUpdatedState(onMarkerClick)
    var preview by remember { mutableStateOf<Float?>(null) }
    val base = scrubberFraction(positionSec, durationSec)
    // The player ticks once a second; a linear tween carries the playhead between
    // ticks so it glides instead of stepping. Seeks and pauses snap.
    val smooth by animateFloatAsState(
        targetValue = preview ?: base,
        animationSpec = if (motion && isPlaying && preview == null) {
            tween(HawraaDuration.PLAYBACK_TICK, easing = LinearEasing)
        } else {
            snap()
        },
        label = "scrubberProgress",
    )
    // While dragging, [smooth] snaps to the finger; on release the target equals the
    // last preview, so the playhead does not glide back across the seek.
    val progress = smooth
    val waveActive = (isPlaying || waiting) && motion
    val amplitude by animateFloatAsState(
        targetValue = if (waveActive) 1f else 0f,
        animationSpec = if (motion) tween(HawraaDuration.SCRUBBER) else snap(),
        label = "waveAmplitude",
    )
    val phase = rememberWavePhase()
    val values = remember(waveform, waveSeed) {
        waveform?.takeIf { it.isNotEmpty() } ?: decorativeWaveform(waveSeed)
    }

    Column(modifier.fillMaxWidth()) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(geometry.controls.lg)
                .semantics { contentDescription = trackLabel }
                .pointerInput(durationSec, markers, haptics) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        var x = down.position.x
                        var horizontal = false
                        preview = scrubberFractionAt(x, size.width.toFloat())
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            if (!change.pressed) break
                            val dx = change.position.x - down.position.x
                            val dy = change.position.y - down.position.y
                            if (!horizontal) {
                                if (abs(dy) > viewConfiguration.touchSlop && abs(dy) > abs(dx)) {
                                    preview = null
                                    break
                                }
                                if (abs(dx) > viewConfiguration.touchSlop && abs(dx) > abs(dy)) {
                                    horizontal = true
                                }
                            }
                            if (horizontal) {
                                x = change.position.x
                                preview = scrubberFractionAt(x, size.width.toFloat())
                                change.consume()
                            }
                        }
                        if (horizontal) {
                            haptics.trigger(HapticIntent.Scrub)
                            if (durationSec > 0) {
                                currentOnSeek(scrubberSeekMs(scrubberFractionAt(x, size.width.toFloat()), durationSec))
                            }
                        } else if (preview != null) {
                            val f = scrubberFractionAt(x, size.width.toFloat())
                            val hit = nearestMarker(
                                fraction = f,
                                width = size.width.toFloat(),
                                markers = markers,
                                durationSec = durationSec,
                                hitSlopPx = scrubber.markerHitSlop.toPx(),
                            )
                            if (hit != null) {
                                haptics.trigger(HapticIntent.Scrub)
                                currentOnMarkerClick(hit.id)
                            } else {
                                haptics.trigger(HapticIntent.Scrub)
                                if (durationSec > 0) {
                                    currentOnSeek(scrubberSeekMs(f, durationSec))
                                }
                            }
                        }
                        preview = null
                    }
                },
        ) {
            val cy = size.height / 2f
            drawTrack(
                playedX = size.width * progress,
                cy = cy,
                amplitude = amplitude,
                phase = phase,
                waveform = values,
                waiting = waiting,
                accent = tokens.accent,
                scale = 1f,
                scrubber = scrubber,
                waveGeometry = geometry.wave,
            )

            if (durationSec > 0) {
                markers.forEach { marker ->
                    val x = size.width * scrubberFraction(marker.positionSec, durationSec)
                    drawCircle(marker.color ?: tokens.accentTeal, scrubber.markerDot.toPx(), Offset(x, cy))
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                formatClock(positionSec, persian),
                style = MaterialTheme.typography.caption.copy(fontFeatureSettings = "tnum"),
                color = tokens.textSecondary,
            )
            Text(
                formatRemaining(positionSec, durationSec, persian),
                style = MaterialTheme.typography.caption.copy(fontFeatureSettings = "tnum"),
                color = tokens.textSecondary,
            )
        }
    }
}

/**
 * The dock's version of the same track: no handle, no caption, [ScrubberGeometry.miniScale].
 * A null [waveform] falls back to the seeded decorative strip, so the dock always shows water.
 */
@Composable
fun WavyProgress(
    progress: Float,
    isPlaying: Boolean,
    waiting: Boolean = false,
    waveform: FloatArray? = null,
    modifier: Modifier = Modifier,
    waveSeed: String = "hawraa",
) {
    val tokens = LocalHawraaTokens.current
    val geometry = LocalHawraaGeometry.current
    val scrubber = geometry.scrubber
    val motion = useMotionEnabled()
    val smoothProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = if (motion && isPlaying) tween(HawraaDuration.PLAYBACK_TICK, easing = LinearEasing) else snap(),
        label = "waveProgress",
    )
    val waveActive = (isPlaying || waiting) && motion
    val amplitude by animateFloatAsState(
        targetValue = if (waveActive) 1f else 0f,
        animationSpec = if (motion) tween(HawraaDuration.SCRUBBER) else snap(),
        label = "miniWaveAmplitude",
    )
    val phase = rememberWavePhase()
    val values = remember(waveform, waveSeed) {
        waveform?.takeIf { it.isNotEmpty() } ?: decorativeWaveform(waveSeed)
    }
    // The dock carries the scrubber's whole design — loudness strip, wake and riding
    // playhead — at mini scale. It needs real headroom: the bars reach ±5 dp and the
    // halo ±7 dp, so a 9 dp canvas would clip the crests into a smear.
    Canvas(modifier.fillMaxWidth().height(scrubber.miniHeight)) {
        drawTrack(
            playedX = size.width * smoothProgress.coerceIn(0f, 1f),
            cy = size.height / 2f,
            amplitude = amplitude,
            phase = phase,
            waveform = values,
            waiting = waiting,
            accent = tokens.accent,
            scale = scrubber.miniScale,
            scrubber = scrubber,
            waveGeometry = geometry.wave,
        )
    }
}

private fun nearestMarker(
    fraction: Float,
    width: Float,
    markers: List<ScrubberMarker>,
    durationSec: Int,
    hitSlopPx: Float,
): ScrubberMarker? {
    if (durationSec <= 0 || markers.isEmpty()) return null
    val nearest = markers.minByOrNull {
        abs(scrubberFraction(it.positionSec, durationSec) - fraction)
    } ?: return null
    val distance = abs(scrubberFraction(nearest.positionSec, durationSec) - fraction) * width
    return if (distance <= hitSlopPx) nearest else null
}

/**
 * The whole track, drawn once and used at two sizes: the scrubber at `scale = 1f`,
 * the dock's mini-player at [ScrubberGeometry.miniScale].
 *
 * Order matters — the loudness strip is the content and goes down first, the wake is
 * the movement and goes over it, and the played wave goes last so the one wave the
 * wake converges into stays on top of its own ripples.
 */
private fun DrawScope.drawTrack(
    playedX: Float,
    cy: Float,
    amplitude: Float,
    phase: Float,
    waveform: FloatArray,
    waiting: Boolean,
    accent: Color,
    scale: Float,
    scrubber: ScrubberGeometry,
    waveGeometry: WaveGeometry,
) {
    val waveAmplitude = scrubber.waveAmplitude.toPx() * scale * amplitude
    val wavelength = scrubber.waveWavelength.toPx() * scale

    drawWaveformBars(
        values = waveform,
        playedX = playedX,
        cy = cy,
        played = accent.copy(alpha = scrubber.playedAlpha),
        resting = accent.copy(alpha = scrubber.restingAlpha),
        scale = scale,
        scrubber = scrubber,
    )

    if (waiting) {
        // A slow travelling wave while the source is being resolved or buffered: the
        // same language as the playing wave, at lower contrast, without a playhead.
        drawPath(
            path = wavePath(
                from = 0f,
                to = size.width,
                cy = cy,
                amplitude = waveAmplitude,
                wavelength = wavelength,
                phase = phase,
                taper = scrubber.waitingTaper.toPx() * scale,
                variation = scrubber.waitingVariation,
            ),
            color = accent.copy(alpha = scrubber.waitingAlpha),
            style = Stroke(width = scrubber.waveStroke.toPx() * scale, cap = StrokeCap.Round),
        )
        return
    }
    if (playedX <= 0f) return

    // The wake first, so the one wave it converges into stays on top. The two strands
    // are deliberately *not* mirror images — different reach, different phase —
    // because a mirrored pair closes into a lens that reads as an outline, not as two
    // ripples on water. Paused, the amplitude animates to zero and the wake goes with
    // the motion it belongs to.
    if (amplitude > 0.01f) {
        drawWaveWake(
            headX = playedX,
            cy = cy,
            spread = -scrubber.wakeSpread.toPx() * scale,
            phaseShift = scrubber.wakePhase,
            amplitude = waveAmplitude,
            phase = phase,
            color = accent,
            scale = scale,
            lengthDp = scrubber.wakeLength.toPx() / scale,
            wavelength = wavelength,
            alphaBase = scrubber.wakeAlpha,
            amplitudeScale = scrubber.wakeAmplitudeScale,
            geometry = waveGeometry,
        )
        drawWaveWake(
            headX = playedX,
            cy = cy,
            spread = scrubber.wakeSpread.toPx() * scrubber.wakeSecondScale * scale,
            phaseShift = -scrubber.wakePhase * scrubber.wakeSecondPhaseScale,
            amplitude = waveAmplitude,
            phase = phase,
            color = accent,
            scale = scale,
            lengthDp = scrubber.wakeLength.toPx() / scale,
            wavelength = wavelength,
            alphaBase = scrubber.wakeAlpha,
            amplitudeScale = scrubber.wakeAmplitudeScale,
            geometry = waveGeometry,
        )
    }

    val taper = scrubber.activeTaper.toPx() * scale
    drawPath(
        path = wavePath(
            from = 0f,
            to = playedX,
            cy = cy,
            amplitude = waveAmplitude,
            wavelength = wavelength,
            phase = phase,
            taper = taper,
            variation = scrubber.playedVariation,
            // The playhead end is not tapered: the crest there is what the eye
            // follows, and the dot masks the cut anyway.
            taperEnd = 0f,
        ),
        color = accent,
        style = Stroke(width = scrubber.waveStroke.toPx() * scale, cap = StrokeCap.Round),
    )

    // The playhead rides the wave instead of sitting on the axis: it is a body on the
    // surface, not a pin stuck through the bar. Same curve, same phase, same taper as
    // the path above, so it is always exactly on the line.
    val headFade = ramp(playedX, min(taper, playedX / 2f))
    val headY = cy + headFade * waveOffsetAt(playedX, waveAmplitude, wavelength, phase, scrubber.playedVariation)
    drawCircle(
        accent.copy(alpha = scrubber.headHaloAlpha),
        scrubber.headHalo.toPx() * scale,
        Offset(playedX, headY),
    )
    drawCircle(accent, scrubber.headDot.toPx() * scale, Offset(playedX, headY))
}

/**
 * The loudness strip: one vertical bar per window, the played ones lit and the rest
 * faded. Callers always supply values — measured peaks when they exist, otherwise the
 * deterministic [decorativeWaveform] — so the bar is never a bare line.
 */
private fun DrawScope.drawWaveformBars(
    values: FloatArray,
    playedX: Float,
    cy: Float,
    played: Color,
    resting: Color,
    scale: Float,
    scrubber: ScrubberGeometry,
) {
    val pitch = scrubber.barPitch.toPx() * scale
    val count = max(2, (size.width / pitch).toInt())
    val bars = sampleWaveform(values, count)
    val width = scrubber.barWidth.toPx() * scale
    val half = scrubber.barHeight.toPx() * scale
    // Centre the strip so the bars do not end on a clipped half-gap.
    val start = (size.width - count * pitch) / 2f + pitch / 2f
    for (index in 0 until count) {
        val x = start + index * pitch
        val reach = half * bars[index]
        drawLine(
            color = if (x <= playedX) played else resting,
            start = Offset(x, cy - reach),
            end = Offset(x, cy + reach),
            strokeWidth = width,
            cap = StrokeCap.Round,
        )
    }
}
