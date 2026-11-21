package ir.hawraa.quran.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Geometry tokens (`docs/DESIGN.md` §6). One strictly-increasing radius ramp, a 4 dp spacing
 * rhythm, and semantic aliases — **never hard-code a corner or a clearance**.
 */
@Immutable
data class Radius(
    val xs: Dp = 8.dp,
    val sm: Dp = 12.dp,
    val md: Dp = 16.dp,
    val lg: Dp = 20.dp,
    val card: Dp = 28.dp,
    val xl: Dp = 32.dp,
    val xxl: Dp = 40.dp,
    val pill: Dp = 999.dp,
) {
    /** Floating chrome: dock, action box, mini-player. */
    val panel: Dp get() = card
    val tile: Dp get() = sm
    val badge: Dp get() = xs
}

@Immutable
data class Spacing(
    val none: Dp = 0.dp,
    val xxs: Dp = 2.dp,
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 20.dp,
    val xxl: Dp = 24.dp,
    val xxxl: Dp = 32.dp,
    val huge: Dp = 44.dp,
    val giant: Dp = 64.dp,
)

/** Circular control sizes. Touch targets pad out to [HawraaGeometry.touchTarget] regardless. */
@Immutable
data class Controls(
    val sm: Dp = 32.dp,
    val md: Dp = 36.dp,
    val lg: Dp = 40.dp,
    val xl: Dp = 44.dp,
    /** The one filled bubble — the primary action. */
    val play: Dp = 52.dp,
)

@Immutable
data class IconSizes(
    val xs: Dp = 13.dp,
    val sm: Dp = 15.dp,
    val md: Dp = 18.dp,
    val lg: Dp = 22.dp,
    val xl: Dp = 26.dp,
    val hero: Dp = 44.dp,
)

/**
 * The dock's geometry. These numbers are a contract (`docs/DESIGN.md` §6.3), not suggestions.
 *
 * The dock is **one panel with two halves**, and both halves are the same height so the seam
 * divides the panel in half rather than cutting a tall nav off a short player.
 */
@Immutable
data class DockGeometry(
    /** Height of the now-playing row. Equal to [navHeight] on purpose. */
    val playerHeight: Dp = 58.dp,
    val navHeight: Dp = 58.dp,
    /** The selected destination's disc. A true circle, not a lozenge. */
    val indicator: Dp = 40.dp,
    /** The resting glyph. */
    val icon: Dp = 22.dp,
    /** The selected glyph, knocked out of the disc — half the disc, rhyming with the play bubble. */
    val iconActive: Dp = 20.dp,
    /** Gap from the safe-area edge to the dock's bottom. */
    val margin: Dp = 10.dp,
    /** Horizontal float. */
    val inset: Dp = 12.dp,
    /** Room below the dock that scrolling content needs so nothing rests against it. */
    val clearance: Dp = 20.dp,
) {
    /**
     * How much bottom padding a screen owes the dock.
     *
     * Sized for the dock **at its tallest** — player plus navigation — because a screen cannot
     * know whether something will start playing while it is open, and reserving for the short
     * case would let the last verse hide behind the now-playing row.
     */
    val dockInset: Dp get() = margin + playerHeight + navHeight + clearance

    /** The same clearance for the collapsed (nav-only) dock. Used only where space is critical. */
    val navInset: Dp get() = margin + navHeight + clearance
}

@Immutable
data class HawraaGeometry(
    val radius: Radius = Radius(),
    val spacing: Spacing = Spacing(),
    val controls: Controls = Controls(),
    val icons: IconSizes = IconSizes(),
    val dock: DockGeometry = DockGeometry(),
    /** Every interactive target is at least this big, even when the glyph is smaller. */
    val touchTarget: Dp = 44.dp,
    val maxContentWidth: Dp = 760.dp,
    /** Horizontal padding of a screen's content. */
    val screenPadding: Dp = 20.dp,
)

val LocalHawraaGeometry = staticCompositionLocalOf { HawraaGeometry() }
