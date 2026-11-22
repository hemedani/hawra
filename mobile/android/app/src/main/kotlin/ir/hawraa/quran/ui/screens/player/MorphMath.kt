package ir.hawraa.quran.ui.screens.player

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Dp
import kotlin.math.pow

/**
 * Pure math behind the mini-player ⇄ now-playing morph.
 *
 * `t` is normalized expand progress: `0f` is the dock's capsule, `1f` the full-screen
 * sheet. Nothing here touches Compose state — every function is deterministic and
 * unit-testable in isolation.
 */

/** How fast the capsule corner releases: quick at first, easing into the square edge. */
private const val CORNER_EXPONENT = 1.6f

/** Progress at which the full content has faded in. */
private const val CONTENT_FADE_START = 0.34f
private const val CONTENT_FADE_END = 0.90f

/** Progress at which the dock row has faded out. */
private const val MINI_FADE_START = 0.12f
private const val MINI_FADE_END = 0.52f

/** Content scale at the collapsed and expanded ends. */
private const val CONTENT_SCALE_COLLAPSED = 0.84f
private const val CONTENT_SCALE_EXPANDED = 1f

/** Dock row scale at the collapsed and expanded ends. */
private const val MINI_SCALE_COLLAPSED = 1f
private const val MINI_SCALE_EXPANDED = 0.94f

/** Straight-line interpolation for the morph's scalar channels. */
internal fun morphLerp(start: Float, stop: Float, t: Float): Float =
    start + (stop - start) * t

/** Straight-line interpolation for the morph's Dp channels (radius, shadow). */
internal fun morphLerp(start: Dp, stop: Dp, t: Float): Dp =
    start + (stop - start) * t

/**
 * The card corner at [t]: the dock's rounded capsule when collapsed, square when full.
 *
 * The exponent makes the corner let go quickly at first and ease into the full-screen edge,
 * which reads as the capsule *unfolding* rather than a rectangle merely growing.
 */
internal fun morphCorner(mini: Dp, t: Float): Dp =
    mini * (1f - t.coerceIn(0f, 1f).pow(CORNER_EXPONENT))

/** Fade a channel between two progress thresholds, clamped to `0..1`. */
internal fun morphAlpha(t: Float, start: Float, end: Float): Float {
    if (end <= start) return if (t >= end) 1f else 0f
    return ((t - start) / (end - start)).coerceIn(0f, 1f)
}

/** Full-sheet content opacity as the card expands. */
internal fun morphContentAlpha(t: Float): Float =
    morphAlpha(t, start = CONTENT_FADE_START, end = CONTENT_FADE_END)

/** Full-sheet content scale as the card expands. */
internal fun morphContentScale(t: Float): Float =
    morphLerp(CONTENT_SCALE_COLLAPSED, CONTENT_SCALE_EXPANDED, t)

/** Dock row opacity: the inverse of the content fade, so the two cross-fade cleanly. */
internal fun morphMiniRowAlpha(t: Float): Float =
    1f - morphAlpha(t, start = MINI_FADE_START, end = MINI_FADE_END)

/** Dock row scale, easing toward [MINI_SCALE_EXPANDED] as it fades out. */
internal fun morphMiniRowScale(t: Float): Float =
    morphLerp(MINI_SCALE_COLLAPSED, MINI_SCALE_EXPANDED, (t / MINI_FADE_END).coerceIn(0f, 1f))

/** Interpolate every edge of [from] toward [to] by [t]. */
internal fun lerpRect(from: Rect, to: Rect, t: Float): Rect = Rect(
    left = morphLerp(from.left, to.left, t),
    top = morphLerp(from.top, to.top, t),
    right = morphLerp(from.right, to.right, t),
    bottom = morphLerp(from.bottom, to.bottom, t),
)
