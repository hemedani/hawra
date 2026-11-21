package ir.hawraa.quran.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import ir.hawraa.quran.domain.paletteIndexFor
import ir.hawraa.quran.ui.theme.AuroraRamps
import ir.hawraa.quran.ui.theme.LocalHawraaGeometry
import ir.hawraa.quran.ui.theme.LocalHawraaTokens
import ir.hawraa.quran.ui.theme.Vazirmatn
import ir.hawraa.quran.ui.theme.tileRadius

/**
 * The per-recitation identity tile (DESIGN.md §10.5 / D5).
 *
 * Hawraa never fetches artwork, so identity is a deterministic aurora ramp derived
 * from the seed (surah, juz, or reciter) with the number set in the UI face. The same
 * seed paints the same gradient on every device, offline — a track's colour is a fact
 * about its name, not a network response.
 */
@Composable
fun PaletteTile(
    seedKey: String,
    label: String,
    modifier: Modifier = Modifier,
    size: Dp = LocalHawraaGeometry.current.tiles.md,
    radius: Dp = tileRadius(size),
) {
    val tokens = LocalHawraaTokens.current
    val geometry = LocalHawraaGeometry.current
    val ramp = AuroraRamps[paletteIndexFor(seedKey)]
    val shape = RoundedCornerShape(radius)
    // A hero tile needs a numeral that carries at hero scale; the dock's small tile
    // keeps the title size. Both stay on the UI face — the mushaf face is scripture.
    val style = if (size >= geometry.tiles.xl) {
        MaterialTheme.typography.titleLarge
    } else {
        MaterialTheme.typography.titleMedium
    }.copy(fontFamily = Vazirmatn)

    Box(
        modifier = modifier.size(size).clip(shape),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .background(Brush.linearGradient(listOf(ramp.first, ramp.second, ramp.third))),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = style,
                color = tokens.text,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}
