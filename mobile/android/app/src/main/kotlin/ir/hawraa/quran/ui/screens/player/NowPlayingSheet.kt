package ir.hawraa.quran.ui.screens.player

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.zIndex
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.hawraa.quran.R
import ir.hawraa.quran.domain.quran.toPersianDigits
import ir.hawraa.quran.ui.appLocale
import ir.hawraa.quran.ui.components.MiniPlayerRow
import ir.hawraa.quran.ui.components.NowPlayingUi
import ir.hawraa.quran.ui.components.PaletteTile
import ir.hawraa.quran.ui.recitation.NowPlaying
import ir.hawraa.quran.ui.recitation.NowPlayingStore
import ir.hawraa.quran.ui.theme.HawraaSpring
import ir.hawraa.quran.ui.theme.LocalGlassColors
import ir.hawraa.quran.ui.theme.LocalHawraaGeometry
import ir.hawraa.quran.ui.theme.tileRadius
import ir.hawraa.quran.ui.theme.useMotionEnabled
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * The full-screen player as a morphing surface (`docs/DESIGN.md` §10.12).
 *
 * The sheet is owned by the shell but says nothing about navigation: it reads [NowPlayingStore],
 * draws the player from the dock's measured rect to full screen, and asks the store to close.
 * That keeps the reader's engine and session ownership untouched while making the transition
 * continuous rather than a second surface appearing over the dock.
 *
 * ## The morph
 *
 * A single normalised progress `t` drives every visual: the card bounds interpolate from
 * [NowPlayingStore.miniPlayerRect] to the viewport, the corner shrinks with `1 - t^1.6`, the
 * dock's glass cross-fades into the opaque reading canvas, and the hero tile flies between the
 * dock tile and its measured place in [PlayerContent]. Content and the dock row cross-fade on
 * separate curves so neither is ever drawn on top of the other for long.
 *
 * ## Motion
 *
 * Opening and closing are springs. A downward pull moves the same `t`, and the release either
 * settles back to open or dismisses with the finger's velocity. With reduced motion every
 * animation snaps to its end value; the sheet never withholds content to make a transition work.
 *
 * [onExpandProgress] streams `t` to the shell so it can lift the backdrop blur as the player
 * opens and lower it as the player folds back onto the dock. The player itself stays outside
 * that blur — it is the surface the blur exists to reveal.
 */
@Composable
fun NowPlayingSheet(
    store: NowPlayingStore,
    backdropBlur: Float,
    onExpandProgress: (Float) -> Unit = {},
    /**
     * Pins the morph at a fixed progress for previews and screenshot tests.
     *
     * Production callers leave this null, so the sheet follows its open/close springs and the
     * reader's pull. A non-null value skips every animation and the mounted gate, which lets a
     * test capture a deterministic frame at any `t` (0 = mini capsule, 0.5 = mid-morph,
     * 1 = full screen). Never set this from app code.
     */
    progressOverride: Float? = null,
    modifier: Modifier = Modifier,
    /** The dock's wave, so the capsule keeps it while the sheet folds. */
    miniProgress: StateFlow<Float>? = null,
) {
    val glass = LocalGlassColors.current
    val geometry = LocalHawraaGeometry.current
    val density = LocalDensity.current
    val motion = useMotionEnabled()
    val scope = rememberCoroutineScope()
    val onExpandUpdated by rememberUpdatedState(onExpandProgress)

    val expanded by store.expanded.collectAsStateWithLifecycle()
    val player by store.state.collectAsStateWithLifecycle()
    val targetRect by store.miniPlayerRect.collectAsStateWithLifecycle()

    val expand = remember { Animatable(if (expanded) 1f else 0f) }
    var interactiveExpand by remember { mutableStateOf<Float?>(null) }
    var animating by remember { mutableStateOf(false) }
    var isDismissing by remember { mutableStateOf(false) }
    var pullSettled by remember { mutableStateOf(false) }
    var settleJob by remember { mutableStateOf<Job?>(null) }

    val openSpring = HawraaSpring.playerOpen
    val closeSpring = HawraaSpring.playerClose

    val pinnedProgress = progressOverride?.coerceIn(0f, 1f)
    val mounted = pinnedProgress != null || expanded || animating

    // The current morph progress as a *pull*, so the animated channels can read it from
    // `graphicsLayer`/`offset`/pointer lambdas. Reading `expand.value` in composition would
    // recompose the sheet (and everything it passes down) on every frame; reading it at the
    // layer/layout pass only invalidates that layer.
    val latestPinned = rememberUpdatedState(pinnedProgress)
    val currentT: () -> Float = remember {
        {
            val pinned = latestPinned.value
            (pinned ?: interactiveExpand ?: expand.value).coerceIn(0f, 1f)
        }
    }

    // Drive the morph on open/close. Closing is owned by [requestDismiss] (the pull or the close
    // button); there is no reopen path while it folds, so `expanded` only has to settle the
    // spring toward the end it names.
    LaunchedEffect(expanded, motion, pinnedProgress) {
        if (pinnedProgress != null) return@LaunchedEffect
        if (expanded) {
            settleJob?.cancel()
            settleJob = null
            interactiveExpand = null
            isDismissing = false
            pullSettled = false
            animating = true
            try {
                if (motion) {
                    if (expand.value <= 0.005f) expand.snapTo(0f)
                    expand.animateTo(1f, openSpring)
                } else {
                    expand.snapTo(1f)
                }
            } finally {
                animating = false
            }
        } else if (!isDismissing && (expand.value > 0f || interactiveExpand != null)) {
            settleJob?.cancel()
            settleJob = null
            animating = true
            isDismissing = true
            try {
                interactiveExpand?.let {
                    expand.snapTo(it)
                    interactiveExpand = null
                }
                if (motion) {
                    expand.animateTo(0f, closeSpring)
                } else {
                    expand.snapTo(0f)
                }
            } finally {
                animating = false
                isDismissing = false
            }
        }
    }

    // Stream live progress to the shell. A pull is reported immediately, not only on release.
    LaunchedEffect(mounted, pinnedProgress) {
        if (!mounted) {
            onExpandUpdated(0f)
            return@LaunchedEffect
        }
        if (pinnedProgress != null) {
            onExpandUpdated(pinnedProgress)
            return@LaunchedEffect
        }
        snapshotFlow { (interactiveExpand ?: expand.value).coerceIn(0f, 1f) }
            .collect { progress -> onExpandUpdated(progress) }
    }
    DisposableEffect(Unit) {
        onDispose { onExpandUpdated(0f) }
    }

    fun requestDismiss(initialVelocity: Float = 0f) {
        if (isDismissing) return
        settleJob?.cancel()
        animating = true
        isDismissing = true
        settleJob = scope.launch {
            try {
                interactiveExpand?.let {
                    expand.snapTo(it)
                    interactiveExpand = null
                }
                if (motion) {
                    expand.animateTo(0f, closeSpring, initialVelocity = initialVelocity)
                } else {
                    expand.snapTo(0f)
                }
                store.close()
            } finally {
                animating = false
                isDismissing = false
            }
        }
    }

    BackHandler(enabled = expanded && !isDismissing) {
        requestDismiss()
    }

    if (!mounted) return
    val ui = player ?: return

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .testTag("now-playing-sheet"),
    ) {
        val maxW = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val maxH = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val maxWDp = with(density) { maxW.toDp() }
        val maxHDp = with(density) { maxH.toDp() }

        val marginPx = with(density) { geometry.dock.margin.toPx() }
        val navBottomPx = with(density) {
            WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding().toPx()
        }
        val rowHeightPx = with(density) { geometry.dock.playerHeight.toPx() }
        val fallbackBottom = (maxH - navBottomPx - marginPx)
            .coerceAtLeast(rowHeightPx + marginPx)
        val miniRect = targetRect ?: Rect(
            left = marginPx,
            top = (fallbackBottom - rowHeightPx).coerceAtLeast(0f),
            right = (maxW - marginPx).coerceAtLeast(marginPx + 1f),
            bottom = fallbackBottom,
        )

        val pullTravelPx = miniRect.top.coerceAtLeast(
            with(density) { geometry.sheet.pullFloor.toPx() },
        )
        val dismissDistPx = with(density) { geometry.sheet.pullDismiss.toPx() }
        val dismissDistFraction = (dismissDistPx / pullTravelPx).coerceIn(0.08f, 0.45f)
        val flingVelocityThreshold = with(density) { geometry.sheet.pullFling.toPx() }

        fun finishPull(velocityY: Float) {
            if (pullSettled) return
            val from = interactiveExpand ?: return
            pullSettled = true
            settleJob?.cancel()
            if (from >= 0.999f && velocityY <= flingVelocityThreshold) {
                interactiveExpand = null
                settleJob = scope.launch { expand.snapTo(1f) }
                return
            }
            val shouldDismiss = from < (1f - dismissDistFraction) ||
                (velocityY > flingVelocityThreshold && from < 0.985f)
            if (shouldDismiss) {
                val initialV = -(velocityY / pullTravelPx).coerceAtLeast(0f)
                requestDismiss(initialVelocity = initialV)
            } else {
                settleJob = scope.launch {
                    expand.snapTo(from)
                    interactiveExpand = null
                    if (motion) {
                        expand.animateTo(1f, HawraaSpring.sheet)
                    } else {
                        expand.snapTo(1f)
                    }
                }
            }
        }

        // Stable entry points for [PlayerContent]. The sheet recomposes while it morphs (its card
        // bounds and the flying artwork are composition values), but these callbacks must not:
        // a new lambda each frame would recompose the whole player body with it.
        val latestRequestDismiss by rememberUpdatedState<() -> Unit> { requestDismiss() }
        val latestFinishPull by rememberUpdatedState<(Float) -> Unit> { finishPull(it) }
        val onCloseStable = remember { { latestRequestDismiss() } }
        val onPullDownStable = remember(pullTravelPx) {
            { dy: Float ->
                settleJob?.cancel()
                settleJob = null
                isDismissing = false
                pullSettled = false
                val current = interactiveExpand ?: expand.value
                interactiveExpand = (current - dy / pullTravelPx).coerceIn(0f, 1f)
            }
        }
        val onPullEndStable = remember { { velocityY: Float -> latestFinishPull(velocityY) } }
        val isPulledStable = remember { { (interactiveExpand ?: expand.value) < 0.999f } }

        // The animated channels read `currentT` at the layer/pointer pass, not in composition.
        // Both modifiers are remembered so their identity survives the sheet's own recompositions;
        // [PlayerContent] can then be skipped frame after frame.
        val contentModifier = remember {
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val t = currentT()
                    alpha = morphContentAlpha(t)
                    val scale = morphContentScale(t)
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin(
                        geometry.sheet.contentOriginX,
                        geometry.sheet.contentOriginY,
                    )
                }
                .consumeTouchesWhile { morphContentAlpha(currentT()) < 0.99f }
        }
        val miniRowModifier = remember {
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val t = currentT()
                    alpha = morphMiniRowAlpha(t)
                    val scale = morphMiniRowScale(t)
                    scaleX = scale
                    scaleY = scale
                }
                .consumeTouchesWhile { morphMiniRowAlpha(currentT()) < 0.9f }
        }

        val t = (pinnedProgress ?: interactiveExpand ?: expand.value).coerceIn(0f, 1f)
        val cardRect = lerpRect(miniRect, Rect(0f, 0f, maxW, maxH), t)
        val cardW = cardRect.width.coerceAtLeast(1f)
        val cardH = cardRect.height.coerceAtLeast(1f)
        val cardWDp = with(density) { cardW.toDp() }
        val cardHDp = with(density) { cardH.toDp() }

        val cardShape = RoundedCornerShape(morphCorner(geometry.radius.xl, t))
        val rimAlpha = ((1f - t) / geometry.sheet.rimFade).coerceIn(0f, 1f)
        val cardShadowDp = geometry.spacing.sm *
            ((1f - t) * geometry.sheet.shadowScale).coerceIn(0f, 1f)

        var heroRectInRoot by remember { mutableStateOf<Rect?>(null) }
        val onHeroPositioned = remember {
            { coords: LayoutCoordinates -> heroRectInRoot = coords.toRootRectOrNull() }
        }
        val canFlyArt = heroRectInRoot != null && t > 0.005f && t < 0.995f
        val tileFadeAlpha = if (canFlyArt) 0f else 1f

        val cardElevationModifier = if (cardShadowDp > Dp.Hairline) {
            Modifier.shadow(
                elevation = cardShadowDp,
                shape = cardShape,
                clip = false,
                ambientColor = glass.shadow.copy(alpha = geometry.sheet.shadowAmbient),
                spotColor = glass.shadow.copy(alpha = geometry.sheet.shadowSpot),
            )
        } else {
            Modifier
        }

        val blockerInteraction = remember { MutableInteractionSource() }

        // API 29–30 has no RenderEffect blur: the shell's backdrop reads as the frost scrim
        // instead, and the sheet fades it in with the same progress that would have lifted blur.
        val scrimAlpha = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            (backdropBlur / geometry.frost.backdrop.value).coerceIn(0f, 1f) * geometry.sheet.scrimMax
        } else {
            0f
        }
        if (scrimAlpha > 0f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(glass.frostFallback.copy(alpha = scrimAlpha)),
            )
        }

        // Full screen at t = 1, the dock's own capsule at t = 0. One surface throughout.
        Box(
            modifier = Modifier
                .offset {
                    val rect = lerpRect(miniRect, Rect(0f, 0f, maxW, maxH), currentT())
                    IntOffset(rect.left.roundToInt(), rect.top.roundToInt())
                }
                .size(cardWDp, cardHDp)
                .then(cardElevationModifier)
                .clip(cardShape)
                .background(glass.canvas.copy(alpha = geometry.sheet.canvasFade * t))
                .background(glass.glassFill.copy(alpha = glass.glassFill.alpha * t))
                .background(glass.frostBase.copy(alpha = glass.frostBase.alpha * (1f - t)))
                .background(glass.frostScrim.copy(alpha = glass.frostScrim.alpha * (1f - t)))
                .background(glass.frostSheen.copy(alpha = glass.frostSheen.alpha * (1f - t)))
                // The open player is a full-screen surface: an unclaimed tap stops here rather
                // than leaking to the screen underneath. Released as the dismiss settles.
                .clickable(
                    interactionSource = blockerInteraction,
                    indication = null,
                    enabled = !isDismissing && t > 0.1f,
                    onClick = {},
                ),
        ) {
            // A fixed viewport keeps the controls from reflowing while the card shrinks.
            // Below the mini row while the capsule is still the live target, above it once the
            // row has handed off: only one of the two overlapping surfaces is hit-tested.
            Box(
                modifier = Modifier
                    .wrapContentSize(align = Alignment.TopCenter, unbounded = true)
                    .size(maxWDp, maxHDp)
                    .zIndex(if (t < HANDOFF_T) 0f else 1f),
            ) {
                PlayerContent(
                    ui = ui,
                    store = store,
                    onClose = onCloseStable,
                    modifier = contentModifier,
                    heroTileAlpha = tileFadeAlpha,
                    onHeroTilePositioned = onHeroPositioned,
                    onPullDown = onPullDownStable,
                    onPullEnd = onPullEndStable,
                    isPulled = isPulledStable,
                )
            }

            // Always composed: the row must not enter/leave composition as the morph crosses its
            // fade threshold. Its alpha (and the tap gate) is read from the layer pass instead.
            // It stays on top only while it is still the surface being handed off from.
            Box(
                modifier = miniRowModifier.zIndex(if (t < HANDOFF_T) 1f else 0f),
                contentAlignment = Alignment.Center,
            ) {
                MiniPlayerRow(
                    nowPlaying = ui.mini().toMiniUi(),
                    miniProgress = miniProgress,
                    onPlayPause = store::toggle,
                    onNext = store::next,
                    // There is no truthful reopen action in this transient copy; the dock owns
                    // the real one. A null target keeps the leading region non-clickable.
                    onExpand = null,
                    modifier = Modifier.height(geometry.dock.playerHeight),
                    tileAlpha = tileFadeAlpha,
                )
            }

            // The catchlight bevel only exists while the sheet is a floating card.
            if (rimAlpha > 0f) {
                Box(
                    Modifier
                        .matchParentSize()
                        .border(
                            width = geometry.glass.borderWidth,
                            brush = Brush.verticalGradient(
                                0f to glass.glassHighlight.copy(
                                    alpha = glass.glassHighlight.alpha * rimAlpha,
                                ),
                                0.32f to glass.glassBorder.copy(
                                    alpha = glass.glassBorder.alpha * 0.58f * rimAlpha,
                                ),
                                0.75f to glass.glassBorder.copy(
                                    alpha = glass.glassBorder.alpha * 0.24f * rimAlpha,
                                ),
                                1f to glass.glassBorder.copy(
                                    alpha = glass.glassBorder.alpha * 0.62f * rimAlpha,
                                ),
                            ),
                            shape = cardShape,
                        ),
                )
            }
        }

        // The shared artwork bridges HeroArtwork and the dock's PaletteTile so the identity
        // does not blink out and reappear between two surfaces.
        val heroRect = heroRectInRoot
        if (canFlyArt && heroRect != null) {
            val mdTilePx = with(density) { geometry.tiles.md.toPx() }
            val dockInsetPx = with(density) { geometry.dock.inset.toPx() }
            val miniArtLeft = miniRect.left + dockInsetPx
            val miniArtTop = miniRect.top + (miniRect.height - mdTilePx) / 2f

            val artLeft = morphLerp(miniArtLeft, heroRect.left, t)
            val artTop = morphLerp(miniArtTop, heroRect.top, t)
            val artSizePx = morphLerp(mdTilePx, heroRect.width, t).coerceAtLeast(1f)
            val artSizeDp = with(density) { artSizePx.toDp() }
            val artRadiusDp = morphLerp(
                tileRadius(geometry.tiles.md),
                geometry.radius.lg - geometry.spacing.xs,
                t,
            )
            val persian = LocalConfiguration.current.appLocale().language == "fa"
            val identity = if (persian) {
                ui.identityNumber.toPersianDigits()
            } else {
                ui.identityNumber.toString()
            }

            PaletteTile(
                seedKey = identity,
                number = identity,
                size = artSizeDp,
                radius = artRadiusDp,
                modifier = Modifier.offset {
                    IntOffset(artLeft.roundToInt(), artTop.roundToInt())
                },
            )
        }
    }
}

/**
 * Progress at which the mini row and the player content swap hit-test order.
 *
 * Both are full-card siblings, so only the topmost one can receive a pointer. Below this the row
 * is the live surface (the capsule); above it the player is. The visual cross-fade is already
 * nearly done here, so the swap cannot be seen.
 */
private const val HANDOFF_T = 0.5f

/**
 * Swallows every pointer event in the **Initial** pass while [blocked] is true.
 *
 * Used on surfaces that are still on screen but have faded past the point of being a truthful
 * target: the event is consumed before a child's clickable or the player's pull sees it, and the
 * predicate is read at event time so it follows the morph without recomposing the node.
 */
private fun Modifier.consumeTouchesWhile(blocked: () -> Boolean): Modifier =
    pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (blocked()) event.changes.forEach { it.consume() }
            }
        }
    }

private fun LayoutCoordinates.toRootRectOrNull(): Rect? {
    if (!isAttached) return null
    val width = size.width.toFloat()
    val height = size.height.toFloat()
    if (width <= 0f || height <= 0f) return null
    return Rect(positionInRoot(), Size(width, height))
}

/** The dock's script choice, kept local so the sheet and dock always agree. */
@Composable
private fun NowPlaying.toMiniUi(): NowPlayingUi {
    val persian = LocalConfiguration.current.appLocale().language == "fa"
    val reciter = if (persian) reciterNameFa else reciterNameEn
    val juz = juzNumber
    if (juz != null) {
        val number = if (persian) juz.toPersianDigits() else juz.toString()
        return NowPlayingUi(
            title = stringResource(R.string.dock_juz_title, number),
            subtitle = stringResource(R.string.dock_juz_subtitle, reciter),
            number = number,
            isPlaying = isPlaying,
            waiting = busy,
        )
    }
    return NowPlayingUi(
        title = if (persian) surahNameFa else surahNameEn,
        subtitle = stringResource(
            R.string.dock_now_playing,
            if (persian) ayah.toPersianDigits() else ayah.toString(),
            reciter,
        ),
        number = if (persian) surahNumber.toPersianDigits() else surahNumber.toString(),
        isPlaying = isPlaying,
        waiting = busy,
    )
}
