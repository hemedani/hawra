package ir.hawraa.quran.ui.components

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.hawraa.quran.R
import ir.hawraa.quran.core.HapticIntent
import ir.hawraa.quran.core.rememberHaptics
import ir.hawraa.quran.ui.components.player.WavyProgress
import ir.hawraa.quran.ui.theme.LocalHawraaGeometry
import ir.hawraa.quran.ui.theme.LocalHawraaTokens
import ir.hawraa.quran.ui.theme.useMotionEnabled
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * The dock's now-playing half — only the fields the mini row draws.
 *
 * This is deliberately a **dock-local** model, not the player store's. A once-a-second
 * position tick updates [miniProgress], which the wave collects in its own leaf, so the
 * dock never recomposes for a tick; Task 6 maps the store to this shape at the boundary.
 */
data class NowPlayingUi(
    /** Surah name, e.g. «فاتحه» / «Al-Fatiha». */
    val title: String,
    /** The line under it, e.g. «آیۀ ۳ · پرهیزگار» / «Ayah 3 · Parhizgar». */
    val subtitle: String,
    /**
     * The numeral on the identity tile — the surah's, or the juz's for a juz-granularity
     * reciter. Already in the display script: the shell localises it once.
     */
    val number: String,
    val isPlaying: Boolean,
    /** Fetching/buffering with nothing to report yet. Keeps its press and shows a spinner. */
    val waiting: Boolean = false,
)

/**
 * One dock destination. Four peers (plan 02 D2), in reading order; the row follows the
 * layout direction, so in RTL the same list reads right-to-left.
 */
enum class DockTab(
    /** Selection key; a route *pattern* for destinations with arguments (the reader). */
    val route: String,
    @StringRes val labelRes: Int,
    /** Semantic name in [ICON_GLYPHS] — never a per-screen drawing. */
    val glyph: String,
) {
    Home("home", R.string.home_title, "home"),
    Read("reader", R.string.reader_title, "read"),
    History("history", R.string.history_title, "history"),
    Settings("settings", R.string.settings_title, "settings"),
    ;

    /** True when [selectedRoute] belongs to this destination (the reader carries a page). */
    fun matches(selectedRoute: String?): Boolean =
        selectedRoute == route || selectedRoute?.startsWith("$route?") == true
}

/**
 * The dock (DESIGN.md §10.4): **one** frost panel with a collapsible mini-player half
 * over an icon-only navigation half.
 *
 * ## Why one panel
 *
 * Two floating surfaces mean their geometry is a negotiation, and it went wrong in the
 * reference app: a raised centre button ended up under the now-playing row. Inside one
 * panel the question cannot be asked — one surface, one radius, one shadow, no clearance
 * to get wrong. Both halves are [DockGeometry.playerHeight] / [DockGeometry.navHeight],
 * so the seam divides the panel in half.
 *
 * ## Why the row collapses
 *
 * With nothing playing there is nothing to report, and a permanently empty row is a
 * permanent lie about the dock's size. The panel animates between nav-only and
 * player + nav + seam, and the seam lives *inside* the fading group so it cannot outlive
 * the row it divides.
 *
 * ## Navigation
 *
 * Four icon-only peers. No labels (two elements per slot for no extra information), no
 * raised centre action (the most template-looking dock element there is). The selected
 * destination is a filled `accent` disc with the glyph knocked out — the same "filled
 * accent circle means this is the current thing" rule the play bubble follows.
 *
 * [onMiniPlayerRect] publishes the measured player-half rect in root coordinates so the
 * full player can morph out of it (Task 6).
 */
@Composable
fun AppDock(
    selectedRoute: String,
    onSelect: (DockTab) -> Unit,
    modifier: Modifier = Modifier,
    nowPlaying: NowPlayingUi? = null,
    miniProgress: StateFlow<Float>? = null,
    onPlayPause: () -> Unit = {},
    onNext: () -> Unit = {},
    onExpand: () -> Unit = {},
    onMiniPlayerRect: (Rect) -> Unit = {},
) {
    val tokens = LocalHawraaTokens.current
    val geometry = LocalHawraaGeometry.current
    val dock = geometry.dock
    val motion = useMotionEnabled()
    val shape = if (nowPlaying != null) {
        RoundedCornerShape(geometry.radius.xl)
    } else {
        RoundedCornerShape(geometry.radius.pill)
    }
    val playerHeightPx = with(LocalDensity.current) { dock.playerHeight.toPx() }
    val currentOnRect by rememberUpdatedState(onMiniPlayerRect)
    val lastRect = remember { mutableStateOf<Rect?>(null) }

    // AnimatedVisibility retains its content while it exits, but the content lambda would
    // otherwise read the now-null state and collapse before the shrink can play. Keep the
    // last non-null snapshot so the row has something to shrink.
    val shown = remember { mutableStateOf(nowPlaying) }
    if (nowPlaying != null) shown.value = nowPlaying

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = dock.margin)
            .padding(bottom = dock.margin),
    ) {
        FrostedSurface(
            modifier = Modifier.fillMaxWidth(),
            shape = shape,
            softElevation = dockElevation(),
        ) {
            Column(Modifier.fillMaxWidth()) {
                AnimatedVisibility(
                    visible = nowPlaying != null,
                    enter = if (motion) {
                        expandVertically() + fadeIn()
                    } else {
                        expandVertically(animationSpec = tween(0)) + fadeIn(animationSpec = tween(0))
                    },
                    exit = if (motion) {
                        shrinkVertically() + fadeOut()
                    } else {
                        shrinkVertically(animationSpec = tween(0)) + fadeOut(animationSpec = tween(0))
                    },
                ) {
                    shown.value?.let { playing ->
                        Column(
                            Modifier.onGloballyPositioned { coords ->
                                if (!coords.isAttached || coords.size.width == 0) return@onGloballyPositioned
                                val topLeft = coords.positionInRoot()
                                val rect = Rect(
                                    left = topLeft.x,
                                    top = topLeft.y,
                                    right = topLeft.x + coords.size.width,
                                    bottom = topLeft.y + playerHeightPx,
                                )
                                if (rect != lastRect.value) {
                                    lastRect.value = rect
                                    currentOnRect(rect)
                                }
                            },
                        ) {
                            MiniPlayerRow(
                                nowPlaying = playing,
                                miniProgress = miniProgress,
                                onPlayPause = onPlayPause,
                                onNext = onNext,
                                onExpand = onExpand,
                                modifier = Modifier.height(dock.playerHeight),
                            )
                            HairlineWave(
                                Modifier
                                    .padding(horizontal = dock.inset)
                                    .height(geometry.spacing.sm),
                                color = tokens.glassBorder,
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(dock.navHeight)
                        .padding(horizontal = dock.inset)
                        .testTag("dock-nav"),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DockTab.entries.forEach { tab ->
                        DockDestination(
                            tab = tab,
                            selected = tab.matches(selectedRoute),
                            onClick = { onSelect(tab) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DockDestination(tab: DockTab, selected: Boolean, onClick: () -> Unit) {
    val tokens = LocalHawraaTokens.current
    val geometry = LocalHawraaGeometry.current
    val dock = geometry.dock
    val motion = useMotionEnabled()
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val scale = remember { Animatable(1f) }
    val label = stringResource(tab.labelRes)

    Box(
        modifier = Modifier
            .size(dock.touch)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
            .clip(CircleShape)
            .clickable(
                role = Role.Tab,
                onClickLabel = label,
                onClick = {
                    haptics.trigger(HapticIntent.Select)
                    if (motion) {
                        scope.launch {
                            scale.animateTo(0.92f, spring(stiffness = Spring.StiffnessHigh))
                            scale.animateTo(1.05f, spring(stiffness = Spring.StiffnessHigh))
                            scale.animateTo(1f, spring(stiffness = Spring.StiffnessMedium))
                        }
                    }
                    onClick()
                },
            )
            .testTag("dock-${tab.name.lowercase()}"),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .size(dock.indicator)
                    .clip(CircleShape)
                    .background(tokens.accent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    name = tab.glyph,
                    contentDescription = label,
                    tint = tokens.onAccent,
                    size = dock.iconActive,
                )
            }
        } else {
            Icon(
                name = tab.glyph,
                contentDescription = label,
                tint = tokens.textSecondary,
                size = dock.icon,
            )
        }
    }
}

/**
 * The dock's player half — identity tile, title and subtitle, wave, play/pause and next.
 *
 * A plain row with **no surface of its own**: the dock owns the panel. The whole leading
 * region (tile, text and wave) is one tap target that opens the full player; the trailing
 * bubbles sit outside it so they never steal that tap. [StandaloneMiniPlayer] draws the
 * same row on routes without the dock, so the two cannot drift apart.
 */
@Composable
internal fun MiniPlayerRow(
    nowPlaying: NowPlayingUi,
    miniProgress: StateFlow<Float>?,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalHawraaTokens.current
    val geometry = LocalHawraaGeometry.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = geometry.spacing.md, end = geometry.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .height(geometry.dock.playerHeight)
                .clickable(onClickLabel = nowPlaying.title, onClick = onExpand)
                .testTag("dock-player"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PaletteTile(
                seedKey = nowPlaying.number,
                number = nowPlaying.number,
                size = geometry.tiles.md,
            )
            Spacer(Modifier.width(geometry.spacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = nowPlaying.title,
                    style = MaterialTheme.typography.labelLarge,
                    color = tokens.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = nowPlaying.subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (miniProgress != null) {
                    MiniWave(
                        progress = miniProgress,
                        isPlaying = nowPlaying.isPlaying,
                        waiting = nowPlaying.waiting,
                    )
                }
            }
        }

        BubbleButton(
            icon = ICON_GLYPHS.getValue(if (nowPlaying.isPlaying) "pause" else "play"),
            contentDescription = stringResource(
                if (nowPlaying.isPlaying) R.string.recitation_pause else R.string.recitation_play,
            ),
            onClick = onPlayPause,
            size = geometry.controls.md,
            filled = true,
            busy = nowPlaying.waiting,
            modifier = Modifier.testTag("dock-play"),
        )
        BubbleButton(
            icon = ICON_GLYPHS.getValue("next"),
            contentDescription = stringResource(R.string.recitation_next_ayah),
            onClick = onNext,
            size = geometry.controls.sm,
            modifier = Modifier.testTag("dock-next"),
        )
    }
}

/**
 * The wave is its own leaf so the once-a-second progress tick recomposes it — and only
 * it — rather than the dock's title row.
 */
@Composable
private fun MiniWave(progress: StateFlow<Float>, isPlaying: Boolean, waiting: Boolean) {
    val value by progress.collectAsStateWithLifecycle()
    WavyProgress(
        progress = value,
        isPlaying = isPlaying,
        waiting = waiting,
        modifier = Modifier.padding(top = LocalHawraaGeometry.current.spacing.xxs),
    )
}
