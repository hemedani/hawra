package ir.hawraa.quran.ui.components

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.hawraa.quran.R
import ir.hawraa.quran.domain.quran.toPersianDigits
import ir.hawraa.quran.ui.theme.LocalGlassColors
import ir.hawraa.quran.ui.theme.LocalHawraaGeometry

/** One destination in the dock's navigation half. */
data class DockItem(
    val key: String,
    val label: String,
    val icon: ImageVector,
    val selected: Boolean,
    val onClick: () -> Unit,
)

/**
 * What the dock's player half draws. Only the fields the row needs, so a state change elsewhere
 * cannot recompose the dock.
 */
data class NowPlayingUi(
    /** Surah name, e.g. «فاتحه». */
    val title: String,
    /** The line under it, e.g. «آیه ۳ · پرهیزگار». */
    val subtitle: String,
    /** The number drawn on the identity tile — the surah's, in Persian digits. */
    val tileNumber: Int,
    val isPlaying: Boolean,
    /** The player is fetching and has nothing to report yet. Keeps its press. */
    val busy: Boolean = false,
)

/**
 * The dock: the now-playing row and the navigation, **as one panel** (`docs/DESIGN.md` §10.4).
 *
 * ## Why one panel
 *
 * These used to be two floating surfaces in the reference app, and two surfaces mean their
 * geometry is a negotiation — one that went wrong, leaving a raised centre button 32 dp underneath
 * the now-playing row. Inside a single panel the question cannot be asked: one surface, one radius,
 * one shadow, no clearance to get wrong. Both halves are the same height (58) so the seam divides
 * the panel in half rather than cutting a tall nav off a short player.
 *
 * ## Why the row collapses
 *
 * With nothing playing there is nothing to report, and a permanent empty row is a permanent lie
 * about the dock's size. The panel animates between `navHeight` and
 * `playerHeight + navHeight + hairline`, and the seam lives *inside* the fading group so the
 * divider cannot outlive the row it divides.
 *
 * ## Why no labels and no raised centre action
 *
 * Four icon-only peers. A label under each glyph makes every slot carry two elements and pushes
 * the row taller for no extra information; a raised centre button is the single most
 * template-looking element a dock can have. The selected state is a filled accent disc with the
 * glyph knocked out — the same "filled accent circle means this is the current thing" rule the
 * play bubble follows, so the panel reads as one object with one rule.
 */
@Composable
fun AppDock(
    items: List<DockItem>,
    modifier: Modifier = Modifier,
    nowPlaying: NowPlayingUi? = null,
    onPlayPause: () -> Unit = {},
    onNext: () -> Unit = {},
    onOpenPlayer: () -> Unit = {},
) {
    val geometry = LocalHawraaGeometry.current
    val dock = geometry.dock

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = dock.inset, vertical = 0.dp)
            .padding(bottom = dock.margin),
    ) {
        GlassSurface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(geometry.radius.card),
            strong = true,
            elevation = GlassElevation.Dock,
        ) {
            Column(Modifier.fillMaxWidth()) {
                // `AnimatedVisibility` owns the height change, so the panel's size and its
                // content can never disagree about whether the player half is showing.
                AnimatedVisibility(
                    visible = nowPlaying != null,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    val playing = nowPlaying
                    if (playing != null) {
                        Column {
                            MiniPlayerRow(
                                nowPlaying = playing,
                                onPlayPause = onPlayPause,
                                onNext = onNext,
                                onOpenPlayer = onOpenPlayer,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(dock.playerHeight)
                                    .testTag("dock-player"),
                            )
                            // The seam, inset so it reads as a division inside the panel rather
                            // than a line drawn across the screen.
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = geometry.spacing.md)
                                    .height(1.dp)
                                    .background(LocalGlassColors.current.track),
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
                    items.forEach { item ->
                        DockDestination(
                            item = item,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DockDestination(item: DockItem, modifier: Modifier = Modifier) {
    val glass = LocalGlassColors.current
    val geometry = LocalHawraaGeometry.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(geometry.dock.navHeight)
            .clip(CircleShape)
            .clickable(
                role = Role.Tab,
                onClickLabel = item.label,
                onClick = item.onClick,
            )
            .testTag("dock-${item.key}"),
        contentAlignment = Alignment.Center,
    ) {
        if (item.selected) {
            Box(
                modifier = Modifier
                    .size(geometry.dock.indicator)
                    .clip(CircleShape)
                    .background(glass.accent)
                    // The same specular top edge every frosted pane gets, at disc scale: it makes
                    // the indicator read as a lit sphere rather than a flat sticker.
                    .drawWithContent {
                        drawContent()
                        val rim = 3.dp.toPx()
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(glass.glassHighlight, Color.Transparent),
                                startY = 0f,
                                endY = rim,
                            ),
                            topLeft = Offset.Zero,
                            size = Size(size.width, rim),
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = item.label,
                    tint = glass.onAccent,
                    modifier = Modifier.size(geometry.dock.iconActive),
                )
            }
        } else {
            Icon(
                imageVector = item.icon,
                contentDescription = item.label,
                tint = glass.textTertiary,
                modifier = Modifier.size(geometry.dock.icon),
            )
        }
    }
}

/**
 * The dock's player half — artwork tile, title and subtitle, play and next.
 *
 * A plain row with **no surface of its own**: the dock owns the panel. The whole left side is one
 * tap target that opens the full player, and the trailing bubbles sit outside it so they never
 * steal that tap.
 *
 * Hawraa has no artwork and never fetches any, so the tile is a deterministic **identity tile**:
 * the surah number set in the mushaf face on the accent wash — or, for a juz-granularity reciter,
 * the juz number, because that is what the title beside it says (`docs/DESIGN.md` §10.5).
 */
@Composable
private fun MiniPlayerRow(
    nowPlaying: NowPlayingUi,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onOpenPlayer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val glass = LocalGlassColors.current
    val geometry = LocalHawraaGeometry.current

    Row(
        modifier = modifier.padding(
            start = geometry.spacing.md,
            end = geometry.spacing.xs,
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .height(geometry.dock.playerHeight)
                .clickable(onClickLabel = nowPlaying.title, onClick = onOpenPlayer),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(geometry.radius.sm))
                    .background(glass.accentSoft),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = nowPlaying.tileNumber.toPersianDigits(),
                    // The **UI** face, deliberately not the mushaf face. Scheherazade draws «۱» as
                    // a single thin calligraphic stroke: at 19 sp inside a 38 dp tile that is a
                    // slanted smudge, not a numeral — it reads as a rendering artefact. The mushaf
                    // face is for scripture; chrome gets the UI face, at a size and weight that
                    // survive the pale wash.
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 18.sp,
                        lineHeight = 18.sp,
                    ),
                    color = glass.accent,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.width(geometry.spacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = nowPlaying.title,
                    style = MaterialTheme.typography.labelLarge,
                    color = glass.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = nowPlaying.subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = glass.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        BubbleButton(
            icon = if (nowPlaying.isPlaying) TransportIcons.Pause else TransportIcons.Play,
            contentDescription = stringResource(
                if (nowPlaying.isPlaying) R.string.recitation_pause else R.string.recitation_play,
            ),
            onClick = onPlayPause,
            size = geometry.controls.md,
            iconSize = 17.dp,
            filled = true,
            busy = nowPlaying.busy,
            modifier = Modifier.testTag("dock-play"),
        )
        BubbleButton(
            icon = TransportIcons.Next,
            contentDescription = stringResource(R.string.recitation_next_ayah),
            onClick = onNext,
            size = geometry.controls.sm,
            iconSize = 15.dp,
            modifier = Modifier.testTag("dock-next"),
        )
    }
}
