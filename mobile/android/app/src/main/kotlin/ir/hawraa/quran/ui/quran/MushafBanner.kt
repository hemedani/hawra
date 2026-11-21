package ir.hawraa.quran.ui.quran

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ir.hawraa.quran.R
import ir.hawraa.quran.domain.quran.toPersianDigits
import ir.hawraa.quran.ui.theme.LocalHawraaGeometry
import ir.hawraa.quran.ui.theme.LocalQuranColors

/**
 * The surah plate that opens a surah — a **flat tinted pane**, not the printed green banner of
 * the old design (`docs/DESIGN.md` §4.5, §10).
 *
 * The change is deliberate: a saturated gradient plate with a gold border is the single most
 * recognisable "Quran app" element there is, and it was the thing the owner asked to leave
 * behind. What stays is the *structure* — a plate, a double rule, and a terminal mark at each
 * end — because that structure is what tells the reader "a surah starts here" rather than the
 * colour.
 *
 * The tint is [QuranColors.bannerFill] (the accent at 12 %) with an [QuranColors.bannerEdge]
 * hairline, so the plate belongs to the accent system rather than fighting it.
 */
@Composable
fun MushafBanner(
    surahNumber: Int,
    surahName: String,
    juz: Int,
    modifier: Modifier = Modifier,
) {
    val colors = LocalQuranColors.current
    val geometry = LocalHawraaGeometry.current
    val shape = RoundedCornerShape(geometry.radius.sm)
    val surahLabel = stringResource(R.string.banner_surah, surahNumber.toPersianDigits())
    val juzLabel = stringResource(R.string.banner_juz, juz.toPersianDigits())

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(color = colors.bannerFill, shape = shape)
            .drawBehind {
                val stroke = 1.dp.toPx()
                val radius = geometry.radius.sm.toPx()
                drawRoundRect(
                    color = colors.bannerEdge,
                    topLeft = Offset(stroke / 2f, stroke / 2f),
                    size = Size(size.width - stroke, size.height - stroke),
                    cornerRadius = CornerRadius(radius),
                    style = Stroke(width = stroke),
                )
                val inset = 5.dp.toPx()
                drawRoundRect(
                    color = colors.bannerEdge.copy(alpha = 0.45f),
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - inset * 2, size.height - inset * 2),
                    cornerRadius = CornerRadius(radius * 0.6f),
                    style = Stroke(width = stroke * 0.8f),
                )
                // Terminal diamonds at both ends of the plate — the one piece of ornament that
                // survives, in the warm marker hue so it reads as decoration and not as chrome.
                val d = 4.dp.toPx()
                val cy = size.height / 2f
                val left = inset + d * 1.8f
                val right = size.width - inset - d * 1.8f
                listOf(left, right).forEach { cx ->
                    val diamond = Path().apply {
                        moveTo(cx, cy - d)
                        lineTo(cx + d, cy)
                        lineTo(cx, cy + d)
                        lineTo(cx - d, cy)
                        close()
                    }
                    drawPath(diamond, color = colors.mark)
                }
            }
            .padding(horizontal = 26.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = surahLabel,
                style = MaterialTheme.typography.labelLarge,
                color = colors.bannerSubText,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Start,
            )
            Text(
                text = surahName,
                style = MaterialTheme.typography.titleLarge,
                color = colors.bannerText,
            )
            Text(
                text = juzLabel,
                style = MaterialTheme.typography.labelLarge,
                color = colors.bannerSubText,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.End,
            )
        }
    }
}
