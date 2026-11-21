package ir.hawraa.quran.ui.components.player

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import ir.hawraa.quran.domain.paletteIndexFor
import ir.hawraa.quran.ui.components.PaletteTile
import ir.hawraa.quran.ui.theme.AuroraRamps
import ir.hawraa.quran.ui.theme.LocalHawraaGeometry
import ir.hawraa.quran.ui.theme.LocalHawraaTokens

/**
 * The player's hero identity tile (`docs/DESIGN.md` §10.5 / D5).
 *
 * Pejvak hands the artwork image between the dock and the player; Hawraa has no
 * artwork, so the hand-off is the same deterministic aurora tile at hero size. The
 * glow is the ramp's own mid stop, so the lift belongs to the content's colour.
 */
@Composable
fun HeroArtwork(
    size: Dp,
    seedKey: String,
    label: String,
    modifier: Modifier = Modifier,
    tileAlpha: Float = 1f,
    onTilePositioned: ((LayoutCoordinates) -> Unit)? = null,
) {
    val tokens = LocalHawraaTokens.current
    val geometry = LocalHawraaGeometry.current
    val player = geometry.player
    val ramp = AuroraRamps[paletteIndexFor(seedKey)]
    val shape = RoundedCornerShape(geometry.radius.lg)
    Box(
        modifier = modifier
            .size(size)
            .shadow(
                player.heroShadow,
                shape,
                clip = false,
                ambientColor = ramp.second,
                spotColor = ramp.second,
            )
            .border(geometry.glass.borderWidth, tokens.glassBorder, shape)
            .padding(player.heroPadding),
    ) {
        PaletteTile(
            seedKey = seedKey,
            label = label,
            size = size - player.heroPadding * 2,
            radius = geometry.radius.lg - geometry.spacing.xs,
            modifier = Modifier
                .then(if (tileAlpha < 1f) Modifier.graphicsLayer { alpha = tileAlpha } else Modifier)
                .then(if (onTilePositioned != null) Modifier.onGloballyPositioned(onTilePositioned) else Modifier),
        )
    }
}

/** A flowing row of player chips — speed, repeat, reciter, download, preset. */
@Composable
fun PlayerChipRow(
    modifier: Modifier = Modifier,
    content: @Composable FlowRowScope.() -> Unit,
) {
    val geometry = LocalHawraaGeometry.current
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(geometry.spacing.sm),
        verticalArrangement = Arrangement.spacedBy(geometry.spacing.sm),
        content = content,
    )
}
