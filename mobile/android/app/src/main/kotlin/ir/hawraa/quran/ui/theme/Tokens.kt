package ir.hawraa.quran.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Geometry tokens from DESING.md §4–5. One strictly-increasing radius ramp, a 4 dp
 * spacing rhythm, and named aliases (tile, segment, panel) so components never
 * hard-code a corner.
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
    val badge: Dp get() = sm
    val tile: Dp get() = sm
    val segment: Dp get() = lg
    val panel: Dp get() = card
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

@Immutable
data class Tiles(
    val sm: Dp = 32.dp,
    val md: Dp = 40.dp,
    val lg: Dp = 48.dp,
    val xl: Dp = 56.dp,
    val hero: Dp = 76.dp,
    /** Size at which an identity tile switches to the hero numeral (twice [hero]). */
    val numeralHero: Dp = 152.dp,
)

@Immutable
data class Controls(
    val sm: Dp = 36.dp,
    val md: Dp = 40.dp,
    val lg: Dp = 44.dp,
    val xl: Dp = 52.dp,
    val play: Dp = 64.dp,
    val spinner: Dp = 20.dp,
    val spinnerStroke: Dp = 2.dp,
)

@Immutable
data class Bars(val thin: Dp = 4.dp, val thick: Dp = 6.dp, val seekerTrack: Dp = 2.dp, val seekerThumb: Dp = 12.dp)

@Immutable
data class IconSizes(val xs: Dp = 13.dp, val sm: Dp = 15.dp, val md: Dp = 18.dp, val lg: Dp = 22.dp, val xl: Dp = 26.dp, val hero: Dp = 44.dp)

@Immutable
data class DockGeometry(
    val playerHeight: Dp = 58.dp,
    val navHeight: Dp = 58.dp,
    val indicator: Dp = 40.dp,
    val icon: Dp = 22.dp,
    val iconActive: Dp = 20.dp,
    val touch: Dp = 44.dp,
    val margin: Dp = 10.dp,
    val inset: Dp = 12.dp,
) {
    /** Screens owe the dock this inset (sized for the dock at its tallest), never a literal. */
    val dockInset: Dp get() = playerHeight + navHeight + margin * 2 + inset

    /** Collapsed nav-only dock clearance. */
    val navInset: Dp get() = navHeight + margin * 2 + inset
}

/** Frost geometry (DESING.md §6 / plan 02). Shell chrome only. */
@Immutable
data class FrostGeometry(
    /**
     * Chrome-pane blur. 22 dp at 0.5x sample scale dissolves scrolling rows into
     * luminous colour washes without hitting Skia/HWUI kernel limits or muddying
     * the glass into a flat tint.
     */
    val blur: Dp = 22.dp,
    /**
     * The full-screen modal-backdrop blur (shell content hidden behind a sheet or the
     * player). Chrome panes — header, dock, mini-player — use [blur] instead.
     */
    val backdrop: Dp = 18.dp,
    /** Maximum height of the frosted pane's inner top catchlight. */
    val sheenCap: Dp = 28.dp,
    val noise: Float = 0.12f,
)

/** Floating header geometry. Height is a floor; a subtitle may grow it. */
@Immutable
data class HeaderGeometry(
    val margin: Dp = 10.dp,
    val minHeight: Dp = 72.dp,
    val contentPadding: Dp = 20.dp,
)

/** Glass pane anatomy (DESING.md §6): the top rim and contour hairline. */
@Immutable
data class GlassGeometry(
    val rimHeight: Dp = 3.dp,
    val borderWidth: Dp = 1.dp,
    val hairline: Dp = 1.dp,
)

/** The wave language (DESING.md §8). Shared by the dock seam, sheets and progress. */
@Immutable
data class WaveGeometry(
    val hairlineHeight: Dp = 6.dp,
    val hairlineAmplitude: Dp = 1.2.dp,
    val hairlineWavelength: Dp = 48.dp,
    val hairlineTaper: Dp = 12.dp,
    val hairlineStroke: Dp = 1.dp,
    val progressHeight: Dp = 16.dp,
    val progressWavelength: Dp = 40.dp,
    val progressAmplitude: Dp = 1.9.dp,
    val progressRestingAmplitudeScale: Float = 0.45f,
    val progressRestingWavelengthScale: Float = 1.7f,
    val progressRestingVariation: Float = 0.35f,
    val progressRestingTaper: Dp = 8.dp,
    val progressRestingStroke: Dp = 1.5.dp,
    val progressActiveVariation: Float = 0.2f,
    val progressActiveTaper: Dp = 5.dp,
    val progressActiveStroke: Dp = 2.dp,
    val progressHeadGlowRadius: Dp = 5.dp,
    val progressHeadRadius: Dp = 2.6.dp,
    val wakeLength: Dp = 54.dp,
    val wakeHeight: Dp = 10.dp,
    val wakeSpread: Dp = 2.2.dp,
    val wakeAmplitude: Dp = 1.6.dp,
    val wakeWavelength: Dp = 30.dp,
    val wakeDashLong: Dp = 9.dp,
    val wakeDashShort: Dp = 0.6.dp,
    val wakeGapTight: Dp = 2.6.dp,
    val wakeGapWide: Dp = 6.5.dp,
    val wakeThin: Dp = 1.dp,
    val wakeThick: Dp = 1.5.dp,
    val wakePhaseSpan: Float = 7f,
)

/** The player identity tile (DESIGN.md §10.5): hero tile shadow, inner inset and size. */
@Immutable
data class PlayerGeometry(
    val heroShadow: Dp = 24.dp,
    val heroPadding: Dp = 5.dp,
    /** The full-screen player's identity tile: artwork scale, but a numeral instead of artwork. */
    val heroSize: Dp = 180.dp,
)

/**
 * The wave scrubber (DESIGN.md §10.6). Every number is Pejvak's: the same track is
 * drawn at `scale = 1f` in the player and [miniScale] in the dock, so the two can
 * never drift apart.
 */
@Immutable
data class ScrubberGeometry(
    val waveWavelength: Dp = 64.dp,
    val waveAmplitude: Dp = 3.dp,
    val waveStroke: Dp = 3.dp,
    val headDot: Dp = 5.5.dp,
    val headHalo: Dp = 9.dp,
    val headHaloAlpha: Float = 0.14f,
    val miniScale: Float = 0.55f,
    val miniHeight: Dp = 14.dp,
    val barWidth: Dp = 1.8.dp,
    val barPitch: Dp = 4.4.dp,
    val barHeight: Dp = 9.dp,
    val playedAlpha: Float = 0.92f,
    val restingAlpha: Float = 0.17f,
    val waitingAlpha: Float = 0.55f,
    val waitingTaper: Dp = 24.dp,
    val waitingVariation: Float = 0.25f,
    val activeTaper: Dp = 12.dp,
    val playedVariation: Float = 0.2f,
    val wakeLength: Dp = 190.dp,
    val wakeSpread: Dp = 10.5.dp,
    val wakePhase: Float = 1.5f,
    val wakeAlpha: Float = 0.62f,
    val wakeAmplitudeScale: Float = 0.35f,
    val wakeSecondScale: Float = 0.8f,
    val wakeSecondPhaseScale: Float = 1.45f,
    val markerDot: Dp = 3.dp,
    val markerHitSlop: Dp = 14.dp,
)

/** The transport bubbles and status line (DESIGN.md §10.6). */
@Immutable
data class TransportGeometry(
    val secondarySize: Dp = 48.dp,
    val playSize: Dp = Controls().play,
    val spinnerStroke: Dp = 2.dp,
    val spinnerScale: Float = 0.34f,
    val statusSpinnerSize: Dp = 14.dp,
    val statusSpinnerStroke: Dp = 1.5.dp,
    val statusSpacing: Dp = 8.dp,
    val statusLongWaitMs: Long = 8_000L,
)

/** The glass sheet's entry/exit motion and drag handle. */
@Immutable
data class SheetGeometry(
    val enterRise: Dp = 12.dp,
    val enterSway: Dp = 7.dp,
    val enterScale: Float = 0.018f,
    val handleWidth: Dp = 36.dp,
    val handleHeight: Dp = 4.dp,
    val crestTravel: Float = 5.2f,
    /** How far the full player must be pulled down before it dismisses. */
    val pullDismiss: Dp = 105.dp,
    /**
     * The pull is normalised over at least this much travel, so a short screen does not
     * fling the player away on what is really a small nudge.
     */
    val pullFloor: Dp = 420.dp,
    /** Downward release speed that completes a pull dismiss. */
    val pullFling: Dp = 520.dp,
    /** Progress span over which the floating card's rim fades as the sheet opens. */
    val rimFade: Float = 0.22f,
    /** Maximum frost scrim where the backdrop cannot blur (API 29–30). */
    val scrimMax: Float = 0.30f,
    /** Opaque reading canvas opacity at full screen. */
    val canvasFade: Float = 0.68f,
    /** Collapsed card shadow scale, relative to `spacing.sm`. */
    val shadowScale: Float = 1.15f,
    /** Ambient/spot shadow opacity while the card floats. */
    val shadowAmbient: Float = 0.22f,
    val shadowSpot: Float = 0.28f,
    /** The content's transform origin (fraction of the viewport) while it scales in. */
    val contentOriginX: Float = 0.5f,
    val contentOriginY: Float = 0.14f,
)

/**
 * Navigation motion (DESIGN.md §9 / plan 02 Task 7): the page-transition channels, the
 * ambient-canvas surge and the WaveSweep crest. Durations and amplitudes live here so no
 * navigation composable carries a raw number.
 */
@Immutable
data class NavMotionGeometry(
    val ambientSurgeMs: Int = 760,
    val ambientShiftX: Float = 0.06f,
    val ambientShiftY: Float = 0.055f,
    /** Ambient-canvas drift while crossing tabs; mirrored by the shell in RTL. */
    val canvasTabShift: Float = 1f,
    val canvasTabY: Float = -0.25f,
    val canvasPushX: Float = 0.25f,
    val canvasPushY: Float = -1f,
    val canvasPopX: Float = -0.25f,
    val canvasPopY: Float = 1f,
    /** Tab switch: lateral glide + buoyant crest. */
    val tabFadeInMs: Int = 240,
    val tabEnterMs: Int = 380,
    val tabEnterHorizontalDivisor: Int = 8,
    val tabEnterVerticalDivisor: Int = 48,
    val tabEnterScale: Float = 0.978f,
    val tabFadeOutMs: Int = 200,
    val tabExitMs: Int = 320,
    val tabExitHorizontalDivisor: Int = 10,
    val tabExitScale: Float = 0.975f,
    /** Push: rise into clarity while the parent recedes. */
    val pushFadeInMs: Int = 260,
    val pushEnterMs: Int = 420,
    val pushEnterVerticalDivisor: Int = 10,
    val pushEnterScale: Float = 0.945f,
    val pushFadeOutMs: Int = 210,
    val pushExitMs: Int = 360,
    val pushExitVerticalDivisor: Int = 22,
    val pushExitScale: Float = 0.96f,
    /** Pop: mirror the dive on the way back. */
    val popFadeInMs: Int = 260,
    val popEnterMs: Int = 380,
    val popEnterVerticalDivisor: Int = 22,
    val popEnterScale: Float = 0.96f,
    val popFadeOutMs: Int = 210,
    val popExitMs: Int = 360,
    val popExitVerticalDivisor: Int = 9,
    val popExitScale: Float = 0.95f,
)

/** The WaveSweep crest (DESIGN.md §9): aqua band, dual crests, swimmer and wake. */
@Immutable
data class WaveSweepGeometry(
    val durationMs: Int = 660,
    val wavelength: Dp = 88.dp,
    val bandHalf: Dp = 58.dp,
    val bandCyanAlpha: Float = 0.065f,
    val bandTealAlpha: Float = 0.045f,
    /** Tab drift: lateral swim at mid-height. */
    val tabCenterFraction: Float = 0.48f,
    val tabCenterOffset: Dp = 18.dp,
    val tabAmplitude: Dp = 8.dp,
    val tabPhaseSpan: Float = 4.2f,
    val tabEchoLag: Dp = 22.dp,
    /** Push: swim up from the bottom. */
    val pushStartX: Float = 0.12f,
    val pushSpanX: Float = 0.76f,
    val pushStartY: Float = 0.88f,
    val pushSpanY: Float = 0.76f,
    val pushAmplitude: Dp = 7.dp,
    val pushPhaseSpan: Float = 3.6f,
    val pushEchoLag: Dp = 24.dp,
    /** Pop: swim down from the top. */
    val popStartX: Float = 0.88f,
    val popSpanX: Float = 0.76f,
    val popStartY: Float = 0.14f,
    val popSpanY: Float = 0.74f,
    val popAmplitude: Dp = 6.5.dp,
    val popPhaseSpan: Float = 3.4f,
    val popEchoLag: Dp = 24.dp,
    val crestAlpha: Float = 0.28f,
    val echoCrestAlpha: Float = 0.22f,
    val crestStroke: Dp = 1.6.dp,
    val echoCrestStroke: Dp = 1.1.dp,
    val crestTaper: Dp = 28.dp,
    val crestVariation: Float = 0.35f,
    val echoAmplitudeScale: Float = 0.62f,
    val wakeSpread: Dp = 8.5.dp,
    val wakePhaseShift: Float = 0.85f,
    val wakeLength: Float = 128f,
    val wakeEchoLength: Float = 112f,
    val wakeAlpha: Float = 0.34f,
    val wakeEchoAlpha: Float = 0.28f,
    val wakeAmplitudeScale: Float = 0.42f,
    val haloRadius: Dp = 8.dp,
    val haloAlpha: Float = 0.18f,
    val tailLength: Dp = 11.dp,
    val tailStroke: Dp = 2.4.dp,
    val tailAlpha: Float = 0.55f,
    val headRadius: Dp = 2.8.dp,
    val headAlpha: Float = 0.86f,
)

/** The player body's own motion (DESIGN.md §10.12): hero entrance and the playing pulse. */
@Immutable
data class PlayerMorph(
    /** Scale the identity tile enters at on a track change, settling to 1. */
    val heroEntranceScale: Float = 0.96f,
    /** Bar count of the header's playing equaliser. */
    val pulseBars: Int = 4,
    /** Phase the equaliser holds under reduced motion. */
    val pulseStaticPhase: Float = 0.5f,
    /** Minimum bar height as a fraction of the control. */
    val pulseMin: Float = 0.3f,
    /** Resting bar height while nothing is playing. */
    val pulseIdle: Float = 0.28f,
)

/** Skeleton loading rows. */
@Immutable
data class SkeletonGeometry(
    val lineHeight: Dp = 12.dp,
    val lineHeightSmall: Dp = 10.dp,
    val lineWidth: Float = 0.6f,
    val lineWidthSmall: Float = 0.4f,
)

@Immutable
data class HawraaGeometry(
    val radius: Radius = Radius(),
    val spacing: Spacing = Spacing(),
    val tiles: Tiles = Tiles(),
    val controls: Controls = Controls(),
    val bars: Bars = Bars(),
    val icons: IconSizes = IconSizes(),
    val dock: DockGeometry = DockGeometry(),
    val frost: FrostGeometry = FrostGeometry(),
    val header: HeaderGeometry = HeaderGeometry(),
    val glass: GlassGeometry = GlassGeometry(),
    val wave: WaveGeometry = WaveGeometry(),
    val player: PlayerGeometry = PlayerGeometry(),
    val scrubber: ScrubberGeometry = ScrubberGeometry(),
    val transport: TransportGeometry = TransportGeometry(),
    val sheet: SheetGeometry = SheetGeometry(),
    val playerMorph: PlayerMorph = PlayerMorph(),
    val skeleton: SkeletonGeometry = SkeletonGeometry(),
    val navMotion: NavMotionGeometry = NavMotionGeometry(),
    val waveSweep: WaveSweepGeometry = WaveSweepGeometry(),
    val maxContentWidth: Dp = 760.dp,
) {
    val touchTarget: Dp get() = dock.touch
    val screenPadding: Dp get() = header.contentPadding
}

val LocalHawraaGeometry = staticCompositionLocalOf { HawraaGeometry() }

/** Corner scales with tile size (DESING.md §5). */
fun tileRadius(size: Dp): Dp = when {
    size <= 32.dp -> 8.dp
    size <= 40.dp -> 12.dp
    size <= 48.dp -> 12.dp
    size <= 56.dp -> 16.dp
    else -> 20.dp
}
